package ec.edu.espe.practicas.dto;

import ec.edu.espe.practicas.model.Activity;
import ec.edu.espe.practicas.model.ActivityStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ReportActivityRow(Long id, LocalDate date, String description, BigDecimal hours, ActivityStatus status) {
    public static ReportActivityRow from(Activity activity) {
        return new ReportActivityRow(activity.getId(), activity.getDate(), activity.getDescription(),
                activity.getDurationHours(), activity.getStatus());
    }
}
