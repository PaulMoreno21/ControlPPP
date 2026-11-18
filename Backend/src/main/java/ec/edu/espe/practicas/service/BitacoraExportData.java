package ec.edu.espe.practicas.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BitacoraExportData(
        Long traineeId,
        String traineeName,
        LocalDate from,
        LocalDate to,
        List<ActivityRow> activities) {

    public BigDecimal acceptedHours() {
        return activities.stream()
                .map(ActivityRow::hours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public record ActivityRow(
            LocalDate date,
            String traineeName,
            String description,
            BigDecimal hours,
            String checkIn,
            String checkOut) { }
}
