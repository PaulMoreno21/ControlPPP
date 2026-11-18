package ec.edu.espe.practicas.dto;

import jakarta.validation.constraints.NotBlank;

public record ResetLocalPasswordRequest(@NotBlank(message = "Ingresa la contraseña temporal.") String newPassword) { }
