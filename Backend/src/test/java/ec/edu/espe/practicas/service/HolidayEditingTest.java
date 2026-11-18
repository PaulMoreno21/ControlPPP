package ec.edu.espe.practicas.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ec.edu.espe.practicas.controller.ApiExceptionHandler;
import ec.edu.espe.practicas.controller.HolidayAdministrationController;
import ec.edu.espe.practicas.dto.HolidayRequest;
import ec.edu.espe.practicas.model.*;
import ec.edu.espe.practicas.repository.*;
import ec.edu.espe.practicas.service.exception.BusinessRuleViolationException;
import ec.edu.espe.practicas.service.exception.ResourceNotFoundException;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HolidayEditingTest {
    private final HolidayRepository holidays = mock(HolidayRepository.class);
    private final AuditLogRepository audits = mock(AuditLogRepository.class);
    private final AuthenticatedUserService users = mock(AuthenticatedUserService.class);
    private final HolidayAdministrationService service = new HolidayAdministrationService(
            mock(HolidayQueryCache.class), users, holidays, audits,
            Clock.fixed(Instant.parse("2026-11-16T18:00:00Z"), ZoneId.of("America/Guayaquil")));
    private final LocalDate originalDate = LocalDate.of(2026, 12, 25);
    private final LocalDate newDate = LocalDate.of(2026, 12, 24);
    private final LocalDateTime registeredAt = LocalDateTime.of(2026, 1, 1, 12, 0);
    private User creator;
    private User editor;
    private Holiday existing;
    private MockMvc mvc;

    @BeforeEach void setup() {
        creator = User.create("1234567890", "Creador", "Original", "creator@invalid.example", "hash", AccountType.LOCAL);
        editor = User.create("0987654321", "Administrador", "Actual", "editor@invalid.example", "hash", AccountType.LOCAL);
        ReflectionTestUtils.setField(creator, "id", 1L);
        ReflectionTestUtils.setField(editor, "id", 2L);
        existing = Holiday.create(originalDate, "Descripción anterior", creator, registeredAt);
        ReflectionTestUtils.setField(existing, "id", 20L);
        existing.setActive(0);
        when(users.requireGlobalRole(any(), eq(RoleName.ADMIN_GENERAL))).thenReturn(editor);
        when(holidays.findById(20L)).thenReturn(Optional.of(existing));
        when(holidays.findByDate(originalDate)).thenReturn(Optional.of(existing));
        when(holidays.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        mvc = MockMvcBuilders.standaloneSetup(new HolidayAdministrationController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test void editsDateAndDescriptionWhileKeepingCreationMetadataAndInactiveState() {
        var result = service.updateHoliday(null, 20L, new HolidayRequest(newDate, "  Nombre corregido  "));
        assertEquals(newDate, result.date()); assertEquals("Nombre corregido", result.description());
        assertEquals(20L, result.id()); assertFalse(result.active());
        assertEquals(1L, result.registeredById()); assertEquals(registeredAt, result.registeredAt());
        var audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(audits).save(audit.capture());
        assertEquals(editor, audit.getValue().getUser());
        assertEquals("EDITAR_FERIADO", audit.getValue().getAction());
        assertTrue(audit.getValue().getDetails().contains("Descripción anterior"));
        assertTrue(audit.getValue().getDetails().contains("24/12/2026"));
        assertTrue(audit.getValue().getDetails().contains("25/12/2026"));
    }

    @Test void keepsSameDateWhenChangingOnlyDescription() {
        var result = service.updateHoliday(null, 20L, new HolidayRequest(originalDate, "Nombre nuevo"));
        assertEquals(originalDate, result.date()); assertEquals("Nombre nuevo", result.description());
        verify(holidays).save(existing);
    }

    @Test void duplicateDateDoesNotModifyExistingRecord() {
        var other = Holiday.create(newDate, "Otro", creator, registeredAt);
        ReflectionTestUtils.setField(other, "id", 21L);
        when(holidays.findByDate(newDate)).thenReturn(Optional.of(other));
        assertThrows(BusinessRuleViolationException.class,
                () -> service.updateHoliday(null, 20L, new HolidayRequest(newDate, "No guardar")));
        assertEquals(originalDate, existing.getDate());
        assertEquals("Descripción anterior", existing.getDescription());
        verify(holidays, never()).save(any()); verifyNoInteractions(audits);
    }

    @Test void unchangedValuesDoNotCreateAuditOrSaveAgain() {
        service.updateHoliday(null, 20L, new HolidayRequest(originalDate, "Descripción anterior"));
        verify(holidays, never()).save(any()); verifyNoInteractions(audits);
    }

    @Test void requiresGlobalAdministratorAndExistingHoliday() {
        assertThrows(ResourceNotFoundException.class,
                () -> service.updateHoliday(null, 99L, new HolidayRequest(newDate, "Nombre")));
        when(users.requireGlobalRole(any(), eq(RoleName.ADMIN_GENERAL))).thenThrow(new AccessDeniedException("Sin permiso"));
        clearInvocations(holidays);
        assertThrows(AccessDeniedException.class,
                () -> service.updateHoliday(null, 20L, new HolidayRequest(newDate, "Nombre")));
        verifyNoInteractions(holidays, audits);
    }

    @Test void httpPutReturnsTheUpdatedHoliday() throws Exception {
        mvc.perform(put("/api/v1/admin/holidays/20").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-12-24\",\"description\":\"Nombre corregido\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.date").value("2026-12-24"))
                .andExpect(jsonPath("$.description").value("Nombre corregido"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @ParameterizedTest @ValueSource(strings = {
            "{\"date\":null,\"description\":\"Nombre\"}",
            "{\"date\":\"2026-12-24\",\"description\":\"   \"}",
            "{\"date\":\"24/12/2026\",\"description\":\"Nombre\"}"
    })
    void httpPutRejectsInvalidFieldsBeforeSaving(String json) throws Exception {
        mvc.perform(put("/api/v1/admin/holidays/20").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
        verify(holidays, never()).save(any()); verifyNoInteractions(audits);
    }

    @Test void httpPutRejectsAnOversizedDescriptionAndDeniedRole() throws Exception {
        mvc.perform(put("/api/v1/admin/holidays/20").contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2026-12-24\",\"description\":\"" + "x".repeat(151) + "\"}"))
                .andExpect(status().isBadRequest());
        when(users.requireGlobalRole(any(), eq(RoleName.ADMIN_GENERAL))).thenThrow(new AccessDeniedException("Sin permiso"));
        mvc.perform(put("/api/v1/admin/holidays/20").contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2026-12-24\",\"description\":\"Nombre\"}"))
                .andExpect(status().isForbidden());
        verify(holidays, never()).save(any()); verifyNoInteractions(audits);
    }
}
