package ec.edu.espe.practicas.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ec.edu.espe.practicas.controller.ActivityController;
import ec.edu.espe.practicas.controller.ApiExceptionHandler;
import ec.edu.espe.practicas.dto.ActivityReviewSummary;
import ec.edu.espe.practicas.model.*;
import ec.edu.espe.practicas.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ActivityTraineeConsultationTest {
    private final ActivityRepository activities = mock(ActivityRepository.class);
    private final ActivityReviewRepository reviews = mock(ActivityReviewRepository.class);
    private final ActivityService service = new ActivityService(activities, mock(AuditLogRepository.class),
            mock(ActivitySubmissionRepository.class), reviews, mock(AssignmentRepository.class),
            mock(HolidayRepository.class), mock(UserRepository.class),
            Clock.fixed(Instant.parse("2026-11-11T02:00:00Z"), ZoneId.of("America/Guayaquil")));
    private final LocalDate today = LocalDate.of(2026, 11, 10);
    private final LocalDate firstDate = LocalDate.of(2026, 9, 21);
    private final User trainee = trainee();
    private final LocalDateTime reviewedAt = LocalDateTime.of(2026, 11, 10, 9, 30);

    private User trainee() {
        var user = User.create("1234567890", "Ana", "Pérez", "test@example.com", "hash", AccountType.LOCAL);
        ReflectionTestUtils.setField(user, "id", 3L);
        return user;
    }

    private Activity activity(long id, ActivityStatus status) {
        var activity = Activity.draft(trainee, firstDate, "Actividad anterior", BigDecimal.ONE);
        ReflectionTestUtils.setField(activity, "id", id);
        activity.setStatus(status);
        return activity;
    }

    private void page(List<Activity> content) {
        when(activities.findPageForTraineeWithPending(eq(3L), eq(firstDate), eq(today.plusDays(1)),
                eq(ActivityStatus.ENVIADA), any()))
                .thenReturn(new PageImpl<>(content, PageRequest.of(0, 15), content.size()));
    }

    @Test void defaultRangeStartsAtFirstActivityAndEndsAtServerToday() {
        when(activities.findFirstActivityDateByTraineeId(3L)).thenReturn(Optional.of(firstDate));
        var range = service.dateRangeForTrainee(3L);
        assertEquals(firstDate, range.from());
        assertEquals(today, range.to());
        verify(activities).findFirstActivityDateByTraineeId(3L);
        verifyNoInteractions(reviews);
    }

    @Test void noActivitiesDefaultsBothDatesToServerToday() {
        when(activities.findFirstActivityDateByTraineeId(3L)).thenReturn(Optional.empty());
        var range = service.dateRangeForTrainee(3L);
        assertEquals(today, range.from());
        assertEquals(today, range.to());
    }

    @Test void pageContainsRejectionReasonAndLoadsReviewsInOneBatch() {
        page(List.of(activity(11L, ActivityStatus.RECHAZADA), activity(12L, ActivityStatus.ACEPTADA)));
        when(reviews.findLatestForActivityIds(List.of(11L, 12L))).thenReturn(List.of(
                new ActivityReviewSummary(11L, "Explica el resultado obtenido.\nIncluye la tarea realizada.", reviewedAt),
                new ActivityReviewSummary(12L, "Actividad completa", reviewedAt)));

        var result = service.listForTraineePage(3L, firstDate, today, 0, 15);

        assertEquals(2, result.totalElements());
        assertEquals("Explica el resultado obtenido.\nIncluye la tarea realizada.", result.content().getFirst().observation());
        assertEquals(reviewedAt, result.content().getFirst().reviewedAt());
        assertEquals(ActivityStatus.RECHAZADA, result.content().getFirst().status());
        assertEquals("Actividad completa", result.content().get(1).observation());
        verify(reviews).findLatestForActivityIds(List.of(11L, 12L));
        verifyNoMoreInteractions(reviews);
        verify(activities, never()).save(any());
    }

    @Test void pendingNewSubmissionDoesNotReuseAnEarlierRejection() {
        page(List.of(activity(11L, ActivityStatus.ENVIADA)));
        when(reviews.findLatestForActivityIds(List.of(11L))).thenReturn(List.of());
        var result = service.listForTraineePage(3L, firstDate, today, 0, 15).content().getFirst();
        assertNull(result.observation());
        assertNull(result.reviewedAt());
        assertEquals(ActivityStatus.ENVIADA, result.status());
        verify(reviews, never()).findBySubmissionId(any());
    }

    @Test void emptyPageDoesNotQueryReviewsWithAnEmptyInClause() {
        page(List.of());
        assertTrue(service.listForTraineePage(3L, firstDate, today, 0, 15).content().isEmpty());
        verifyNoInteractions(reviews);
    }

    @Test void explicitFiltersArePreservedAndReversedDatesAreRejected() {
        LocalDate from = LocalDate.of(2026, 10, 1);
        when(activities.findPageForTraineeWithPending(eq(3L), eq(from), eq(today.plusDays(1)),
                eq(ActivityStatus.ENVIADA), any())).thenReturn(new PageImpl<>(List.of()));
        service.listForTraineePage(3L, from, today, 0, 15);
        verify(activities, never()).findFirstActivityDateByTraineeId(any());
        assertThrows(ec.edu.espe.practicas.service.exception.BusinessRuleViolationException.class,
                () -> service.listForTraineePage(3L, today, from, 0, 15));
    }

    @Test void nonPagedListAlsoIncludesLatestObservation() {
        when(activities.findByTraineeIdAndDateGreaterThanEqualAndDateLessThanOrderByDateAsc(
                3L, firstDate, today.plusDays(1))).thenReturn(List.of(activity(11L, ActivityStatus.RECHAZADA)));
        when(reviews.findLatestForActivityIds(List.of(11L))).thenReturn(List.of(
                new ActivityReviewSummary(11L, "Detalla la actividad", reviewedAt)));
        assertEquals("Detalla la actividad", service.listResponsesForTrainee(3L, firstDate, today).getFirst().observation());
    }

    @Test void httpRangeUsesAuthenticatedTraineeEvenIfAnotherIdIsSupplied() throws Exception {
        var auth = mock(AuthenticatedUserService.class);
        when(auth.requireRole(isNull(), eq(RoleName.PRACTICANTE))).thenReturn(trainee);
        when(activities.findFirstActivityDateByTraineeId(3L)).thenReturn(Optional.of(firstDate));
        var mvc = MockMvcBuilders.standaloneSetup(new ActivityController(service, auth)).build();
        mvc.perform(get("/api/v1/activities/date-range").param("traineeId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-09-21"))
                .andExpect(jsonPath("$.to").value("2026-11-10"));
        verify(activities, never()).findFirstActivityDateByTraineeId(999L);
    }

    @Test void httpPageExposesReasonToItsOwner() throws Exception {
        var auth = mock(AuthenticatedUserService.class);
        when(auth.requireRole(isNull(), eq(RoleName.PRACTICANTE))).thenReturn(trainee);
        page(List.of(activity(11L, ActivityStatus.RECHAZADA)));
        when(reviews.findLatestForActivityIds(List.of(11L))).thenReturn(List.of(
                new ActivityReviewSummary(11L, "Describe lo realizado", reviewedAt)));
        var mvc = MockMvcBuilders.standaloneSetup(new ActivityController(service, auth)).build();
        mvc.perform(get("/api/v1/activities/page").param("from", "2026-09-21").param("to", "2026-11-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].observation").value("Describe lo realizado"))
                .andExpect(jsonPath("$.content[0].reviewedAt").value("2026-11-10T09:30:00"));
    }

    @Test void rangeIsDeniedWithoutTraineeRole() throws Exception {
        var auth = mock(AuthenticatedUserService.class);
        when(auth.requireRole(isNull(), eq(RoleName.PRACTICANTE))).thenThrow(new AccessDeniedException("Acceso denegado"));
        var mvc = MockMvcBuilders.standaloneSetup(new ActivityController(service, auth))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get("/api/v1/activities/date-range")).andExpect(status().isForbidden());
        verifyNoInteractions(activities, reviews);
    }
}
