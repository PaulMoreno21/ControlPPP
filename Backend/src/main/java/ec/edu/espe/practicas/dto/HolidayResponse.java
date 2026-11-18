package ec.edu.espe.practicas.dto;

import ec.edu.espe.practicas.model.Holiday;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record HolidayResponse(
        Long id,
        LocalDate date,
        String description,
        boolean active,
        Long registeredById,
        String registeredByName,
        LocalDateTime registeredAt) {

    public static HolidayResponse from(Holiday holiday) {
        var user = holiday.getRegisteredBy();
        return new HolidayResponse(
                holiday.getId(),
                holiday.getDate(),
                holiday.getDescription(),
                Integer.valueOf(1).equals(holiday.getActive()),
                user.getId(),
                user.getFirstName() + " " + user.getLastName(),
                holiday.getRegisteredAt());
    }
}
