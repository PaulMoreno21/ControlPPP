package ec.edu.espe.practicas.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ec.edu.espe.practicas.model.*;
import ec.edu.espe.practicas.repository.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

class AuditDetailsFormatterTest {
    private final UserRepository users = mock(UserRepository.class);
    private final LaboratoryRepository laboratories = mock(LaboratoryRepository.class);
    private final AuditDetailsFormatter formatter = new AuditDetailsFormatter(users, laboratories);
    private final LocalDateTime occurredAt = LocalDateTime.of(2026, 10, 30, 18, 46);
    private final User trainee = user(3L, "Ana", "Pérez");

    private User user(Long id, String firstName, String lastName) {
        User user = User.create("1234567890", firstName, lastName, "test@example.com", "hash", AccountType.LOCAL);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private AuditLog log(String action, String entity, String details) {
        return AuditLog.record(trainee, action, entity, 3L, occurredAt, details);
    }

    @Test void oldClosureHasReadableDateTimesAndDoesNotChangeStoredRecord() {
        String original = "fecha=2026-10-30; entrada=2026-10-30T12:46:58.476414; "
                + "salida=2026-10-30T18:46:58.476414; motivo=limite_6_horas";
        AuditLog log = log("CIERRE_AUTOMATICO_ASISTENCIA", "TCWASISTENCIAS", original);
        String result = formatter.formatPage(List.of(log)).get(log);
        assertTrue(result.contains("Ana Pérez del 30/10/2026"));
        assertTrue(result.contains("6 horas"));
        assertTrue(result.contains("Entrada: 12:46; salida: 18:46"));
        assertFalse(result.contains("="));
        assertEquals(original, log.getDetails());
        verifyNoInteractions(users, laboratories);
    }

    @ParameterizedTest @ValueSource(strings = {"PDF", "XLSX", "DOCX", "CSV"})
    void oldExportDisplaysRangeAndFormat(String format) {
        AuditLog log = log("EXPORTAR_BITACORA_" + format, "BITACORA",
                "desde=2026-10-01; hasta=2026-10-30; formato=" + format);
        String result = formatter.formatPage(List.of(log)).get(log);
        assertTrue(result.contains("Ana Pérez en " + format));
        assertTrue(result.contains("del 01/10/2026 al 30/10/2026"));
        assertFalse(result.contains("="));
    }

    @Test void assignmentNamesAreLoadedInBatchesForWholePageIncludingUnlinkAndReassignment() {
        User tutor = user(6L, "Luis", "Vera");
        Laboratory lab = Laboratory.create("Redes", "Bloque A");
        ReflectionTestUtils.setField(lab, "id", 1L);
        when(users.findAllById(any())).thenReturn(List.of(trainee, tutor));
        when(laboratories.findAllById(any())).thenReturn(List.of(lab));
        List<AuditLog> logs = List.of(
                log("ASIGNAR_TUTOR", "TCWASIGNACIONES", "practicante=3; tutor=6; laboratorio=1"),
                log("REASIGNAR_TUTOR", "TCWASIGNACIONES", "practicante=3; tutor=6; laboratorio=1"),
                log("DESVINCULAR_TUTOR", "TCWASIGNACIONES", "practicante=3; tutor=6; laboratorio=1"));
        Map<AuditLog, String> results = formatter.formatPage(logs);
        for (String detail : results.values()) {
            assertTrue(detail.contains("Ana Pérez"));
            assertTrue(detail.contains("Luis Vera"));
            assertTrue(detail.contains("«Redes»"));
            assertFalse(detail.contains("="));
        }
        assertTrue(results.get(logs.get(2)).startsWith("Se desvinculó"));
        verify(users, times(1)).findAllById(eq(Set.of(3L, 6L)));
        verify(laboratories, times(1)).findAllById(eq(Set.of(1L)));
        verifyNoMoreInteractions(users, laboratories);
    }

    @Test void missingOrInvalidReferencesDoNotExposeIdsOrFailPage() {
        AuditLog log = log("DESVINCULAR_TUTOR", "TCWASIGNACIONES",
                "practicante=no_disponible; tutor=9999999999999999999999999");
        String result = formatter.formatPage(List.of(log)).get(log);
        assertEquals("Se desvinculó al practicante registrado del tutor registrado.", result);
        verifyNoInteractions(users, laboratories);
    }

    @Test void invalidDateDoesNotBreakOtherEventsOnPage() {
        AuditLog invalid = log("EXPORTAR_BITACORA_PDF", "BITACORA", "desde=no_valida; hasta=2026-10-30");
        AuditLog readable = log("REGISTRAR_ENTRADA", "TCWASISTENCIAS", "Se registró la entrada de Ana Pérez.");
        var result = formatter.formatPage(List.of(invalid, readable));
        assertTrue(result.get(invalid).contains("información suficiente"));
        assertEquals(readable.getDetails(), result.get(readable));
        verifyNoInteractions(users, laboratories);
    }

    @Test void holidayAndDeletedLaboratoryUseRecordedDescriptions() {
        AuditLog holiday = log("ELIMINAR_FERIADO", "TCWFERIADOS", "fecha=2026-10-09; descripcion=Independencia");
        AuditLog laboratory = log("ELIMINAR_LABORATORIO", "TCWLABORATORIOS", "nombre=Redes; ubicacion=Bloque A");
        var result = formatter.formatPage(List.of(holiday, laboratory));
        assertEquals("Se eliminó el feriado «Independencia» del 09/10/2026.", result.get(holiday));
        assertEquals("Se eliminó el laboratorio «Redes», ubicado en «Bloque A».", result.get(laboratory));
        verifyNoInteractions(users, laboratories);
    }

    @Test void oldUserCreationPreservesRecordedNameAndReadableRoles() {
        AuditLog log = log("CREAR_USUARIO", "TCWUSUARIOS",
                "Usuario creado=Ana Pérez; Roles=[PRACTICANTE]; laboratorio=1");
        String result = formatter.formatPage(List.of(log)).get(log);
        assertTrue(result.contains("Ana Pérez con los roles: Practicante"));
        assertFalse(result.contains("PRACTICANTE"));
        assertFalse(result.contains("laboratorio=1"));
    }

    @Test void semicolonsAndEqualsWithinDescriptionsRemainVisible() {
        String name = "Redes; 24 equipos; señal: estable=si";
        String location = "Bloque A; piso 2; acceso: puerta=principal\nHorario extendido";
        AuditLog log = log("ELIMINAR_LABORATORIO", "TCWLABORATORIOS",
                "nombre=" + name + "; ubicacion=" + location);

        assertEquals("Se eliminó el laboratorio «" + name + "», ubicado en «" + location + "».",
                formatter.formatPage(List.of(log)).get(log));
        verifyNoInteractions(users, laboratories);
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "\t", "\r\n", "\u000B", "\f", " \t\r\n\f "})
    void legacyWhitespaceBetweenFieldsPreservesHumanMessage(String whitespace) {
        AuditLog log = log("ELIMINAR_LABORATORIO", "TCWLABORATORIOS",
                " nombre = Redes ;" + whitespace + "ubicacion = Bloque A ");

        assertEquals("Se eliminó el laboratorio «Redes», ubicado en «Bloque A».",
                formatter.formatPage(List.of(log)).get(log));
        verifyNoInteractions(users, laboratories);
    }

