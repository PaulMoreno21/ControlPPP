package ec.edu.espe.practicas.service.holidays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import ec.edu.espe.practicas.config.HolidaySynchronizationSettings;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HolidaySynchronizationServiceTest {
    private final FeriadosIoClient client = mock(FeriadosIoClient.class);
    private final HolidayImportService importer = mock(HolidayImportService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T02:00:00Z"), ZoneId.of("America/Guayaquil"));

    private HolidaySynchronizationService service(boolean enabled, String key, long owner) {
        return new HolidaySynchronizationService(new HolidaySynchronizationSettings(enabled, key, owner), client, importer, clock);
    }

    @Test void disabledOrMissingConfigurationNeverFetchesOrWrites() {
        var disabled = service(false, "frd_fixture", 1L);
        disabled.synchronize(); assertEquals("DISABLED", disabled.status().state());
        verifyNoInteractions(importer);
        for (var configured : List.of(service(true, "", 1L), service(true, "frd_fixture", 0))) {
            configured.synchronize(); assertEquals("NOT_CONFIGURED", configured.status().state());
        }
        verifyNoInteractions(client);
        verify(importer, never()).importYear(anyInt(), anyList(), anyLong());
    }

    @Test void fetchesOnlyTheLocalCurrentYearAndOnlyOnceInThisInstance() {
        var first = List.of(new NationalHoliday(LocalDate.of(2025, 1, 1), "Uno"));
        when(client.holidays(2025)).thenReturn(first);
        when(importer.importYear(2025, first, 1L)).thenReturn(1);
        var synchronization = service(true, "frd_fixture", 1L);
        synchronization.synchronize(); synchronization.synchronize();
        assertEquals("SUCCESS", synchronization.status().state());
        assertEquals(1, synchronization.status().added());
        assertEquals(clock.instant(), synchronization.status().lastSuccess());
        verify(client, times(1)).holidays(2025); verifyNoMoreInteractions(client);
    }

    @Test void persistedCompletionPreventsRequestsAfterRestartAndManualChanges() {
        when(client.holidays(2025)).thenReturn(List.of());
        var first = service(true, "frd_fixture", 1L); first.synchronize();
        when(importer.completion(2025)).thenReturn(Optional.of(LocalDateTime.now(clock)));
        // Another service instance represents a Backend restart; key can even be removed afterwards.
        var restarted = service(true, "", 0); restarted.synchronize();
        assertEquals("SUCCESS", restarted.status().state());
        verify(client, times(1)).holidays(2025);
        verify(importer, times(1)).importYear(eq(2025), anyList(), eq(1L));
    }

    @Test void newYearEnablesOneNewRequestWithoutReimportingPreviousYear() {
        var instant = new AtomicReference<>(Instant.parse("2025-12-31T23:00:00Z"));
        Clock moving = new Clock() {
            @Override public Instant instant() { return instant.get(); }
            @Override public ZoneId getZone() { return ZoneId.of("America/Guayaquil"); }
            @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        };
        when(client.holidays(anyInt())).thenReturn(List.of());
        var synchronization = new HolidaySynchronizationService(
                new HolidaySynchronizationSettings(true, "frd_fixture", 1L), client, importer, moving);
        synchronization.synchronize();
        instant.set(Instant.parse("2026-01-01T05:01:00Z"));
        synchronization.synchronize(); synchronization.synchronize();
        verify(client, times(1)).holidays(2025); verify(client, times(1)).holidays(2026);
        verifyNoMoreInteractions(client);
    }

    @Test void failureDoesNotMarkTheYearCompletedAndCanRecoverOnAnotherStartup() {
        when(client.holidays(anyInt())).thenThrow(new IllegalStateException("frd_secret / upstream error"));
        var synchronization = service(true, "frd_fixture", 1L);
        synchronization.synchronize(); assertEquals("ERROR", synchronization.status().state());
        assertNull(synchronization.status().lastSuccess());
        assertFalse(synchronization.status().message().contains("secret"));
        verify(importer, never()).importYear(anyInt(), anyList(), anyLong());
        doReturn(List.of()).when(client).holidays(anyInt());
        synchronization.synchronize(); assertEquals("SUCCESS", synchronization.status().state());
    }

    @Test void databaseFailureDoesNotSuppressTheNextAttempt() {
        when(client.holidays(2025)).thenReturn(List.of());
        when(importer.importYear(eq(2025), anyList(), eq(1L))).thenThrow(new IllegalStateException("rollback"));
        var synchronization = service(true, "frd_fixture", 1L);
        synchronization.synchronize(); assertEquals("ERROR", synchronization.status().state());
        doReturn(1).when(importer).importYear(eq(2025), anyList(), eq(1L));
        synchronization.synchronize(); assertEquals("SUCCESS", synchronization.status().state());
        verify(client, times(2)).holidays(2025);
    }

    @Test void concurrentRunsDoNotMultiplyRequests() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(client.holidays(2025)).thenAnswer(invocation -> {
            entered.countDown(); release.await(5, TimeUnit.SECONDS); return List.of();
        });
        var synchronization = service(true, "frd_fixture", 1L);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(synchronization::synchronize);
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                synchronization.synchronize(); assertEquals("RUNNING", synchronization.status().state());
                verify(client, times(1)).holidays(2025);
            } finally { release.countDown(); }
            first.get(5, TimeUnit.SECONDS);
        }
        verifyNoMoreInteractions(client);
    }
}
