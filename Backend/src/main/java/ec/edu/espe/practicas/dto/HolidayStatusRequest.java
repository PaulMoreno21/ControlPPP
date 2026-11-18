package ec.edu.espe.practicas.dto;

import jakarta.validation.constraints.NotNull;

public record HolidayStatusRequest(@NotNull Boolean active) {
}
