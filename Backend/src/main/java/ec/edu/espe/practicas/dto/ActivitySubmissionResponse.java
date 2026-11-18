package ec.edu.espe.practicas.dto;

import ec.edu.espe.practicas.model.ActivitySubmission;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ActivitySubmissionResponse(
        Long id,
        Long activityId,
        Long traineeId,
        String traineeName,
        LocalDate activityDate,
        String description,
        BigDecimal durationHours,
        Integer submissionNumber,
        LocalDateTime submittedAt) {

    public static ActivitySubmissionResponse from(ActivitySubmission submission) {
        var activity = submission.getActivity();
        var trainee = activity.getTrainee();
        return new ActivitySubmissionResponse(
                submission.getId(),
                activity.getId(),
                trainee.getId(),
                trainee.getFirstName() + " " + trainee.getLastName(),
                activity.getDate(),
                activity.getDescription(),
                activity.getDurationHours(),
                submission.getSubmissionNumber(),
                submission.getSubmittedAt());
    }
}
