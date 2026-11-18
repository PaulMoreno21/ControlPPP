package ec.edu.espe.practicas.service;

import ec.edu.espe.practicas.model.AuditLog;
import ec.edu.espe.practicas.model.User;
import ec.edu.espe.practicas.repository.AuditLogRepository;
import ec.edu.espe.practicas.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationAuditService {
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public AuthenticationAuditService(AuditLogRepository auditLogRepository, UserRepository userRepository, Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedLogin(User knownAccount, String submittedEmail, String reason) {
        User actor = knownAccount == null
                ? null
                : userRepository.findById(knownAccount.getId()).orElse(null);
        auditLogRepository.saveAndFlush(AuditLog.record(
                actor,
                "LOGIN_FALLIDO",
                "AUTENTICACION",
                actor == null ? null : actor.getId(),
                LocalDateTime.now(clock),
                reason + " Correo ingresado: " + maskEmail(submittedEmail) + "."));
    }

    private String maskEmail(String value) {
        if (value == null || value.isBlank()) return "no indicado";
        String email = value.trim().replaceAll("[\\r\\n]", "");
        int at = email.indexOf('@');
        if (at < 1) return email.substring(0, Math.min(2, email.length())) + "***";
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        String visible = local.substring(0, Math.min(1, local.length()));
        return visible + "***@" + domain;
    }
}
