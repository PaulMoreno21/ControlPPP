package ec.edu.espe.practicas.service.holidays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import ec.edu.espe.practicas.model.*;
import ec.edu.espe.practicas.repository.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HolidayImportServiceTest {
    private final HolidayRepository holidays = mock(HolidayRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UserRoleRepository roles = mock(UserRoleRepository.class);
    private final AuditLogRepository audits = mock(AuditLogRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-17T02:00:00Z"), ZoneId.of("America/Guayaquil"));
    private final HolidayImportService service = new HolidayImportService(holidays, users, roles, audits, clock);
    private User administrator;
    private UserRole global;

    @BeforeEach void setup() {
        administrator = mock(User.class);
        when(administrator.getActive()).thenReturn(1);
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(administrator));
        global = mock(UserRole.class);
        var role = mock(Role.class);
        when(role.getName()).thenReturn(RoleName.ADMIN_GENERAL);
        when(global.getRole()).thenReturn(role);
        when(roles.findByUserId(1L)).thenReturn(List.of(global));
        when(holidays.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test void importsOnlyMissingDatesWithoutOverwritingInactiveOrManualRecords() {
        var old = Holiday.create(LocalDate.of(2026, 1, 1), "Nombre manual", administrator, LocalDateTime.now(clock));
        old.setActive(0);
        when(holidays.findByDateBetweenOrderByDateAsc(any(), any())).thenReturn(List.of(old));
        int count = service.importYear(2026, List.of(new NationalHoliday(old.getDate(), "Nombre externo"),
                new NationalHoliday(LocalDate.of(2026, 12, 25), "Navidad")), 1L);
        assertEquals(1, count);
        assertEquals(0, old.getActive()); assertEquals("Nombre manual", old.getDescription());
        var saved = ArgumentCaptor.forClass(Holiday.class);
        verify(holidays).save(saved.capture());
        assertEquals(administrator, saved.getValue().getRegisteredBy());
        assertEquals(LocalDate.of(2026, 12, 25), saved.getValue().getDate());
        var audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(audits).save(audit.capture());
        var completionAudit = audit.getValue();
        assertNull(completionAudit.getUser());
        assertTrue(completionAudit.getDetails().contains("Feriados nuevos registrados: 1."));
        assertEquals(HolidayImportService.COMPLETED_ACTION, completionAudit.getAction());
        assertEquals(2026L, completionAudit.getEntityId());
        assertEquals("Sistema", ec.edu.espe.practicas.dto.AuditLogResponse.from(completionAudit).actorName());
        var failedLogin = AuditLog.record(null, "LOGIN_LOCAL_FALLIDO", "TCWUSUARIOS", null,
                LocalDateTime.now(clock), "Acceso no autorizado.");
        assertEquals("Intento de acceso sin cuenta asociada",
                ec.edu.espe.practicas.dto.AuditLogResponse.from(failedLogin).actorName());
    }

    @Test void existingDatesAreKeptAndAnnualCompletionIsRecorded() {
        var date = LocalDate.of(2026, 1, 1);
        var existing = Holiday.create(date, "Año Nuevo", administrator, LocalDateTime.now(clock));
        when(holidays.findByDateBetweenOrderByDateAsc(any(), any())).thenReturn(List.of(existing));
        assertEquals(0, service.importYear(2026, List.of(new NationalHoliday(date, "Año Nuevo")), 1L));
        verify(holidays, never()).save(any());
        var marker = ArgumentCaptor.forClass(AuditLog.class);
        verify(audits).save(marker.capture());
        assertEquals(HolidayImportService.COMPLETED_ACTION, marker.getValue().getAction());
    }

    @Test void inactiveOrNonGlobalAdministratorCannotOwnAnImport() {
        when(administrator.getActive()).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> service.importYear(2026, List.of(), 1L));
        when(administrator.getActive()).thenReturn(1);
        when(roles.findByUserId(1L)).thenReturn(List.of());
        assertThrows(IllegalStateException.class, () -> service.importYear(2026, List.of(), 1L));
        when(roles.findByUserId(1L)).thenReturn(List.of(global));
        when(global.getLaboratory()).thenReturn(mock(Laboratory.class));
        assertThrows(IllegalStateException.class, () -> service.importYear(2026, List.of(), 1L));
        verifyNoInteractions(holidays);
        verify(audits, never()).save(any());
    }

    @Test void missingAdministratorOrWrongYearNeverWrites() {
        assertThrows(IllegalStateException.class, () -> service.importYear(2026, List.of(), 99L));
        assertThrows(IllegalArgumentException.class, () -> service.importYear(2026,
                List.of(new NationalHoliday(LocalDate.of(2027, 1, 1), "Año Nuevo")), 1L));
        verify(holidays, never()).save(any()); verify(audits, never()).save(any());
    }

    @Test void completedYearIsNotReimportedEvenAfterAnEditOrDeletion() {
        var marker = AuditLog.record(null, HolidayImportService.COMPLETED_ACTION,
                "CALENDARIO_FERIADOS", 2026L, LocalDateTime.now(clock), "Completado");
        when(audits.findFirstByActionAndEntityIdOrderByIdDesc(HolidayImportService.COMPLETED_ACTION, 2026L))
                .thenReturn(Optional.of(marker));
        assertEquals(0, service.importYear(2026,
                List.of(new NationalHoliday(LocalDate.of(2026, 12, 25), "No reimportar")), 1L));
        verifyNoInteractions(holidays);
        verify(audits, never()).save(any());
        assertEquals(Optional.of(marker.getOccurredAt()), service.completion(2026));
    }

    @Test void multipleDatesProduceExactlyOneSummaryEvent() {
        int count = service.importYear(2026, List.of(
                new NationalHoliday(LocalDate.of(2026, 1, 1), "Año Nuevo"),
                new NationalHoliday(LocalDate.of(2026, 12, 25), "Navidad")), 1L);
        assertEquals(2, count);
        verify(holidays, times(2)).save(any());
        var event = ArgumentCaptor.forClass(AuditLog.class);
        verify(audits).save(event.capture());
        assertEquals(HolidayImportService.COMPLETED_ACTION, event.getValue().getAction());
        assertTrue(event.getValue().getDetails().contains("Feriados nuevos registrados: 2."));
        assertEquals(2026L, event.getValue().getEntityId());
    }
}
