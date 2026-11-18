package ec.edu.espe.practicas.controller;

import ec.edu.espe.practicas.dto.CreateManagedUserRequest;
import ec.edu.espe.practicas.dto.ResetLocalPasswordRequest;
import ec.edu.espe.practicas.dto.ManagedUserResponse;
import ec.edu.espe.practicas.dto.PagedResponse;
import ec.edu.espe.practicas.dto.UserStatusRequest;
import ec.edu.espe.practicas.dto.UpdateManagedUserRequest;
import ec.edu.espe.practicas.service.PasswordManagementService;
import ec.edu.espe.practicas.service.UserAdministrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/laboratories/{laboratoryId}/users")
public class UserAdministrationController {
    private final UserAdministrationService userAdministrationService;
    private final PasswordManagementService passwordManagementService;

    public UserAdministrationController(
            UserAdministrationService userAdministrationService,
            PasswordManagementService passwordManagementService) {
        this.userAdministrationService = userAdministrationService;
        this.passwordManagementService = passwordManagementService;
    }

    @GetMapping
    public List<ManagedUserResponse> listUsers(
            Authentication authentication, @PathVariable @Positive(message = "Selecciona un laboratorio válido.") Long laboratoryId) {
        return userAdministrationService.listUsers(authentication, laboratoryId);
    }

    @GetMapping("/page")
    public PagedResponse<ManagedUserResponse> listUsersPage(
            Authentication authentication,
            @PathVariable @Positive(message = "Selecciona un laboratorio válido.") Long laboratoryId,
            @RequestParam(required = false) @Size(max = 10, message = "La búsqueda admite hasta 10 caracteres.") @Pattern(regexp = "[A-Za-z0-9]*", message = "Busca por identificación usando letras o números, sin espacios.") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        return userAdministrationService.listUsersPage(authentication, laboratoryId, search, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ManagedUserResponse createUser(
            Authentication authentication,
            @PathVariable @Positive(message = "Selecciona un laboratorio válido.") Long laboratoryId,
            @Valid @RequestBody CreateManagedUserRequest request) {
        return userAdministrationService.createUser(authentication, laboratoryId, request);
    }

    @PutMapping("/{userId}")
    public ManagedUserResponse updateUser(
            Authentication authentication,
            @PathVariable @Positive(message = "Selecciona un laboratorio válido.") Long laboratoryId,
            @PathVariable @Positive(message = "Selecciona un usuario válido.") Long userId,
            @Valid @RequestBody UpdateManagedUserRequest request) {
        return userAdministrationService.updateUser(authentication, laboratoryId, userId, request);
    }

    @PatchMapping("/{userId}/status")
    public ManagedUserResponse updateUserStatus(
            Authentication authentication,
            @PathVariable @Positive(message = "Selecciona un laboratorio válido.") Long laboratoryId,
            @PathVariable @Positive(message = "Selecciona un usuario válido.") Long userId,
            @Valid @RequestBody UserStatusRequest request) {
        return userAdministrationService.updateUserStatus(
                authentication, laboratoryId, userId, request.active());
    }

    @PutMapping("/{userId}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetLocalPassword(
            Authentication authentication,
            @PathVariable @Positive(message = "Selecciona un laboratorio válido.") Long laboratoryId,
            @PathVariable @Positive(message = "Selecciona un usuario válido.") Long userId,
            @Valid @RequestBody ResetLocalPasswordRequest request) {
        passwordManagementService.resetLocalPassword(authentication, laboratoryId, userId, request);
    }
}
