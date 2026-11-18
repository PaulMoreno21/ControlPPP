package ec.edu.espe.practicas.dto;

import jakarta.validation.constraints.*;

public record UpdateGeneralAdministratorRequest(
        @NotBlank(message = "Ingresa la identificación.")
        @Pattern(regexp = ManagedUserValidation.IDENTIFICATION,
                message = "La identificación debe contener entre 1 y 10 letras o números, sin espacios.") String nationalId,
        @NotBlank(message = "Ingresa los nombres.") @Size(max = 100, message = "Los nombres admiten hasta 100 caracteres.")
        @Pattern(regexp = ManagedUserValidation.NAME, message = "Ingresa nombres con letras y separadores válidos.") String firstName,
        @NotBlank(message = "Ingresa los apellidos.") @Size(max = 100, message = "Los apellidos admiten hasta 100 caracteres.")
        @Pattern(regexp = ManagedUserValidation.NAME, message = "Ingresa apellidos con letras y separadores válidos.") String lastName,
        @NotBlank(message = "Ingresa el correo.") @Email(message = "Ingresa un correo válido.")
        @Pattern(regexp = ManagedUserValidation.EMAIL, message = "Ingresa un correo válido.")
        @Size(max = 150, message = "El correo admite hasta 150 caracteres.") String email) {
    public UpdateGeneralAdministratorRequest {
        nationalId = ManagedUserValidation.trim(nationalId);
        firstName = ManagedUserValidation.trim(firstName);
        lastName = ManagedUserValidation.trim(lastName);
        email = ManagedUserValidation.trim(email);
    }
}
