package ec.edu.espe.practicas.dto;

import ec.edu.espe.practicas.model.Activity;
import ec.edu.espe.practicas.model.ActivityReview;
import ec.edu.espe.practicas.model.ActivityStatus;
import ec.edu.espe.practicas.model.ActivitySubmission;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TutorActivityResponse(
        Long activityId,
        LocalDate date,
        String description,
        BigDecimal durationHours,
        String status,
        Long submissionId,
        Integer submissionNumber,
        LocalDateTime submittedAt,
        String observation,
        LocalDateTime reviewedAt) {

    public static TutorActivityResponse from(
            Activity activity, ActivitySubmission submission, ActivityReview review) {
        String status = activity.getStatus() == ActivityStatus.ENVIADA
                ? "PENDIENTE"
                : activity.getStatus().name();
        return new TutorActivityResponse(
                activity.getId(),
                activity.getDate(),
                activity.getDescription(),
                activity.getDurationHours(),
                status,
                submission == null ? null : submission.getId(),
                submission == null ? null : submission.getSubmissionNumber(),
                submission == null ? activity.getLastSubmittedAt() : submission.getSubmittedAt(),
                review == null ? null : review.getObservation(),
                review == null ? null : review.getReviewedAt());
    }
}
