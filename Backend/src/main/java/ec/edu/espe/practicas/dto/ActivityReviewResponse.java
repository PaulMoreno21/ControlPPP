package ec.edu.espe.practicas.dto;

import ec.edu.espe.practicas.model.ActivityReview;
import ec.edu.espe.practicas.model.ReviewDecision;
import java.time.LocalDateTime;

public record ActivityReviewResponse(
        Long id,
        Long submissionId,
        ReviewDecision decision,
        String observation,
        LocalDateTime reviewedAt) {

    public static ActivityReviewResponse from(ActivityReview review) {
        return new ActivityReviewResponse(
                review.getId(),
                review.getSubmission().getId(),
                review.getDecision(),
                review.getObservation(),
                review.getReviewedAt());
    }
}
