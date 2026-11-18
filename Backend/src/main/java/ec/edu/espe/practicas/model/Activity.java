package ec.edu.espe.practicas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "TCWACTIVIDADES")
@SequenceGenerator(name = "activity_seq", sequenceName = "SEQ_CWACTIVIDADES", allocationSize = 1)
public class Activity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "activity_seq")
    @Column(name = "TCWACTIVIDADES_ID", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "TCWUSUARIOS_ID_PRACTICANTE", nullable = false)
    private User trainee;

    @NotNull
    @Column(name = "TCWACTIVIDADES_FECHA", nullable = false)
    private LocalDate date;

    @NotBlank
    @Lob
    @Column(name = "TCWACTIVIDADES_DESCRIPCION", nullable = false)
    private String description;

    @NotNull @Positive
    @Column(name = "TCWACTIVIDADES_DURACION_HORA", nullable = false, precision = 5, scale = 2)
    private BigDecimal durationHours;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "TCWACTIVIDADES_ESTADO", nullable = false, length = 10)
    private ActivityStatus status = ActivityStatus.BORRADOR;

    @Column(name = "TCWACTIVIDADES_FECHA_ENVIO")
    private LocalDateTime lastSubmittedAt;

    protected Activity() { }

    public static Activity draft(
            User trainee, LocalDate date, String description, BigDecimal durationHours) {
        Activity activity = new Activity();
        activity.trainee = trainee;
        activity.date = date;
        activity.description = description;
        activity.durationHours = durationHours;
        activity.status = ActivityStatus.BORRADOR;
        return activity;
    }

    public void submit(LocalDateTime submittedAt) {
        this.status = ActivityStatus.ENVIADA;
        this.lastSubmittedAt = submittedAt;
    }

    public void recordDecision(ReviewDecision decision) {
        this.status = decision == ReviewDecision.ACEPTADA
                ? ActivityStatus.ACEPTADA
                : ActivityStatus.RECHAZADA;
    }

    public Long getId() { return id; }
    public User getTrainee() { return trainee; }
    public void setTrainee(User trainee) { this.trainee = trainee; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getDurationHours() { return durationHours; }
    public void setDurationHours(BigDecimal durationHours) { this.durationHours = durationHours; }
    public ActivityStatus getStatus() { return status; }
    public void setStatus(ActivityStatus status) { this.status = status; }
    public LocalDateTime getLastSubmittedAt() { return lastSubmittedAt; }
    public void setLastSubmittedAt(LocalDateTime lastSubmittedAt) { this.lastSubmittedAt = lastSubmittedAt; }
}
