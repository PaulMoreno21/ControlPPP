package ec.edu.espe.practicas.dto;

import ec.edu.espe.practicas.model.Activity;
import ec.edu.espe.practicas.model.ActivityStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ActivityResponse(
        Long id,
        LocalDate date,
        String description,
        BigDecimal durationHours,
        ActivityStatus status,
        LocalDateTime lastSubmittedAt,
        String observation,
        LocalDateTime reviewedAt) {

    public static ActivityResponse from(Activity activity) {
        return from(activity, null);
    }

    public static ActivityResponse from(Activity activity, ActivityReviewSummary review) {
        return new ActivityResponse(
                activity.getId(),
                activity.getDate(),
                activity.getDescription(),
                activity.getDurationHours(),
                activity.getStatus(),
                activity.getLastSubmittedAt(),
                review == null ? null : review.observation(),
                review == null ? null : review.reviewedAt());
    }
}
