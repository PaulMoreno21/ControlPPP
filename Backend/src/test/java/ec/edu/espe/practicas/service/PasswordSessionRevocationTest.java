package ec.edu.espe.practicas.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ec.edu.espe.practicas.dto.ChangePasswordRequest;
import ec.edu.espe.practicas.dto.ResetLocalPasswordRequest;
import ec.edu.espe.practicas.model.*;
import ec.edu.espe.practicas.repository.*;
import ec.edu.espe.practicas.security.ActiveSessionRegistry;
import ec.edu.espe.practicas.service.exception.BusinessRuleViolationException;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PasswordSessionRevocationTest {
    private final Authentication auth = mock(Authentication.class);
    private final AuthenticatedUserService authenticated = mock(AuthenticatedUserService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UserRoleRepository roles = mock(UserRoleRepository.class);
    private final AuditLogRepository audit = mock(AuditLogRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final ActiveSessionRegistry sessions = new ActiveSessionRegistry(Clock.systemUTC(), 900, 100);
    private final PasswordManagementService service = new PasswordManagementService(
            authenticated, users, roles, audit, encoder, Clock.systemUTC(), sessions, entityManager);
    private User target;

    @BeforeEach void setup() {
        target = user(1L);
        when(authenticated.requireAuthenticatedUserForPasswordChange(auth)).thenReturn(target);
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(target));
        when(encoder.matches("current-password", "old-hash")).thenReturn(true);
        when(encoder.encode("New-password-123")).thenReturn("new-hash");
        sessions.register("first", 1L, Instant.now().plusSeconds(900));
        sessions.register("second", 1L, Instant.now().plusSeconds(900));
        sessions.register("other", 2L, Instant.now().plusSeconds(900));
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach void cleanup() { TransactionSynchronizationManager.clearSynchronization(); }

    private User user(Long id) {
        var user = User.create("1234567890", "Persona", "Prueba", "person" + id + "@example.com", "old-hash", AccountType.LOCAL);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private void commit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    @Test void ownChangeRevokesEverySessionOnlyAfterCommit() {
        service.changeOwnPassword(auth, new ChangePasswordRequest("current-password", "New-password-123"));
        verify(users).findByIdForUpdate(1L);
        verify(entityManager).refresh(target);
        assertTrue(sessions.isActive("first"));
        commit();
        assertFalse(sessions.isActive("first"));
        assertFalse(sessions.isActive("second"));
        assertTrue(sessions.isActive("other"));
        assertFalse(target.isPasswordChangeRequired());
    }

    @Test void wrongPasswordDoesNotRevokeOrSave() {
        assertThrows(BusinessRuleViolationException.class, () -> service.changeOwnPassword(auth,
                new ChangePasswordRequest("wrong-password", "New-password-123")));
        commit();
        assertTrue(sessions.isActive("first"));
        assertTrue(sessions.isActive("second"));
        verify(users, never()).save(any());
        verifyNoInteractions(audit);
    }

    @Test void rollbackKeepsExistingSessions() {
        service.changeOwnPassword(auth, new ChangePasswordRequest("current-password", "New-password-123"));
        TransactionSynchronizationManager.getSynchronizations().forEach(
                callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertTrue(sessions.isActive("first"));
        assertTrue(sessions.isActive("second"));
    }

    @Test void auditFailureDoesNotScheduleRevocation() {
        when(audit.save(any())).thenThrow(new IllegalStateException("simulated persistence failure"));
        assertThrows(IllegalStateException.class, () -> service.changeOwnPassword(auth,
                new ChangePasswordRequest("current-password", "New-password-123")));
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
        assertTrue(sessions.isActive("first"));
    }

    @Test void administratorResetRevokesOnlyTargetAndKeepsMandatoryPasswordChange() {
        User administrator = user(9L);
        sessions.register("administrator", 9L, Instant.now().plusSeconds(900));
        when(authenticated.requireAdministratorForLaboratory(auth, 7L)).thenReturn(administrator);
        var laboratory = mock(Laboratory.class);
        when(laboratory.getId()).thenReturn(7L);
        var role = mock(Role.class);
        when(role.getName()).thenReturn(RoleName.PRACTICANTE);
        when(roles.findByUserId(1L)).thenReturn(List.of(UserRole.assign(target, role, laboratory)));
        service.resetLocalPassword(auth, 7L, 1L, new ResetLocalPasswordRequest("New-password-123"));
        assertTrue(sessions.isActive("first"));
        commit();
        assertFalse(sessions.isActive("first"));
        assertFalse(sessions.isActive("second"));
        assertTrue(sessions.isActive("administrator"));
        assertTrue(sessions.isActive("other"));
        assertTrue(target.isPasswordChangeRequired());
    }

    @Test void resetsGlobalAdministratorWithoutLaboratory() {
        var administrator = user(9L);
        when(authenticated.requireGlobalRole(auth, RoleName.ADMIN_GENERAL)).thenReturn(administrator);
        var globalRole = mock(Role.class);
        when(globalRole.getName()).thenReturn(RoleName.ADMIN_GENERAL);
        when(roles.findByUserId(1L)).thenReturn(List.of(UserRole.assign(target, globalRole, null)));
        service.resetGeneralAdministratorPassword(auth, 1L, new ResetLocalPasswordRequest("New-password-123"));
        assertTrue(sessions.isActive("first"));
        commit();
        assertFalse(sessions.isActive("first"));
        assertTrue(sessions.isActive("other"));
        assertTrue(target.isPasswordChangeRequired());
    }

    @Test void globalPasswordResetRejectsSelfAndNonGlobalAccounts() {
        when(authenticated.requireGlobalRole(auth, RoleName.ADMIN_GENERAL)).thenReturn(user(9L));
        assertThrows(BusinessRuleViolationException.class, () -> service.resetGeneralAdministratorPassword(
                auth, 9L, new ResetLocalPasswordRequest("New-password-123")));
        when(roles.findByUserId(1L)).thenReturn(List.of());
        assertThrows(ec.edu.espe.practicas.service.exception.ResourceNotFoundException.class, () ->
                service.resetGeneralAdministratorPassword(auth, 1L, new ResetLocalPasswordRequest("New-password-123")));
        assertTrue(sessions.isActive("first"));
        verify(users, never()).save(any());
    }
}
