package ec.edu.espe.practicas.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ec.edu.espe.practicas.dto.*;
import ec.edu.espe.practicas.model.*;
import ec.edu.espe.practicas.repository.*;
import ec.edu.espe.practicas.security.ActiveSessionRegistry;
import ec.edu.espe.practicas.service.exception.*;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

class GeneralAdministratorServiceTest {
    private final Authentication auth = mock(Authentication.class);
    private final AuthenticatedUserService authenticated = mock(AuthenticatedUserService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UserRoleRepository roles = mock(UserRoleRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final AuditLogRepository audit = mock(AuditLogRepository.class);
    private final ActiveSessionRegistry sessions = new ActiveSessionRegistry(Clock.systemUTC(), 900, 100);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final GlobalAdministratorPolicy policy = new GlobalAdministratorPolicy(roleRepository, roles);
    private final GeneralAdministratorService service = new GeneralAdministratorService(
            authenticated, users, roles, audit, policy, sessions, Clock.systemUTC(), entityManager);
    private User actor;
    private User target;
    private Role globalRole;

    private User user(Long id, String email) {
        User user = User.create("1234567890", "Nombre", "Apellido", email, "original-hash", AccountType.LOCAL);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Role role(RoleName name) {
        Role result = mock(Role.class);
        when(result.getName()).thenReturn(name);
        return result;
    }

    @BeforeEach void setup() {
        actor = user(1L, "actor@example.com");
        target = user(2L, "target@example.com");
        globalRole = role(RoleName.ADMIN_GENERAL);
        when(authenticated.requireGlobalRole(auth, RoleName.ADMIN_GENERAL)).thenReturn(actor);
        when(users.findByIdForUpdate(2L)).thenReturn(Optional.of(target));
        when(roles.findByUserId(2L)).thenReturn(List.of(UserRole.assign(target, globalRole, null)));
        when(roleRepository.findByNameForUpdate(RoleName.ADMIN_GENERAL)).thenReturn(Optional.of(globalRole));
        when(roles.countOtherActiveGlobalAdministrators(RoleName.ADMIN_GENERAL, 2L)).thenReturn(1L);
    }

    @Test void listsGlobalOnlyAccountsWithoutLaboratoryAndWithoutDuplicates() {
        when(users.findGlobalAdministrators(eq(RoleName.ADMIN_GENERAL), eq("%123%"), any()))
                .thenReturn(new PageImpl<>(List.of(target), PageRequest.of(0, 15), 1));
        when(roles.findByUserIdIn(List.of(2L))).thenReturn(List.of(UserRole.assign(target, globalRole, null)));
        var result = service.list(auth, "123", 0, 15);
        assertEquals(1, result.totalElements());
        assertEquals(List.of(RoleName.ADMIN_GENERAL), result.content().getFirst().roles());
        verify(users).findGlobalAdministrators(eq(RoleName.ADMIN_GENERAL), eq("%123%"), argThat(page ->
                page.getPageSize() == 15 && page.getSort().getOrderFor("id") != null));
    }

    @Test void rejectsLaboratoryAdministratorBeforeReadingGlobalAccounts() {
        when(authenticated.requireGlobalRole(auth, RoleName.ADMIN_GENERAL)).thenThrow(new AccessDeniedException("denied"));
        assertThrows(AccessDeniedException.class, () -> service.list(auth, null, 0, 15));
        assertThrows(AccessDeniedException.class, () -> service.update(auth, 2L,
                new UpdateGeneralAdministratorRequest("1234567890", "Nombre", "Apellido", "target@example.com")));
        assertThrows(AccessDeniedException.class, () -> service.setActive(auth, 2L, false));
        verifyNoInteractions(users, audit);
    }

    @Test void editingPreservesHashAndLaboratoryRoles() {
        var lab = Laboratory.create("Laboratorio", null);
        var assigned = UserRole.assign(target, role(RoleName.TUTOR_ESPE), lab);
        when(roles.findByUserId(2L)).thenReturn(List.of(UserRole.assign(target, globalRole, null), assigned));
        var result = service.update(auth, 2L,
                new UpdateGeneralAdministratorRequest(" AB123 ", " María ", " Pérez ", "TARGET@example.com"));
        assertEquals("María", target.getFirstName());
        assertEquals("target@example.com", target.getEmail());
        assertEquals("original-hash", target.getPasswordHash());
        assertTrue(result.roles().containsAll(List.of(RoleName.ADMIN_GENERAL, RoleName.TUTOR_ESPE)));
        verify(roles, never()).deleteAll(anyIterable());
        verify(roles, never()).save(any());
        verify(entityManager).refresh(target);
        verify(audit).save(any());
    }

    @Test void duplicateEmailDoesNotSave() {
        when(users.findByEmailIgnoreCase("used@example.com")).thenReturn(Optional.of(actor));
        assertThrows(BusinessRuleViolationException.class, () -> service.update(auth, 2L,
                new UpdateGeneralAdministratorRequest("123", "Nombre", "Apellido", "used@example.com")));
        verify(users, never()).save(any());
        verifyNoInteractions(audit);
    }

    @Test void cannotChangeOwnStatusOrDeactivateLastActiveAdministrator() {
        assertThrows(BusinessRuleViolationException.class, () -> service.setActive(auth, 1L, false));
        when(roles.countOtherActiveGlobalAdministrators(RoleName.ADMIN_GENERAL, 2L)).thenReturn(0L);
        assertThrows(BusinessRuleViolationException.class, () -> service.setActive(auth, 2L, false));
        verify(roleRepository).findByNameForUpdate(RoleName.ADMIN_GENERAL);
        verify(users, never()).save(any());
    }

    @Test void deactivatePreservesAccountAndHistoryButRevokesTargetSessions() {
        sessions.register("target", 2L, Instant.now().plusSeconds(900));
        sessions.register("actor", 1L, Instant.now().plusSeconds(900));
        var result = service.setActive(auth, 2L, false);
        assertEquals(0, result.active());
        assertFalse(sessions.isActive("target"));
        assertTrue(sessions.isActive("actor"));
        verify(users, never()).delete(any());
        verify(roles, never()).deleteAll(anyIterable());
        verify(audit).save(any());
    }

    @Test void globalRoutesRejectTargetWithoutGlobalRole() {
        when(roles.findByUserId(2L)).thenReturn(List.of());
        assertThrows(ResourceNotFoundException.class, () -> service.update(auth, 2L,
                new UpdateGeneralAdministratorRequest("123", "Nombre", "Apellido", "target@example.com")));
        verify(users, never()).save(any());
    }

    @Test void laboratoryRoutesCannotBypassLastAdministratorProtection() {
        var labs = mock(LaboratoryRepository.class);
        var lab = Laboratory.create("Laboratorio", null);
        ReflectionTestUtils.setField(lab, "id", 7L);
        var tutorRole = role(RoleName.TUTOR_ESPE);
        when(labs.findById(7L)).thenReturn(Optional.of(lab));
        when(authenticated.requireAdministratorForLaboratory(auth, 7L)).thenReturn(actor);
        when(authenticated.hasGlobalRole(actor, RoleName.ADMIN_GENERAL)).thenReturn(true);
        when(users.findById(2L)).thenReturn(Optional.of(target));
        when(roles.findByUserId(1L)).thenReturn(List.of(UserRole.assign(actor, globalRole, null)));
        when(roles.findByUserId(2L)).thenReturn(List.of(UserRole.assign(target, globalRole, null), UserRole.assign(target, tutorRole, lab)));
        when(roleRepository.findByName(RoleName.TUTOR_ESPE)).thenReturn(Optional.of(tutorRole));
        when(roles.countOtherActiveGlobalAdministrators(RoleName.ADMIN_GENERAL, 2L)).thenReturn(0L);
        var legacy = new UserAdministrationService(authenticated, users, roles, roleRepository, labs, audit,
                mock(AssignmentRepository.class), mock(org.springframework.security.crypto.password.PasswordEncoder.class),
                Clock.systemUTC(), policy, sessions);
        assertThrows(BusinessRuleViolationException.class, () -> legacy.updateUserStatus(auth, 7L, 2L, false));
        assertThrows(BusinessRuleViolationException.class, () -> legacy.updateUser(auth, 7L, 2L,
                new UpdateManagedUserRequest("1234567890", "Nombre", "Apellido", "target@example.com", List.of(RoleName.TUTOR_ESPE))));
        verify(users, never()).save(any());
        verify(roles, never()).deleteAll(anyIterable());
    }
}
