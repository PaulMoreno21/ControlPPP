package ec.edu.espe.practicas.service;

import ec.edu.espe.practicas.dto.CreateManagedUserRequest;
import ec.edu.espe.practicas.dto.ManagedUserResponse;
import ec.edu.espe.practicas.dto.PagedResponse;
import ec.edu.espe.practicas.dto.UpdateManagedUserRequest;
import ec.edu.espe.practicas.model.AuditLog;
import ec.edu.espe.practicas.model.AccountType;
import ec.edu.espe.practicas.model.Laboratory;
import ec.edu.espe.practicas.model.Role;
import ec.edu.espe.practicas.model.RoleName;
import ec.edu.espe.practicas.model.User;
import ec.edu.espe.practicas.model.UserRole;
import ec.edu.espe.practicas.repository.AuditLogRepository;
import ec.edu.espe.practicas.repository.AssignmentRepository;
import ec.edu.espe.practicas.repository.LaboratoryRepository;
import ec.edu.espe.practicas.repository.RoleRepository;
import ec.edu.espe.practicas.repository.UserRepository;
import ec.edu.espe.practicas.repository.UserRoleRepository;
import ec.edu.espe.practicas.service.exception.BusinessRuleViolationException;
import ec.edu.espe.practicas.service.exception.ResourceNotFoundException;
import ec.edu.espe.practicas.security.ActiveSessionRegistry;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserAdministrationService {
    private static final Integer ACTIVE = 1;
    private static final Integer INACTIVE = 0;

    private final AuthenticatedUserService authenticatedUserService;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final AuditLogRepository auditLogRepository;
    private final AssignmentRepository assignmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final GlobalAdministratorPolicy globalAdministratorPolicy;
    private final ActiveSessionRegistry sessions;

    public UserAdministrationService(
            AuthenticatedUserService authenticatedUserService,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            RoleRepository roleRepository,
            LaboratoryRepository laboratoryRepository,
            AuditLogRepository auditLogRepository,
            AssignmentRepository assignmentRepository,
            PasswordEncoder passwordEncoder,
            Clock clock, GlobalAdministratorPolicy globalAdministratorPolicy, ActiveSessionRegistry sessions) {
        this.authenticatedUserService = authenticatedUserService;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.laboratoryRepository = laboratoryRepository;
        this.auditLogRepository = auditLogRepository;
        this.assignmentRepository = assignmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.globalAdministratorPolicy = globalAdministratorPolicy;
        this.sessions = sessions;
    }

    @Transactional(readOnly = true)
    public List<ManagedUserResponse> listUsers(Authentication authentication, Long laboratoryId) {
        User administrator = authenticatedUserService
                .requireAdministratorForLaboratory(authentication, laboratoryId);
        boolean administratorIsGlobal = authenticatedUserService.hasGlobalRole(administrator, RoleName.ADMIN_GENERAL);
        laboratoryRepository.findById(laboratoryId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el laboratorio indicado."));
        Map<Long, List<UserRole>> rolesByUser = userRoleRepository.findByLaboratoryId(laboratoryId).stream()
                .collect(Collectors.groupingBy(
                        userRole -> userRole.getUser().getId(),
                        java.util.LinkedHashMap::new,
                        Collectors.toList()));

        return rolesByUser.values().stream()
                .map(roles -> ManagedUserResponse.from(
                        roles.getFirst().getUser(),
                        visibleRoles(
                                roles.getFirst().getUser().getId(),
                                roles.stream().map(userRole -> userRole.getRole().getName()).toList(),
                                administratorIsGlobal)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PagedResponse<ManagedUserResponse> listUsersPage(
            Authentication authentication, Long laboratoryId, String search, int page, int size) {
        User administrator = authenticatedUserService
                .requireAdministratorForLaboratory(authentication, laboratoryId);
        boolean administratorIsGlobal = authenticatedUserService.hasGlobalRole(administrator, RoleName.ADMIN_GENERAL);
        laboratoryRepository.findById(laboratoryId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el laboratorio indicado."));
        String normalizedSearch = search == null || search.isBlank() ? null : "%" + search.trim() + "%";
        var usersPage = userRoleRepository.findUsersByLaboratoryId(laboratoryId, normalizedSearch,
                PageableFactory.create(page, size, Sort.by(Sort.Order.asc("user.lastName"),
                        Sort.Order.asc("user.firstName"), Sort.Order.asc("user.nationalId"))));
        List<Long> userIds = usersPage.getContent().stream().map(User::getId).toList();
        Map<Long, List<UserRole>> rolesByUser = userIds.isEmpty() ? Map.of()
                : userRoleRepository.findByLaboratoryIdAndUserIdIn(laboratoryId, userIds).stream()
                        .collect(Collectors.groupingBy(userRole -> userRole.getUser().getId()));
        return PagedResponse.from(usersPage, user -> {
            List<RoleName> roles = rolesByUser.getOrDefault(user.getId(), List.of()).stream()
                    .map(userRole -> userRole.getRole().getName())
                    .toList();
            return ManagedUserResponse.from(user,
                    visibleRoles(user.getId(), roles, administratorIsGlobal));
        });
    }

    public ManagedUserResponse createUser(
            Authentication authentication, Long laboratoryId, CreateManagedUserRequest request) {
        User administrator = authenticatedUserService
                .requireAdministratorForLaboratory(authentication, laboratoryId);
        List<RoleName> requestedRoles = request.requestedRoles();
        if (requestedRoles.isEmpty()) {
            throw new BusinessRuleViolationException("Debe asignar al menos un rol al usuario.");
        }
        if (requestedRoles.contains(RoleName.PRACTICANTE) && requestedRoles.size() > 1) {
            throw new BusinessRuleViolationException(
                    "El rol PRACTICANTE no se puede combinar con otros roles.");
        }
        boolean globalAdministrator = authenticatedUserService.hasGlobalRole(administrator, RoleName.ADMIN_GENERAL);
        if (!globalAdministrator && requestedRoles.contains(RoleName.ADMIN_GENERAL)) {
            throw new AccessDeniedException(
                    "Un administrador de laboratorio no puede asignar el rol global ADMIN_GENERAL.");
        }
        if (!globalAdministrator && requestedRoles.stream().anyMatch(role ->
                role != RoleName.PRACTICANTE && role != RoleName.TUTOR_ESPE
                        && role != RoleName.ADMIN_LABORATORIO)) {
            throw new AccessDeniedException("No puede asignar uno o más roles solicitados.");
        }
        if (Boolean.TRUE.equals(request.localAccount())
                && (request.password() == null || request.password().isBlank() || request.password().length() < 12)) {
            throw new BusinessRuleViolationException(
                    "La contraseña local debe tener al menos 12 caracteres.");
        }
        if (Boolean.TRUE.equals(request.localAccount())
                && request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new BusinessRuleViolationException(
                    "La contraseña es demasiado larga. Acórtala e inténtalo de nuevo.");
        }
        if (!Boolean.TRUE.equals(request.localAccount()) && request.password() != null) {
            throw new BusinessRuleViolationException(
                    "No envíe una contraseña para una cuenta institucional.");
        }

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String nationalId = request.nationalId().trim();
        if (userRepository.findByNationalId(nationalId).isPresent()) {
            throw new BusinessRuleViolationException("Ya existe una cuenta con esa identificación.");
        }
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new BusinessRuleViolationException("Ya existe una cuenta con ese correo.");
        }

        Laboratory laboratory = laboratoryRepository.findById(laboratoryId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el laboratorio indicado."));
        List<Role> roles = requestedRoles.stream()
                .map(roleName -> roleRepository.findByName(roleName)
                        .orElseThrow(() -> new ResourceNotFoundException("No existe el rol solicitado: " + roleName)))
                .toList();

        String rawPassword = Boolean.TRUE.equals(request.localAccount())
                ? request.password()
                : Base64.getEncoder().encodeToString(UUID.randomUUID().toString().getBytes(
                        java.nio.charset.StandardCharsets.UTF_8));
        User user = User.create(
                nationalId,
                request.firstName().trim(),
                request.lastName().trim(),
                email,
                passwordEncoder.encode(rawPassword),
                Boolean.TRUE.equals(request.localAccount()) ? AccountType.LOCAL : AccountType.INSTITUTIONAL);
        User savedUser = userRepository.save(user);

        roles.forEach(role -> userRoleRepository.save(UserRole.assign(
                savedUser, role, role.getName() == RoleName.ADMIN_GENERAL ? null : laboratory)));
        auditLogRepository.save(AuditLog.record(
                administrator,
                "CREAR_USUARIO",
                "TCWUSUARIOS",
                savedUser.getId(),
                LocalDateTime.now(clock),
                "Se registró al usuario " + AuditMessages.name(savedUser)
                        + " con los roles: " + String.join(", ", roleLabels(requestedRoles))
                        + ", en el laboratorio «" + laboratory.getName() + "»."));

        return ManagedUserResponse.from(savedUser, requestedRoles);
    }

    public ManagedUserResponse updateUserStatus(
            Authentication authentication, Long laboratoryId, Long userId, Boolean active) {
        User administrator = authenticatedUserService
                .requireAdministratorForLaboratory(authentication, laboratoryId);
        if (administrator.getId().equals(userId)) {
            throw new BusinessRuleViolationException("No puede cambiar el estado de su propia cuenta.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el usuario indicado."));
        List<UserRole> roles = userRoleRepository.findByUserId(userId);
        boolean hasRoleInLaboratory = roles.stream()
                .anyMatch(userRole -> userRole.getLaboratory() != null
                        && laboratoryId.equals(userRole.getLaboratory().getId()));
        if (!hasRoleInLaboratory) {
            throw new ResourceNotFoundException("No existe el usuario en el laboratorio indicado.");
        }

        boolean administratorIsGlobal = userRoleRepository.findByUserId(administrator.getId()).stream()
                .anyMatch(userRole -> userRole.getRole().getName() == RoleName.ADMIN_GENERAL
                        && userRole.getLaboratory() == null);
        boolean targetHasOtherScopes = roles.stream()
                .anyMatch(userRole -> userRole.getLaboratory() == null
                        || !laboratoryId.equals(userRole.getLaboratory().getId()));
        if (!administratorIsGlobal && targetHasOtherScopes) {
            throw new AccessDeniedException(
                    "Un administrador de laboratorio no puede cambiar el estado de una cuenta compartida.");
        }

        int newStatus = Boolean.TRUE.equals(active) ? ACTIVE : INACTIVE;
        if (newStatus == INACTIVE && roles.stream().anyMatch(role ->
                role.getRole().getName() == RoleName.ADMIN_GENERAL && role.getLaboratory() == null)) {
            globalAdministratorPolicy.requireAnotherActiveAdministrator(userId);
        }
        user.setActive(newStatus);
        userRepository.save(user);
        auditLogRepository.save(AuditLog.record(
                administrator,
                newStatus == ACTIVE ? "ACTIVAR_USUARIO" : "DESACTIVAR_USUARIO",
                "TCWUSUARIOS",
                user.getId(),
                LocalDateTime.now(clock),
                "Se " + (newStatus == ACTIVE ? "activó" : "desactivó")
                        + " la cuenta de " + AuditMessages.name(user) + "."));
        if (newStatus == INACTIVE) sessions.revokeAllAfterCommit(userId);

        return ManagedUserResponse.from(user,
                visibleRoles(userId, roles.stream()
                        .filter(userRole -> userRole.getLaboratory() != null
                                && laboratoryId.equals(userRole.getLaboratory().getId()))
                        .map(userRole -> userRole.getRole().getName())
                        .toList(), administratorIsGlobal));
    }

    public ManagedUserResponse updateUser(
            Authentication authentication,
            Long laboratoryId,
            Long userId,
            UpdateManagedUserRequest request) {
        User administrator = authenticatedUserService
                .requireAdministratorForLaboratory(authentication, laboratoryId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el usuario indicado."));
        List<UserRole> roles = userRoleRepository.findByUserId(userId);
        boolean hasRoleInLaboratory = roles.stream()
                .anyMatch(userRole -> userRole.getLaboratory() != null
                        && laboratoryId.equals(userRole.getLaboratory().getId()));
        if (!hasRoleInLaboratory) {
            throw new ResourceNotFoundException("No existe el usuario en el laboratorio indicado.");
        }

        boolean administratorIsGlobal = authenticatedUserService.hasGlobalRole(administrator, RoleName.ADMIN_GENERAL);
        boolean targetHasOtherScopes = roles.stream()
                .anyMatch(userRole -> userRole.getLaboratory() == null
                        || !laboratoryId.equals(userRole.getLaboratory().getId()));
        if (!administratorIsGlobal && targetHasOtherScopes) {
            throw new AccessDeniedException(
                    "Un administrador de laboratorio no puede editar una cuenta compartida.");
        }

        List<RoleName> requestedRoles = request.roles().stream().distinct().toList();
        if (requestedRoles.isEmpty()) {
            throw new BusinessRuleViolationException("Debe asignar al menos un rol al usuario.");
        }
        if (requestedRoles.contains(RoleName.PRACTICANTE) && requestedRoles.size() > 1) {
            throw new BusinessRuleViolationException(
                    "El rol PRACTICANTE no se puede combinar con otros roles.");
        }
        if (requestedRoles.contains(RoleName.ADMIN_GENERAL) && !administratorIsGlobal) {
            throw new AccessDeniedException(
                    "Solo un Administrador General puede asignar el rol global ADMIN_GENERAL.");
        }

        Laboratory laboratory = laboratoryRepository.findById(laboratoryId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el laboratorio indicado."));
        List<Role> requestedRoleEntities = requestedRoles.stream()
                .map(roleName -> roleRepository.findByName(roleName)
                        .orElseThrow(() -> new ResourceNotFoundException("No existe el rol solicitado: " + roleName)))
                .toList();

        String nationalId = request.nationalId().trim();
        String firstName = request.firstName().trim();
        String lastName = request.lastName().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        userRepository.findByNationalId(nationalId)
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> {
                    throw new BusinessRuleViolationException(
                            "Ya existe una cuenta con esa identificación.");
                });
        userRepository.findByEmailIgnoreCase(email)
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> {
                    throw new BusinessRuleViolationException("Ya existe una cuenta con ese correo.");
                });

        List<RoleName> previousRoles = roles.stream()
                .filter(userRole -> (userRole.getLaboratory() != null
                                && laboratoryId.equals(userRole.getLaboratory().getId()))
                        || (userRole.getLaboratory() == null
                                && userRole.getRole().getName() == RoleName.ADMIN_GENERAL))
                .map(userRole -> userRole.getRole().getName())
                .distinct()
                .sorted()
                .toList();
        List<RoleName> normalizedRequestedRoles = requestedRoles.stream().sorted().toList();
        if (previousRoles.contains(RoleName.ADMIN_GENERAL) && !requestedRoles.contains(RoleName.ADMIN_GENERAL)) {
            globalAdministratorPolicy.requireAnotherActiveAdministrator(userId);
        }
        String previousValues = "cedula=" + user.getNationalId() + "; correo=" + user.getEmail()
                + "; roles=" + roles.stream()
                        .filter(userRole -> (userRole.getLaboratory() != null
                                        && laboratoryId.equals(userRole.getLaboratory().getId()))
                                || (userRole.getLaboratory() == null
                                        && userRole.getRole().getName() == RoleName.ADMIN_GENERAL))
                        .map(userRole -> userRole.getRole().getName())
                        .distinct()
                        .toList();
        boolean emailChanged = !email.equalsIgnoreCase(user.getEmail());
        user.setNationalId(nationalId);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        userRepository.save(user);

        List<UserRole> assignmentsInScope = roles.stream()
                .filter(userRole -> (userRole.getLaboratory() != null
                                && laboratoryId.equals(userRole.getLaboratory().getId()))
                        || (userRole.getLaboratory() == null
                                && userRole.getRole().getName() == RoleName.ADMIN_GENERAL))
                .toList();
        userRoleRepository.deleteAll(assignmentsInScope);
        userRoleRepository.flush();
        requestedRoleEntities.forEach(role -> userRoleRepository.save(UserRole.assign(
                user,
                role,
                role.getName() == RoleName.ADMIN_GENERAL ? null : laboratory)));
        auditLogRepository.save(AuditLog.record(
                administrator,
                "EDITAR_USUARIO",
                "TCWUSUARIOS",
                user.getId(),
                LocalDateTime.now(clock),
                previousValues + "; nueva_cedula=" + nationalId + "; nuevo_correo=" + email
                        + "; nuevos_roles=" + requestedRoles));
        if (!previousRoles.equals(normalizedRequestedRoles)) {
            auditLogRepository.save(AuditLog.record(
                    administrator,
                    "CAMBIAR_ROLES_USUARIO",
                    "TCWUSUARIOS",
                    user.getId(),
                    LocalDateTime.now(clock),
                    "Se cambiaron los roles de " + firstName + " " + lastName
                            + ". Roles anteriores: " + roleLabels(previousRoles)
                            + "; roles nuevos: " + roleLabels(normalizedRequestedRoles)
                            + "; laboratorio: " + laboratory.getName() + "."));
        }

        if (emailChanged || (previousRoles.contains(RoleName.ADMIN_GENERAL) && !requestedRoles.contains(RoleName.ADMIN_GENERAL))) {
            sessions.revokeAllAfterCommit(userId);
        }
        return ManagedUserResponse.from(user,
                visibleRoles(userId, requestedRoles, administratorIsGlobal));
    }

    private List<RoleName> visibleRoles(Long userId, List<RoleName> laboratoryRoles, boolean includeGlobalRole) {
        List<RoleName> visible = new java.util.ArrayList<>(laboratoryRoles);
        if (includeGlobalRole && userRoleRepository.findByUserId(userId).stream()
                .anyMatch(userRole -> userRole.getRole().getName() == RoleName.ADMIN_GENERAL
                        && userRole.getLaboratory() == null)) {
            visible.add(RoleName.ADMIN_GENERAL);
        }
        return visible.stream().distinct().toList();
    }

    private List<String> roleLabels(List<RoleName> roles) {
        return roles.stream().map(role -> switch (role) {
            case PRACTICANTE -> "Practicante";
            case TUTOR_ESPE -> "Tutor ESPE";
            case ADMIN_LABORATORIO -> "Administrador de laboratorio";
            case ADMIN_GENERAL -> "Administrador General";
        }).toList();
    }
}
