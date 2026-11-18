package ec.edu.espe.practicas.service;

import ec.edu.espe.practicas.dto.*;
import ec.edu.espe.practicas.model.*;
import ec.edu.espe.practicas.repository.*;
import ec.edu.espe.practicas.security.ActiveSessionRegistry;
import ec.edu.espe.practicas.service.exception.*;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GeneralAdministratorService {
    private final AuthenticatedUserService authenticated;
    private final UserRepository users;
    private final UserRoleRepository userRoles;
    private final AuditLogRepository audit;
    private final GlobalAdministratorPolicy policy;
    private final ActiveSessionRegistry sessions;
    private final Clock clock;
    private final EntityManager entityManager;

    public GeneralAdministratorService(AuthenticatedUserService authenticated, UserRepository users,
            UserRoleRepository userRoles, AuditLogRepository audit, GlobalAdministratorPolicy policy,
            ActiveSessionRegistry sessions, Clock clock, EntityManager entityManager) {
        this.authenticated = authenticated;
        this.users = users;
        this.userRoles = userRoles;
        this.audit = audit;
        this.policy = policy;
        this.sessions = sessions;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public PagedResponse<ManagedUserResponse> list(Authentication auth, String search, int page, int size) {
        authenticated.requireGlobalRole(auth, RoleName.ADMIN_GENERAL);
        String term = search == null || search.isBlank() ? null : "%" + search.trim() + "%";
        var result = users.findGlobalAdministrators(RoleName.ADMIN_GENERAL, term,
                PageableFactory.create(page, size, Sort.by("lastName", "firstName", "id")));
        var ids = result.getContent().stream().map(User::getId).toList();
        Map<Long, List<UserRole>> byUser = ids.isEmpty() ? Map.of() : userRoles.findByUserIdIn(ids).stream()
                .collect(Collectors.groupingBy(role -> role.getUser().getId()));
        return PagedResponse.from(result, user -> response(user, byUser.getOrDefault(user.getId(), List.of())));
    }

    public ManagedUserResponse update(Authentication auth, Long id, UpdateGeneralAdministratorRequest request) {
        User actor = authenticated.requireGlobalRole(auth, RoleName.ADMIN_GENERAL);
        User user = lockedAdministrator(id);
        var roles = userRoles.findByUserId(id);
        String email = request.email().toLowerCase(Locale.ROOT);
        users.findByNationalId(request.nationalId()).filter(existing -> !existing.getId().equals(id)).ifPresent(existing -> {
            throw new BusinessRuleViolationException("Ya existe una cuenta con esa identificación.");
        });
        users.findByEmailIgnoreCase(email).filter(existing -> !existing.getId().equals(id)).ifPresent(existing -> {
            throw new BusinessRuleViolationException("Ya existe una cuenta con ese correo.");
        });
        boolean emailChanged = !email.equalsIgnoreCase(user.getEmail());
        user.setNationalId(request.nationalId());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(email);
        users.save(user);
        record(actor, user, "EDITAR_USUARIO", "Se actualizaron los datos del administrador general ");
        if (emailChanged) sessions.revokeAllAfterCommit(id);
        // Global and laboratory roles are retained; no arbitrary lab is needed to edit identity.
        return response(user, roles);
    }

    public ManagedUserResponse setActive(Authentication auth, Long id, boolean active) {
        User actor = authenticated.requireGlobalRole(auth, RoleName.ADMIN_GENERAL);
        if (actor.getId().equals(id)) {
            throw new BusinessRuleViolationException("No puedes cambiar el estado de tu propia cuenta.");
        }
        // Take role lock before target-user lock, also used by the existing laboratory routes.
        if (!active) policy.requireAnotherActiveAdministrator(id);
        User user = lockedAdministrator(id);
        var roles = userRoles.findByUserId(id);
        if (user.getActive() != (active ? 1 : 0)) {
            user.setActive(active ? 1 : 0);
            users.save(user);
            record(actor, user, active ? "ACTIVAR_USUARIO" : "DESACTIVAR_USUARIO",
                    active ? "Se activó el acceso del administrador general " : "Se desactivó el acceso del administrador general ");
            if (!active) sessions.revokeAllAfterCommit(id);
        }
        return response(user, roles);
    }

    private User lockedAdministrator(Long id) {
        User user = users.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("No existe la cuenta indicada."));
        entityManager.refresh(user);
        boolean global = userRoles.findByUserId(id).stream().anyMatch(role ->
                role.getRole().getName() == RoleName.ADMIN_GENERAL && role.getLaboratory() == null);
        if (!global) throw new ResourceNotFoundException("La cuenta no es un administrador general.");
        return user;
    }

    private ManagedUserResponse response(User user, List<UserRole> roles) {
        return ManagedUserResponse.from(user, roles.stream().map(role -> role.getRole().getName()).toList());
    }

    private void record(User actor, User target, String action, String prefix) {
        audit.save(AuditLog.record(actor, action, "TCWUSUARIOS", target.getId(), LocalDateTime.now(clock),
                prefix + target.getFirstName() + " " + target.getLastName() + "."));
    }
}
