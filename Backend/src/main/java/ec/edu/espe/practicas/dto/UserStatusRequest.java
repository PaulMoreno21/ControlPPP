package ec.edu.espe.practicas.dto;

import jakarta.validation.constraints.NotNull;

public record UserStatusRequest(@NotNull(message = "Selecciona si la cuenta estará activa o inactiva.") Boolean active) {
}