    @Test void unicodeFieldNamesAreRecognizedWithoutAppearingInTheMessage() {
        AuditLog log = log("ELIMINAR_LABORATORIO", "TCWLABORATORIOS",
                "nombre=Redes; observación=Texto; \uD801\uDC00=Otro texto; ubicacion=Bloque A");

        assertEquals("Se eliminó el laboratorio «Redes», ubicado en «Bloque A».",
                formatter.formatPage(List.of(log)).get(log));
    }

    @Test void emptyAndRepeatedFieldsKeepLegacyMeaning() {
        AuditLog log = log("ELIMINAR_LABORATORIO", "TCWLABORATORIOS",
                "nombre=Anterior; nombre=Redes; ubicacion=Anterior; ubicacion=;  =ignorado");

        assertEquals("Se eliminó el laboratorio «Redes».", formatter.formatPage(List.of(log)).get(log));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Se registró la entrada de Ana Pérez.", "\tnombre=Redes", "nombre1=Redes", "=Redes"})
    void messagesWithoutAnInitialLegacyFieldAreUnchanged(String details) {
        AuditLog log = log("ELIMINAR_LABORATORIO", "TCWLABORATORIOS", details);

        assertEquals(details, formatter.formatPage(List.of(log)).get(log));
        verifyNoInteractions(users, laboratories);
    }

    @Test void longDescriptionsWithManyInvalidSeparatorsDoNotPreventPageFormatting() {
        String name = "Redes;" + " ".repeat(20_000) + "sin igualdad!"
                + (";" + " ".repeat(512) + "sin igualdad!").repeat(1_000);
        AuditLog log = log("ELIMINAR_LABORATORIO", "TCWLABORATORIOS", "nombre=" + name + "; ubicacion=Bloque A");

        assertTimeout(Duration.ofSeconds(5), () -> assertEquals(
                "Se eliminó el laboratorio «" + name + "», ubicado en «Bloque A».",
                formatter.formatPage(List.of(log)).get(log)));
        assertEquals("nombre=" + name + "; ubicacion=Bloque A", log.getDetails());
        verifyNoInteractions(users, laboratories);
    }

    @Test void queryKeepsPaginationAndReturnsReadableDetailsWithoutWriting() {
        var auth = mock(Authentication.class);
        var authenticated = mock(AuthenticatedUserService.class);
        var audits = mock(AuditLogRepository.class);
        var activities = mock(ActivityRepository.class);
        var log = log("EXPORTAR_BITACORA_PDF", "BITACORA", "desde=2026-10-01; hasta=2026-10-30; formato=PDF");
        when(audits.findByOccurredAtGreaterThanEqualAndOccurredAtLessThan(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(log), PageRequest.of(1, 15), 31));
        var service = new AuditQueryService(authenticated, audits, activities, formatter);
        var result = service.listPage(auth, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30), 1, 15);
        assertEquals(1, result.page());
        assertEquals(31, result.totalElements());
        assertTrue(result.content().getFirst().details().contains("del 01/10/2026 al 30/10/2026"));
        verify(authenticated).requireGlobalRole(auth, RoleName.ADMIN_GENERAL);
        verify(audits, never()).save(any());
    }

