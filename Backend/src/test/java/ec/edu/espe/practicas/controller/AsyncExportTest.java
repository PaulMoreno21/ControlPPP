package ec.edu.espe.practicas.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import ec.edu.espe.practicas.service.*;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

class AsyncExportTest {
    @Test
    void successfulDeferredExportRecordsAudit() throws Exception {
        var service = mock(ActivityReportService.class);
        var exporter = mock(BitacoraExporter.class);
        var auth = mock(Authentication.class);
        var date = LocalDate.of(2026, 10, 29);
        var data = new BitacoraExportData(1L, "Practicante", date, date, List.of());
        when(exporter.format()).thenReturn(BitacoraFormat.CSV);
        when(service.getAcceptedActivities(auth, 1L, date, date)).thenReturn(data);
        when(exporter.export(data)).thenReturn(new byte[] {1, 2});
        var task = new ActivityReportController(service, List.of(exporter)).exportCsv(auth, 1L, date, date);
        verifyNoInteractions(service);
        assertArrayEquals(new byte[] {1, 2}, task.call().getBody());
        verify(service).recordExport(auth, 1L, date, date, "CSV");
    }

    @Test
    void generationIsDeferredAndStillAuthorizesBeforeExporting() throws Exception {
        var service = mock(ActivityReportService.class);
        var exporter = mock(BitacoraExporter.class);
        var auth = mock(Authentication.class);
        when(exporter.format()).thenReturn(BitacoraFormat.CSV);
        var controller = new ActivityReportController(service, List.of(exporter));
        var date = LocalDate.of(2026, 10, 29);
        var task = controller.exportCsv(auth, 1L, date, date);
        verifyNoInteractions(service);
        when(service.getAcceptedActivities(auth, 1L, date, date)).thenThrow(new AccessDeniedException("Denied"));
        assertThrows(AccessDeniedException.class, task::call);
        verify(exporter, never()).export(any());
        verify(service, never()).recordExport(any(), any(), any(), any(), any());
    }
}
