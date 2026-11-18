package ec.edu.espe.practicas.dto;

import java.math.BigDecimal;

public record TutorTraineeActivitiesPageResponse(
        TutorTraineeResponse trainee,
        BigDecimal acceptedHoursTotal,
        long acceptedActivitiesCount,
        long pendingActivitiesCount,
        PagedResponse<TutorActivityResponse> activities) {
}
