package ec.edu.espe.practicas.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import ec.edu.espe.practicas.config.RateLimitProperties;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Fixed windows starting at each identity's first request; counters are local to this JVM. */
public final class RequestRateLimiter {
    public record Decision(boolean allowed, long retryAfterSeconds) { }
    private record Key(String scope, String identity) { }
    private static final class Window {
        private final long startedAt;
        private int used;
        Window(long startedAt) { this.startedAt = startedAt; }
    }

    private final Cache<Key, Window> windows;
    private final Ticker ticker;
    private final long windowNanos;
    private final int maxKeys;

    public RequestRateLimiter(RateLimitProperties properties, Ticker ticker) {
        this.ticker = ticker;
        this.windowNanos = TimeUnit.SECONDS.toNanos(properties.windowSeconds());
        this.maxKeys = properties.maxKeys();
        this.windows = Caffeine.newBuilder().ticker(ticker)
                .expireAfterWrite(Duration.ofSeconds(properties.windowSeconds())).build();
    }

    // Admission and increments are atomic; no live bucket is evicted to admit a new identity.
    public synchronized Decision consume(String scope, String identity, int limit) {
        Key key = new Key(scope, identity);
        Window window = windows.getIfPresent(key);
        long now = ticker.read();
        if (window == null) {
            if (windows.estimatedSize() >= maxKeys) {
                windows.cleanUp();
                if (windows.estimatedSize() >= maxKeys) {
                    return new Decision(false, secondsRoundedUp(windowNanos));
                }
            }
            window = new Window(now);
            windows.put(key, window);
        }
        if (window.used >= limit) {
            return new Decision(false, secondsRoundedUp(windowNanos - (now - window.startedAt)));
        }
        window.used++;
        return new Decision(true, 0);
    }

    private static long secondsRoundedUp(long nanos) {
        return Math.max(1, (nanos + 999_999_999L) / 1_000_000_000L);
    }
}
