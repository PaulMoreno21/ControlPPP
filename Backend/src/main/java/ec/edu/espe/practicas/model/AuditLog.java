package ec.edu.espe.practicas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@Entity
@Table(name = "TCWAUDITORIAS")
@SequenceGenerator(name = "audit_log_seq", sequenceName = "SEQ_CWAUDITORIAS", allocationSize = 1)
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "audit_log_seq")
    @Column(name = "TCWAUDITORIAS_ID", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TCWUSUARIOS_ID")
    private User user;

    @NotBlank @Size(max = 50)
    @Column(name = "TCWAUDITORIAS_ACCION", nullable = false, length = 50)
    private String action;

    @NotBlank @Size(max = 50)
    @Column(name = "TCWAUDITORIAS_ENTIDAD", nullable = false, length = 50)
    private String entityName;

    @Column(name = "TCWAUDITORIAS_ENTIDAD_ID")
    private Long entityId;

    @NotNull
    @Column(name = "TCWAUDITORIAS_FECHA", nullable = false)
    private LocalDateTime occurredAt;

    @Lob
    @Column(name = "TCWAUDITORIAS_DETALLE")
    private String details;

    protected AuditLog() { }

    public static AuditLog record(
            User user, String action, String entityName, Long entityId,
            LocalDateTime occurredAt, String details) {
        AuditLog log = new AuditLog();
        log.user = user;
        log.action = action;
        log.entityName = entityName;
        log.entityId = entityId;
        log.occurredAt = occurredAt;
        log.details = details;
        return log;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getEntityName() { return entityName; }
    public void setEntityName(String entityName) { this.entityName = entityName; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
