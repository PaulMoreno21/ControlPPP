package ec.edu.espe.practicas.controller;

import ec.edu.espe.practicas.dto.*;
import ec.edu.espe.practicas.service.GeneralAdministratorService;
import ec.edu.espe.practicas.service.PasswordManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/general-administrators")
public class GeneralAdministratorController {
    private final GeneralAdministratorService administrators;
    private final PasswordManagementService passwords;

    public GeneralAdministratorController(GeneralAdministratorService administrators, PasswordManagementService passwords) {
        this.administrators = administrators;
        this.passwords = passwords;
    }

    @GetMapping("/page")
    public PagedResponse<ManagedUserResponse> list(Authentication auth,
            @RequestParam(required = false) @Size(max = 10, message = "La búsqueda admite hasta 10 caracteres.")
            @Pattern(regexp = "[A-Za-z0-9]*", message = "Busca con letras o números, sin espacios.") String search,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "15") int size) {
        return administrators.list(auth, search, page, size);
    }

    @PutMapping("/{id}")
    public ManagedUserResponse update(Authentication auth, @PathVariable @Positive(message = "Selecciona una cuenta válida.") Long id,
            @Valid @RequestBody UpdateGeneralAdministratorRequest request) {
        return administrators.update(auth, id, request);
    }

    @PatchMapping("/{id}/status")
    public ManagedUserResponse status(Authentication auth, @PathVariable @Positive(message = "Selecciona una cuenta válida.") Long id,
            @Valid @RequestBody UserStatusRequest request) {
        return administrators.setActive(auth, id, request.active());
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(Authentication auth, @PathVariable @Positive(message = "Selecciona una cuenta válida.") Long id,
            @Valid @RequestBody ResetLocalPasswordRequest request) {
        passwords.resetGeneralAdministratorPassword(auth, id, request);
    }
}
