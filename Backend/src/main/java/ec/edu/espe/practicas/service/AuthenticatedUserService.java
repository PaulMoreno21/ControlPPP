package ec.edu.espe.practicas.service;

import ec.edu.espe.practicas.model.RoleName;
import ec.edu.espe.practicas.model.User;
import ec.edu.espe.practicas.model.UserRole;
import ec.edu.espe.practicas.repository.UserRepository;
import ec.edu.espe.practicas.repository.UserRoleRepository;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthenticatedUserService {
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final String identityClaim;

    public AuthenticatedUserService(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            @Value("${app.security.identity-claim:email}") String identityClaim) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.identityClaim = identityClaim;
    }

    public User requireRole(Authentication authentication, RoleName requiredRole) {
        User user = requireAuthenticatedUser(authentication);

        boolean hasRole = userRoleRepository.findByUserId(user.getId()).stream()
                .map(UserRole::getRole)
                .filter(Objects::nonNull)
                .anyMatch(role -> role.getName() == requiredRole);
        if (!hasRole) {
            throw new AccessDeniedException("El usuario no tiene el rol requerido para esta operación.");
        }
        return user;
    }

    public User requireAdministratorForLaboratory(Authentication authentication, Long laboratoryId) {
        User user = requireAuthenticatedUser(authentication);
        boolean authorized = userRoleRepository.findByUserId(user.getId()).stream()
                .filter(Objects::nonNull)
                .anyMatch(userRole -> {
                    RoleName roleName = userRole.getRole().getName();
                    Long assignedLaboratoryId = userRole.getLaboratory() == null
                            ? null
                            : userRole.getLaboratory().getId();
                    return (roleName == RoleName.ADMIN_GENERAL && assignedLaboratoryId == null)
                            || (roleName == RoleName.ADMIN_LABORATORIO
                                    && Objects.equals(assignedLaboratoryId, laboratoryId));
                });
        if (!authorized) {
            throw new AccessDeniedException("No tiene permisos administrativos para este laboratorio.");
        }
        return user;
    }

    public User requireGlobalRole(Authentication authentication, RoleName requiredRole) {
        User user = requireAuthenticatedUser(authentication);
        boolean hasGlobalRole = userRoleRepository.findByUserId(user.getId()).stream()
                .anyMatch(userRole -> userRole.getRole().getName() == requiredRole
                        && userRole.getLaboratory() == null);
        if (!hasGlobalRole) {
            throw new AccessDeniedException("Se requiere el rol global " + requiredRole + ".");
        }
        return user;
    }

    public boolean hasGlobalRole(User user, RoleName roleName) {
        return userRoleRepository.findByUserId(user.getId()).stream()
                .anyMatch(userRole -> userRole.getRole().getName() == roleName
                        && userRole.getLaboratory() == null);
    }

    public User requireAuthenticatedUser(Authentication authentication) {
        User user = resolveAuthenticatedUser(authentication);
        if (user.isPasswordChangeRequired()) {
            throw new AccessDeniedException("Debes cambiar tu contraseña temporal antes de continuar.");
        }
        return user;
    }

    public User requireAuthenticatedUserForPasswordChange(Authentication authentication) {
        return resolveAuthenticatedUser(authentication);
    }

    private User resolveAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AccessDeniedException("Se requiere una sesión válida.");
        }

        String identity = jwt.getClaimAsString(identityClaim);
        if (identity == null || identity.isBlank()) {
            throw new AccessDeniedException("El token no contiene la identidad configurada.");
        }

        return userRepository.findByEmailIgnoreCase(identity)
                .filter(candidate -> Integer.valueOf(1).equals(candidate.getActive()))
                .orElseThrow(() -> new AccessDeniedException(
                        "La identidad no tiene una cuenta activa en el sistema."));
    }
}
