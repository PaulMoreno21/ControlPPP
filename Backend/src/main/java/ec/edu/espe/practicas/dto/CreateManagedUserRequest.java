package ec.edu.espe.practicas.dto;

import ec.edu.espe.practicas.model.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record CreateManagedUserRequest(
        @NotBlank(message = "Ingresa la identificación.")
        @Pattern(regexp = ManagedUserValidation.IDENTIFICATION,
                message = "La identificación debe contener entre 1 y 10 letras o números, sin espacios.") String nationalId,
        @NotBlank(message = "Ingresa los nombres.") @Size(max = 100, message = "Los nombres admiten hasta 100 caracteres.")
        @Pattern(regexp = ManagedUserValidation.NAME, message = "Los nombres deben contener letras y separadores válidos.") String firstName,
        @NotBlank(message = "Ingresa los apellidos.") @Size(max = 100, message = "Los apellidos admiten hasta 100 caracteres.")
        @Pattern(regexp = ManagedUserValidation.NAME, message = "Los apellidos deben contener letras y separadores válidos.") String lastName,
        @NotBlank(message = "Ingresa el correo electrónico.") @Email(message = "Ingresa un correo electrónico válido.")
        @Pattern(regexp = ManagedUserValidation.EMAIL, message = "Ingresa un correo electrónico válido.")
        @Size(max = 150, message = "El correo admite hasta 150 caracteres.") String email,
        RoleName role,
        @NotNull(message = "Selecciona el tipo de cuenta.") Boolean localAccount,
        String password,
        @Size(max = 4, message = "Selecciona únicamente los roles disponibles.")
        List<@NotNull(message = "Selecciona un rol válido.") RoleName> roles) {

    public CreateManagedUserRequest {
        nationalId = ManagedUserValidation.trim(nationalId);
        firstName = ManagedUserValidation.trim(firstName);
        lastName = ManagedUserValidation.trim(lastName);
        email = ManagedUserValidation.trim(email);
    }

    public List<RoleName> requestedRoles() {
        if (roles != null && !roles.isEmpty()) {
            return roles.stream().filter(java.util.Objects::nonNull).distinct().toList();
        }
        return role == null ? List.of() : List.of(role);
    }
}
