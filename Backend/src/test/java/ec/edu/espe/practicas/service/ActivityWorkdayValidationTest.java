package ec.edu.espe.practicas.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ec.edu.espe.practicas.controller.ActivityController;
import ec.edu.espe.practicas.controller.ApiExceptionHandler;
import ec.edu.espe.practicas.model.*;
import ec.edu.espe.practicas.repository.*;
import ec.edu.espe.practicas.service.exception.BusinessRuleViolationException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ActivityWorkdayValidationTest {
    private final ActivityRepository activities = mock(ActivityRepository.class);
    private final AuditLogRepository audits = mock(AuditLogRepository.class);
    private final HolidayRepository holidays = mock(HolidayRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ActivityService service = new ActivityService(activities, audits,
            mock(ActivitySubmissionRepository.class), mock(ActivityReviewRepository.class),
            mock(AssignmentRepository.class), holidays, users,
            Clock.fixed(Instant.parse("2026-11-04T17:00:00Z"), ZoneId.of("America/Guayaquil")));
    private final LocalDate originalDate = LocalDate.of(2026, 10, 26);
    private final LocalDate holiday = LocalDate.of(2026, 10, 9);
    private User trainee;
    private Activity existing;

    @BeforeEach void setup() {
        trainee = User.create("1234567890", "Ana", "Pérez", "test@example.com", "hash", AccountType.LOCAL);
        ReflectionTestUtils.setField(trainee, "id", 3L);
        existing = Activity.draft(trainee, originalDate, "Actividad anterior", BigDecimal.ONE);
        ReflectionTestUtils.setField(existing, "id", 10L);
        when(users.findById(3L)).thenReturn(Optional.of(trainee));
        when(activities.findById(10L)).thenReturn(Optional.of(existing));
        when(activities.save(any(Activity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(holidays.existsByDateAndActive(holiday, 1)).thenReturn(true);
    }

    static Stream<Arguments> blockedDates() {
        return Stream.concat(Stream.of("individual", "lote", "borrador", "rechazada").flatMap(operation -> Stream.of(
                Arguments.of(operation, LocalDate.of(2026, 10, 31), "fines de semana"),
                Arguments.of(operation, LocalDate.of(2026, 11, 1), "fines de semana"),
                Arguments.of(operation, LocalDate.of(2026, 10, 9), "feriado"))),
                Stream.of("individual", "lote").map(operation ->
                        Arguments.of(operation, LocalDate.of(2026, 11, 5), "fechas futuras")));
    }

    private void save(String operation, LocalDate date, BigDecimal hours) {
        switch (operation) {
            case "individual" -> service.createDraft(3L, date, "Actividad nueva", hours);
            case "lote" -> service.createDrafts(3L, date, List.of(
                    new ActivityDraftInput("Actividad nueva", hours),
                    new ActivityDraftInput("Segunda actividad", BigDecimal.ONE)));
            case "rechazada" -> {
                existing.setStatus(ActivityStatus.RECHAZADA);
                service.updateDraft(3L, 10L, date, "Actividad corregida", hours);
            }
            default -> service.updateDraft(3L, 10L, date, "Actividad corregida", hours);
        }
    }

    @ParameterizedTest @MethodSource("blockedDates")
    void rejectsSaturdaySundayAndActiveHolidayBeforeAnyWrite(String operation, LocalDate date, String reason) {
        var error = assertThrows(BusinessRuleViolationException.class,
                () -> save(operation, date, BigDecimal.ONE));
        assertTrue(error.getMessage().contains(reason));
        verify(activities, never()).save(any());
        verifyNoInteractions(audits);
        assertEquals(originalDate, existing.getDate());
        assertEquals("Actividad anterior", existing.getDescription());
    }

    @ParameterizedTest @ValueSource(strings = {"individual", "lote", "borrador", "rechazada"})
    void acceptsSixHoursOnWeekdayWithoutAttendance(String operation) {
        // A batch contains one additional hour, so the first activity uses five.
        save(operation, originalDate, BigDecimal.valueOf("lote".equals(operation) ? 5 : 6));
        verify(holidays, atLeastOnce()).existsByDateAndActive(originalDate, 1);
        verify(activities, times("lote".equals(operation) ? 2 : 1)).save(any());
        verify(audits, times("lote".equals(operation) ? 2 : 1)).save(any());
    }

    @ParameterizedTest @ValueSource(strings = {"individual", "lote"})
    void acceptsCreationTodayWithoutAttendance(String operation) {
        save(operation, LocalDate.of(2026, 11, 4), BigDecimal.ONE);
        verify(activities, times("lote".equals(operation) ? 2 : 1)).save(any());
    }

    @ParameterizedTest @ValueSource(strings = {"borrador", "rechazada"})
    void creationRestrictionDoesNotChangeExistingEditPermissions(String operation) {
        LocalDate future = LocalDate.of(2026, 11, 5);
        save(operation, future, BigDecimal.ONE);
        assertEquals(future, existing.getDate());
        verify(activities).save(existing);
    }

    @Test void creationUsesServerLocalDateRatherThanUtcDate() {
        // UTC is already November 5; Ecuador is still November 4.
        var localService = new ActivityService(activities, audits,
                mock(ActivitySubmissionRepository.class), mock(ActivityReviewRepository.class),
                mock(AssignmentRepository.class), holidays, users,
                Clock.fixed(Instant.parse("2026-11-05T02:00:00Z"), ZoneId.of("America/Guayaquil")));
        var error = assertThrows(BusinessRuleViolationException.class, () -> localService.createDraft(
                3L, LocalDate.of(2026, 11, 5), "Actividad futura", BigDecimal.ONE));
        assertEquals("No se pueden crear actividades en fechas futuras.", error.getMessage());
        verify(activities, never()).save(any());
        verifyNoInteractions(audits);
    }

    @ParameterizedTest @ValueSource(strings = {"individual", "lote"})
    void weekdayStillRejectsMoreThanSixHours(String operation) {
        var error = assertThrows(BusinessRuleViolationException.class,
                () -> save(operation, originalDate, BigDecimal.valueOf(7)));
        assertTrue(error.getMessage().contains("seis horas"));
        verify(activities, never()).save(any());
        verifyNoInteractions(audits);
    }

    @ParameterizedTest
    @CsvSource({"individual,2026-11-01", "lote,2026-11-01", "borrador,2026-11-01",
            "individual,2026-10-09", "lote,2026-10-09", "borrador,2026-10-09",
            "individual,2026-11-05", "lote,2026-11-05"})
    void directApiRequestAlsoRejectsNonWorkingDate(String operation, LocalDate date) throws Exception {
        var authenticated = mock(AuthenticatedUserService.class);
        when(authenticated.requireRole(any(), eq(RoleName.PRACTICANTE))).thenReturn(trainee);
        var mvc = MockMvcBuilders.standaloneSetup(new ActivityController(service, authenticated))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        String body = "lote".equals(operation)
                ? "{\"date\":\"" + date + "\",\"activities\":[{\"description\":\"Actividad\",\"durationHours\":1}]}"
                : "{\"date\":\"" + date + "\",\"description\":\"Actividad\",\"durationHours\":1}";
        String path = "/api/v1/activities" + ("lote".equals(operation) ? "/batch"
                : "borrador".equals(operation) ? "/10" : "");
        var request = "borrador".equals(operation) ? put(path) : post(path);
        mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(date.isAfter(LocalDate.of(2026, 11, 4))
                        ? "No se pueden crear actividades en fechas futuras."
                        : date.equals(holiday)
                        ? "No se pueden registrar actividades en un día feriado."
                        : "No se pueden registrar actividades en fines de semana."));
        verify(activities, never()).save(any());
        verifyNoInteractions(audits);
    }
}
