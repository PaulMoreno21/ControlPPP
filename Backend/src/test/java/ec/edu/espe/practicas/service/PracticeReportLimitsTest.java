package ec.edu.espe.practicas.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import ec.edu.espe.practicas.dto.ReportScopeResponse;
import ec.edu.espe.practicas.dto.ReportTraineeOption;
import ec.edu.espe.practicas.model.Activity;
import ec.edu.espe.practicas.repository.ActivityRepository;
import ec.edu.espe.practicas.repository.AttendanceRepository;
import ec.edu.espe.practicas.service.exception.BusinessRuleViolationException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Limit;
import org.springframework.security.core.Authentication;

class PracticeReportLimitsTest {
    private final AttendanceRepository attendance = mock(AttendanceRepository.class);
    private final ActivityRepository activities = mock(ActivityRepository.class);
    private final Authentication auth = mock(Authentication.class);
    private final LocalDate date = LocalDate.of(2026, 10, 29);

    private PracticeReportService service(int traineeCount) {
        var service = spy(new PracticeReportService(null, null, null, null, attendance, activities));
        var trainees = LongStream.rangeClosed(1, traineeCount)
                .mapToObj(id -> new ReportTraineeOption(id, "Practicante " + id, "p@example.test", 1L, "Lab"))
                .toList();
        doReturn(new ReportScopeResponse("ADMIN_GENERAL", List.of(), trainees, true, true))
                .when(service).scope(auth, null);
        when(attendance.findByTraineeIdInAndDateBetweenOrderByDateAsc(anyList(), any(), any(), any(Limit.class)))
                .thenReturn(List.of());
        when(activities.findByTraineeIdInAndDateGreaterThanEqualAndDateLessThanOrderByDateAscIdAsc(
                anyList(), any(), any(), any(Limit.class))).thenReturn(List.of());
        return service;
    }

    @Test
    void refusesOversizedReportInsteadOfReturningPartialTotals() {
        var service = service(1);
        when(activities.findByTraineeIdInAndDateGreaterThanEqualAndDateLessThanOrderByDateAscIdAsc(
                anyList(), any(), any(), any(Limit.class)))
                .thenReturn(Collections.nCopies(10001, mock(Activity.class)));
        assertThrows(BusinessRuleViolationException.class,
                () -> service.report(auth, date, date, null, null, 0, 15));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void splitsLargeOracleInListsIntoBatches() {
        var service = service(501);
        service.report(auth, date, date, null, null, 0, 15);
        ArgumentCaptor<List> ids = ArgumentCaptor.forClass(List.class);
        verify(attendance, times(2)).findByTraineeIdInAndDateBetweenOrderByDateAsc(
                ids.capture(), eq(date), eq(date), any(Limit.class));
        assertEquals(500, ids.getAllValues().get(0).size());
        assertEquals(1, ids.getAllValues().get(1).size());
    }
}
