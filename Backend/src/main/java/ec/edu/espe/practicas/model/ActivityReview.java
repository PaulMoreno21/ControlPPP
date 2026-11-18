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
import jakarta.persistence.OneToOne;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Entity
@Table(name = "TCWREVISIONES", uniqueConstraints = @UniqueConstraint(
        name = "UK_TCWREVISIONES_ENVIO", columnNames = "TCWENVIOACTIVIDADES_ID"))
@SequenceGenerator(name = "activity_review_seq", sequenceName = "SEQ_CWREVISIONES", allocationSize = 1)
public class ActivityReview {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "activity_review_seq")
    @Column(name = "TCWREVISIONES_ID", nullable = false)
    private Long id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "TCWENVIOACTIVIDADES_ID", nullable = false, unique = true)
    private ActivitySubmission submission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "TCWUSUARIOS_ID_TUTOR", nullable = false)
    private User tutor;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "TCWREVISIONES_DECISION", nullable = false, length = 10)
    private ReviewDecision decision;

    @Lob
    @Column(name = "TCWREVISIONES_OBSERVACION")
    private String observation;

    @NotNull
    @Column(name = "TCWREVISIONES_FECHA", nullable = false)
    private LocalDateTime reviewedAt;

    protected ActivityReview() { }

    public static ActivityReview create(
            ActivitySubmission submission,
            User tutor,
            ReviewDecision decision,
            String observation,
            LocalDateTime reviewedAt) {
        ActivityReview review = new ActivityReview();
        review.submission = submission;
        review.tutor = tutor;
        review.decision = decision;
        review.observation = observation;
        review.reviewedAt = reviewedAt;
        return review;
    }

    public Long getId() { return id; }
    public ActivitySubmission getSubmission() { return submission; }
    public void setSubmission(ActivitySubmission submission) { this.submission = submission; }
    public User getTutor() { return tutor; }
    public void setTutor(User tutor) { this.tutor = tutor; }
    public ReviewDecision getDecision() { return decision; }
    public void setDecision(ReviewDecision decision) { this.decision = decision; }
    public String getObservation() { return observation; }
    public void setObservation(String observation) { this.observation = observation; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
}
