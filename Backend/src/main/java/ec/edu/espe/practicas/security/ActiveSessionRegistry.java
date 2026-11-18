package ec.edu.espe.practicas.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import ec.edu.espe.practicas.service.exception.BusinessRuleViolationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Allow-list: a signed token alone is insufficient; restart invalidates all sessions. */
@Component
public class ActiveSessionRegistry {
    private record Session(Long userId, Instant expiresAt, Instant idleExpiresAt) { }
    private final Cache<String, Session> sessions;
    private final Clock clock;
    private final int maximum;
    private final Duration idleTimeout;
    private final Duration maximumLifetime;

    @Autowired
    public ActiveSessionRegistry(Clock clock,
            @Value("${app.jwt.expiration-seconds:28800}") long lifetimeSeconds,
            @Value("${app.session.max-active:50000}") int maximum,
            @Value("${app.session.idle-timeout-seconds:900}") long idleSeconds) {
        if (lifetimeSeconds < 1 || maximum < 1 || idleSeconds < 1 || idleSeconds > lifetimeSeconds) {
            throw new IllegalArgumentException("Configuración de sesión inválida.");
        }
        this.clock = clock;
        this.maximum = maximum;
        this.idleTimeout = Duration.ofSeconds(idleSeconds);
        this.maximumLifetime = Duration.ofSeconds(lifetimeSeconds);
        sessions = Caffeine.newBuilder().expireAfterWrite(idleTimeout).build();
    }

    public ActiveSessionRegistry(Clock clock, long lifetimeSeconds, int maximum) {
        this(clock, lifetimeSeconds, maximum, Math.min(900, lifetimeSeconds));
    }

    public synchronized Instant register(String id, Long userId, Instant expiresAt) {
        java.util.Objects.requireNonNull(userId, "La sesión requiere un usuario.");
        Instant now = clock.instant();
        if (!now.isBefore(expiresAt)) throw new IllegalArgumentException("La sesión ya venció.");
        if (sessions.estimatedSize() >= maximum) {
            sessions.cleanUp();
            sessions.asMap().entrySet().removeIf(entry -> expired(entry.getValue(), now));
            if (sessions.estimatedSize() >= maximum) {
                throw new BusinessRuleViolationException("No se pudo iniciar sesión. Intenta nuevamente más tarde.");
            }
        }
        Instant maximumExpiry = now.plus(maximumLifetime);
        Instant absolute = expiresAt.isBefore(maximumExpiry) ? expiresAt : maximumExpiry;
        Instant idleExpiresAt = idleDeadline(now, absolute);
        sessions.put(id, new Session(userId, absolute, idleExpiresAt));
        return idleExpiresAt;
    }

    public synchronized boolean isActive(String id) {
        return validSession(id) != null;
    }

    public synchronized Instant idleExpiresAt(String id) {
        Session session = validSession(id);
        return session == null ? null : session.idleExpiresAt();
    }

    /** Ordinary reads never renew idle time. */
    public synchronized Instant touch(String id) {
        Session session = validSession(id);
        if (session == null) return null;
        Instant deadline = idleDeadline(clock.instant(), session.expiresAt());
        sessions.put(id, new Session(session.userId(), session.expiresAt(), deadline));
        return deadline;
    }

    private Session validSession(String id) {
        if (id == null) return null;
        Session session = sessions.getIfPresent(id);
        if (session != null && expired(session, clock.instant())) {
            sessions.invalidate(id);
            return null;
        }
        return session;
    }

    private static boolean expired(Session session, Instant now) {
        return !now.isBefore(session.expiresAt()) || !now.isBefore(session.idleExpiresAt());
    }

    private Instant idleDeadline(Instant now, Instant absolute) {
        Instant idle = now.plus(idleTimeout);
        return idle.isBefore(absolute) ? idle : absolute;
    }

    public synchronized void revoke(String id) {
        if (id != null) sessions.invalidate(id);
    }

    public synchronized void revokeAll(Long userId) {
        sessions.asMap().entrySet().removeIf(entry -> entry.getValue().userId().equals(userId));
    }

    public void revokeAllAfterCommit(Long userId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { revokeAll(userId); }
            });
        } else {
            revokeAll(userId);
        }
    }
}
