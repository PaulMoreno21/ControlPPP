package ec.edu.espe.practicas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PracticeReportRow(
        Long traineeId,
        String traineeName,
        String traineeEmail,
        String laboratoryName,
        LocalDate date,
        LocalDateTime checkIn,
        LocalDateTime checkOut,
        BigDecimal attendanceHours,
        BigDecimal acceptedActivityHours,
        List<ReportActivityRow> activities) { }
