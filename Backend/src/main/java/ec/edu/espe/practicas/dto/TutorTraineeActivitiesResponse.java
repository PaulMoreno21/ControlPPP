package ec.edu.espe.practicas.dto;

import java.math.BigDecimal;
import java.util.List;

public record TutorTraineeActivitiesResponse(
        TutorTraineeResponse trainee,
        BigDecimal acceptedHoursTotal,
        List<TutorActivityResponse> activities) {
}
