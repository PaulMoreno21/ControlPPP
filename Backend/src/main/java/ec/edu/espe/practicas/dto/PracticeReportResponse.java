package ec.edu.espe.practicas.dto;

import java.math.BigDecimal;

public record PracticeReportResponse(
        PagedResponse<PracticeReportRow> rows,
        long totalAttendanceDays,
        BigDecimal attendanceHours,
        BigDecimal acceptedActivityHours) { }
