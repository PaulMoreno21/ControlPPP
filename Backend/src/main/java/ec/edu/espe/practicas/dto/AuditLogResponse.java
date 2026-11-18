package ec.edu.espe.practicas.dto;

import ec.edu.espe.practicas.model.AuditLog;
import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        Long actorId,
        String actorName,
        String action,
        String entityName,
        Long entityId,
        LocalDateTime occurredAt,
        String details) {

    public static AuditLogResponse from(AuditLog auditLog) {
        return from(auditLog, auditLog.getDetails());
    }

    public static AuditLogResponse from(AuditLog auditLog, String details) {
        var user = auditLog.getUser();
        String actorName;
        if (user != null) {
            actorName = user.getFirstName() + " " + user.getLastName();
        } else if ("IMPORTAR_FERIADOS".equals(auditLog.getAction())
                || "PRECARGA_ANUAL_FERIADOS".equals(auditLog.getAction())) {
            actorName = "Sistema";
        } else {
            actorName = "Intento de acceso sin cuenta asociada";
        }
        return new AuditLogResponse(
                auditLog.getId(), user == null ? null : user.getId(),
                actorName,
                auditLog.getAction(), auditLog.getEntityName(), auditLog.getEntityId(),
                auditLog.getOccurredAt(), details);
    }
}