    @Test void newAutomaticClosureStoresHumanMessageAndKeepsSixHourRule() {
        var attendances = mock(AttendanceRepository.class);
        var audits = mock(AuditLogRepository.class);
        var holidays = mock(HolidayRepository.class);
        var clock = Clock.fixed(Instant.parse("2026-10-31T00:00:00Z"), ZoneId.of("America/Guayaquil"));
        LocalDateTime entry = LocalDateTime.of(2026, 10, 30, 12, 46, 58);
        Attendance attendance = Attendance.start(trainee, entry.toLocalDate(), entry);
        when(attendances.findOpenAttendanceAtOrBefore(any())).thenReturn(List.of(attendance));
        new AttendanceService(attendances, audits, holidays, users, clock).closeOverdueAttendances();
        var captured = ArgumentCaptor.forClass(AuditLog.class);
        verify(audits).save(captured.capture());
        assertEquals(entry.plusHours(6), attendance.getCheckOut());
        assertTrue(captured.getValue().getDetails().contains("Ana Pérez del 30/10/2026"));
        assertFalse(captured.getValue().getDetails().contains("="));
    }

    @Test void sameDayAdjustmentDoesNotClaimSixHoursWereWorked() {
        String result = AuditMessages.attendance("CIERRE_AUTOMATICO_ASISTENCIA", "Ana Pérez",
                LocalDate.of(2026, 10, 30), LocalDateTime.of(2026, 10, 30, 20, 0),
                LocalDateTime.of(2026, 10, 30, 23, 59), true);
        assertTrue(result.contains("dentro del mismo día"));
        assertFalse(result.contains("6 horas"));
    }
}
