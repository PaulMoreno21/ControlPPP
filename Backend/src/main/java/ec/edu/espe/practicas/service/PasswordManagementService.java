package ec.edu.espe.practicas.service;

import ec.edu.espe.practicas.dto.ChangePasswordRequest;
import ec.edu.espe.practicas.dto.PasswordChangeResponse;
import ec.edu.espe.practicas.dto.ResetLocalPasswordRequest;
import ec.edu.espe.practicas.security.ActiveSessionRegistry;
import jakarta.persistence.EntityManager;
import ec.edu.espe.practicas.model.AccountType;
import ec.edu.espe.practicas.model.AuditLog;
import ec.edu.espe.practicas.model.RoleName;
import ec.edu.espe.practicas.model.User;
import ec.edu.espe.practicas.model.UserRole;
import ec.edu.espe.practicas.repository.AuditLogRepository;
import ec.edu.espe.practicas.repository.UserRepository;
import ec.edu.espe.practicas.repository.UserRoleRepository;
import ec.edu.espe.practicas.service.exception.BusinessRuleViolationException;
import ec.edu.espe.practicas.service.exception.ResourceNotFoundException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PasswordManagementService {
    private final AuthenticatedUserService authenticatedUserService;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final ActiveSessionRegistry sessions;
    private final EntityManager entityManager;

    public PasswordManagementService(
            AuthenticatedUserService authenticatedUserService,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            AuditLogRepository auditLogRepository,
            PasswordEncoder passwordEncoder,
            Clock clock, ActiveSessionRegistry sessions, EntityManager entityManager) {
        this.authenticatedUserService = authenticatedUserService;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.sessions = sessions;
        this.entityManager = entityManager;
    }

    public PasswordChangeResponse changeOwnPassword(Authentication authentication, ChangePasswordRequest request) {
        User user = authenticatedUserService.requireAuthenticatedUserForPasswordChange(authentication);
        user = userRepository.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No existe el usuario indicado."));
        // Authentication may already have loaded this managed entity before waiting for the row lock.
        entityManager.refresh(user);
        if (!Integer.valueOf(1).equals(user.getActive())) throw new AccessDeniedException("La cuenta no está activa.");
        requireLocalAccount(user);
        validatePassword(request.newPassword());
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleViolationException("La contraseña actual no es correcta.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessRuleViolationException("La nueva contraseña debe ser diferente a la actual.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangeRequired(false);
        userRepository.save(user);
        auditLogRepository.save(AuditLog.record(
                user, "CAMBIAR_CONTRASENA", "TCWUSUARIOS", user.getId(), LocalDateTime.now(clock),
                "El usuario actualizó su contraseña local."));
        sessions.revokeAllAfterCommit(user.getId());
        return new PasswordChangeResponse(false);
    }

    public void resetLocalPassword(
            Authentication authentication, Long laboratoryId, Long userId, ResetLocalPasswordRequest request) {
        User administrator = authenticatedUserService
                .requireAdministratorForLaboratory(authentication, laboratoryId);
        if (administrator.getId().equals(userId)) {
            throw new BusinessRuleViolationException("Para tu propia cuenta, usa el cambio de contraseña.");
        }
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el usuario indicado."));
        validatePassword(request.newPassword());

        List<UserRole> roles = userRoleRepository.findByUserId(userId);
        boolean hasRoleInLaboratory = roles.stream().anyMatch(role -> role.getLaboratory() != null
                && laboratoryId.equals(role.getLaboratory().getId()));
        boolean globalAdministrator = authenticatedUserService.hasGlobalRole(administrator, RoleName.ADMIN_GENERAL);
        boolean targetIsGlobalAdministrator = roles.stream().anyMatch(role -> role.getLaboratory() == null
                && role.getRole().getName() == RoleName.ADMIN_GENERAL);
        if (!hasRoleInLaboratory && !(globalAdministrator && targetIsGlobalAdministrator)) {
            throw new ResourceNotFoundException("No existe el usuario en el laboratorio indicado.");
        }
        boolean targetHasOtherScopes = roles.stream().anyMatch(role -> role.getLaboratory() == null
                || !laboratoryId.equals(role.getLaboratory().getId()));
        if (!globalAdministrator && targetHasOtherScopes) {
            throw new AccessDeniedException("No puedes restablecer la contraseña de una cuenta compartida.");
        }
        applyAdministrativeReset(administrator, user, request.newPassword());
    }

    public void resetGeneralAdministratorPassword(Authentication authentication, Long userId, ResetLocalPasswordRequest request) {
        User administrator = authenticatedUserService.requireGlobalRole(authentication, RoleName.ADMIN_GENERAL);
        if (administrator.getId().equals(userId)) {
            throw new BusinessRuleViolationException("Para tu propia cuenta, usa el cambio de contraseña.");
        }
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe la cuenta indicada."));
        boolean global = userRoleRepository.findByUserId(userId).stream().anyMatch(role ->
                role.getRole().getName() == RoleName.ADMIN_GENERAL && role.getLaboratory() == null);
        if (!global) throw new ResourceNotFoundException("La cuenta no es un administrador general.");
        applyAdministrativeReset(administrator, user, request.newPassword());
    }

    private void applyAdministrativeReset(User administrator, User user, String password) {
        requireLocalAccount(user);
        validatePassword(password);
        if (passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessRuleViolationException("La contraseña temporal debe ser diferente a la actual.");
        }

        user.setPasswordHash(passwordEncoder.encode(password));
        user.setPasswordChangeRequired(true);
        userRepository.save(user);
        auditLogRepository.save(AuditLog.record(
                administrator, "RESTABLECER_CONTRASENA", "TCWUSUARIOS", user.getId(), LocalDateTime.now(clock),
                "Se restableció la contraseña local de " + user.getFirstName() + " " + user.getLastName()
                        + "; deberá cambiarla al iniciar sesión."));
        sessions.revokeAllAfterCommit(user.getId());
    }

    private void requireLocalAccount(User user) {
        if (user.getAccountType() != AccountType.LOCAL) {
            throw new BusinessRuleViolationException(
                    "Esta cuenta es institucional y no tiene una contraseña local para modificar.");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank() || password.length() < 12) {
            throw new BusinessRuleViolationException(
                    "La contraseña debe tener al menos 12 caracteres.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessRuleViolationException(
                    "La contraseña es demasiado larga. Acórtala e inténtalo de nuevo.");
        }
    }
}
