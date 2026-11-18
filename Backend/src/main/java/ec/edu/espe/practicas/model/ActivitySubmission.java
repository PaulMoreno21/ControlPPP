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
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;

@Entity
@Table(name = "TCWENVIOACTIVIDADES", uniqueConstraints = @UniqueConstraint(
        name = "UK_TCWENVIOACTIVIDADES_ACT_NUM",
        columnNames = {"TCWACTIVIDADES_ID", "TCWENVIOACTIVIDADES_NUMERO"}))
@SequenceGenerator(name = "activity_submission_seq", sequenceName = "SEQ_CWENVIOS", allocationSize = 1)
public class ActivitySubmission {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "activity_submission_seq")
    @Column(name = "TCWENVIOACTIVIDADES_ID", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "TCWACTIVIDADES_ID", nullable = false)
    private Activity activity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "TCWUSUARIOS_ID_TUTOR", nullable = false)
    private User tutor;

    @NotNull @Positive
    @Column(name = "TCWENVIOACTIVIDADES_NUMERO", nullable = false)
    private Integer submissionNumber;

    @NotNull
    @Column(name = "TCWENVIOACTIVIDADES_FECHA", nullable = false)
    private LocalDateTime submittedAt;

    protected ActivitySubmission() { }

    public static ActivitySubmission create(
            Activity activity, User tutor, Integer submissionNumber, LocalDateTime submittedAt) {
        ActivitySubmission submission = new ActivitySubmission();
        submission.activity = activity;
        submission.tutor = tutor;
        submission.submissionNumber = submissionNumber;
        submission.submittedAt = submittedAt;
        return submission;
    }

    public Long getId() { return id; }
    public Activity getActivity() { return activity; }
    public void setActivity(Activity activity) { this.activity = activity; }
    public User getTutor() { return tutor; }
    public void setTutor(User tutor) { this.tutor = tutor; }
    public Integer getSubmissionNumber() { return submissionNumber; }
    public void setSubmissionNumber(Integer submissionNumber) { this.submissionNumber = submissionNumber; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
}
