package ec.edu.espe.practicas.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ec.edu.espe.practicas.controller.ActivityController;
import ec.edu.espe.practicas.controller.ApiExceptionHandler;
import ec.edu.espe.practicas.dto.TraineePendingCount;
import ec.edu.espe.practicas.model.*;
import ec.edu.espe.practicas.repository.*;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TutorPendingCountsTest {
    private final ActivitySubmissionRepository submissions = mock(ActivitySubmissionRepository.class);
    private final ActivityRepository activities = mock(ActivityRepository.class);
    private final ActivityReviewRepository reviews = mock(ActivityReviewRepository.class);
    private final ActivityService service = new ActivityService(activities, mock(AuditLogRepository.class),
            submissions, reviews, mock(AssignmentRepository.class), mock(HolidayRepository.class),
            mock(UserRepository.class), Clock.systemUTC());

    @Test void aggregatesAllTraineesInOneQueryWithoutLoadingActivities() {
        when(submissions.findPendingCountsByTutorId(6L, ActivityStatus.ENVIADA)).thenReturn(List.of(
                new TraineePendingCount(3L, 4), new TraineePendingCount(7L, 2)));
        var response = service.pendingCountsForTutor(6L);
        assertEquals(6, response.total());
        assertEquals(2, response.trainees().size());
        assertEquals(4, response.trainees().getFirst().pendingActivitiesCount());
        verify(submissions).findPendingCountsByTutorId(6L, ActivityStatus.ENVIADA);
        verifyNoMoreInteractions(submissions);
        verifyNoInteractions(activities, reviews);
    }

    @Test void noPendingActivitiesReturnsZeroAndAnEmptyBreakdown() {
        when(submissions.findPendingCountsByTutorId(6L, ActivityStatus.ENVIADA)).thenReturn(List.of());
        var response = service.pendingCountsForTutor(6L);
        assertEquals(0, response.total());
        assertTrue(response.trainees().isEmpty());
    }

    @Test void countsAreRecalculatedAfterReviewsInsteadOfBeingCached() {
        when(submissions.findPendingCountsByTutorId(6L, ActivityStatus.ENVIADA))
                .thenReturn(List.of(new TraineePendingCount(3L, 2)))
                .thenReturn(List.of(new TraineePendingCount(3L, 1)))
                .thenReturn(List.of());
        assertEquals(2, service.pendingCountsForTutor(6L).total());
        assertEquals(1, service.pendingCountsForTutor(6L).total());
        assertEquals(0, service.pendingCountsForTutor(6L).total());
        verify(submissions, times(3)).findPendingCountsByTutorId(6L, ActivityStatus.ENVIADA);
    }

    @Test void totalSupportsLargeCountsWithoutIntegerOverflow() {
        when(submissions.findPendingCountsByTutorId(6L, ActivityStatus.ENVIADA)).thenReturn(List.of(
                new TraineePendingCount(3L, Integer.MAX_VALUE), new TraineePendingCount(7L, 2)));
        assertEquals((long) Integer.MAX_VALUE + 2, service.pendingCountsForTutor(6L).total());
    }

    @Test void httpOnlyUsesAuthenticatedTutorAndReturnsBothCounters() throws Exception {
        var auth = mock(AuthenticatedUserService.class);
        var tutor = User.create("1234567890", "Luis", "Vera", "test@example.com", "hash", AccountType.LOCAL);
        ReflectionTestUtils.setField(tutor, "id", 6L);
        when(auth.requireRole(isNull(), eq(RoleName.TUTOR_ESPE))).thenReturn(tutor);
        when(submissions.findPendingCountsByTutorId(6L, ActivityStatus.ENVIADA))
                .thenReturn(List.of(new TraineePendingCount(3L, 2)));
        var mvc = MockMvcBuilders.standaloneSetup(new ActivityController(service, auth)).build();
        mvc.perform(get("/api/v1/tutor/pending-counts").param("tutorId", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.trainees[0].traineeId").value(3))
                .andExpect(jsonPath("$.trainees[0].pendingActivitiesCount").value(2));
        verify(submissions, never()).findPendingCountsByTutorId(eq(999L), any());
    }

    @Test void otherRolesCannotReadTutorCounts() throws Exception {
        var auth = mock(AuthenticatedUserService.class);
        when(auth.requireRole(isNull(), eq(RoleName.TUTOR_ESPE))).thenThrow(new AccessDeniedException("Acceso denegado"));
        var mvc = MockMvcBuilders.standaloneSetup(new ActivityController(service, auth))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get("/api/v1/tutor/pending-counts")).andExpect(status().isForbidden());
        verifyNoInteractions(submissions, activities, reviews);
    }
}
