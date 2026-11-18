package ec.edu.espe.practicas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Entity
@Table(name = "TCWASIGNACIONES")
@SequenceGenerator(name = "assignment_seq", sequenceName = "SEQ_CWASIGNACIONES", allocationSize = 1)
public class Assignment {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "assignment_seq")
    @Column(name = "TCWASIGNACIONES_ID", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "TCWUSUARIOS_ID_PRACTICANTE", nullable = false)
    private User trainee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "TCWUSUARIOS_ID_TUTOR", nullable = false)
    private User tutor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "TCWUSUARIOS_ID_ASIGNADOR", nullable = false)
    private User assignedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "TCWLABORATORIOS_ID", nullable = false)
    private Laboratory laboratory;

    @NotNull
    @Column(name = "TCWASIGNACIONES_FECHA", nullable = false)
    private LocalDateTime assignedAt;

    @NotNull
    @Column(name = "TCWASIGNACIONES_VIGENTE", nullable = false)
    private Integer current = 1;

    protected Assignment() { }

    public static Assignment create(
            User trainee, User tutor, User assignedBy, Laboratory laboratory, LocalDateTime assignedAt) {
        Assignment assignment = new Assignment();
        assignment.trainee = trainee;
        assignment.tutor = tutor;
        assignment.assignedBy = assignedBy;
        assignment.laboratory = laboratory;
        assignment.assignedAt = assignedAt;
        assignment.current = 1;
        return assignment;
    }

    public Long getId() { return id; }
    public User getTrainee() { return trainee; }
    public void setTrainee(User trainee) { this.trainee = trainee; }
    public User getTutor() { return tutor; }
    public void setTutor(User tutor) { this.tutor = tutor; }
    public User getAssignedBy() { return assignedBy; }
    public void setAssignedBy(User assignedBy) { this.assignedBy = assignedBy; }
    public Laboratory getLaboratory() { return laboratory; }
    public void setLaboratory(Laboratory laboratory) { this.laboratory = laboratory; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }
    public Integer getCurrent() { return current; }
    public void setCurrent(Integer current) { this.current = current; }
}
