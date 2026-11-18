package ec.edu.espe.practicas.dto;

import ec.edu.espe.practicas.model.Attendance;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AttendanceResponse(
        Long id,
        LocalDate date,
        LocalDateTime checkIn,
        LocalDateTime checkOut) {

    public static AttendanceResponse from(Attendance attendance) {
        return new AttendanceResponse(
                attendance.getId(),
                attendance.getDate(),
                attendance.getCheckIn(),
                attendance.getCheckOut());
    }
}
