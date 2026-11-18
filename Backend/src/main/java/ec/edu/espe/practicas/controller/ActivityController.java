package ec.edu.espe.practicas.controller;

import ec.edu.espe.practicas.dto.ActivityRequest;
import ec.edu.espe.practicas.dto.ActivityBatchRequest;
import ec.edu.espe.practicas.dto.ActivityResponse;
import ec.edu.espe.practicas.dto.ActivityDateRange;
import ec.edu.espe.practicas.dto.TutorPendingCountsResponse;
import ec.edu.espe.practicas.dto.ActivityReviewResponse;
import ec.edu.espe.practicas.dto.ActivitySubmissionResponse;
import ec.edu.espe.practicas.dto.ReviewRequest;
import ec.edu.espe.practicas.dto.TutorTraineeActivitiesResponse;
import ec.edu.espe.practicas.dto.TutorTraineeResponse;
import ec.edu.espe.practicas.dto.PagedResponse;
import ec.edu.espe.practicas.dto.TutorTraineeActivitiesPageResponse;
import ec.edu.espe.practicas.model.RoleName;
import ec.edu.espe.practicas.service.ActivityService;
import ec.edu.espe.practicas.service.ActivityDraftInput;
import ec.edu.espe.practicas.service.AuthenticatedUserService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ActivityController {
    private final ActivityService activityService;
    private final AuthenticatedUserService authenticatedUserService;

    public ActivityController(
            ActivityService activityService,
            AuthenticatedUserService authenticatedUserService) {
        this.activityService = activityService;
        this.authenticatedUserService = authenticatedUserService;
    }

    @GetMapping("/activities")
    public List<ActivityResponse> listActivities(
            Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var trainee = authenticatedUserService.requireRole(authentication, RoleName.PRACTICANTE);
        return activityService.listResponsesForTrainee(trainee.getId(), from, to);
    }

    @GetMapping("/activities/date-range")
    public ActivityDateRange activityDateRange(Authentication authentication) {
        var trainee = authenticatedUserService.requireRole(authentication, RoleName.PRACTICANTE);
        return activityService.dateRangeForTrainee(trainee.getId());
    }

    @GetMapping("/activities/page")
    public PagedResponse<ActivityResponse> listActivitiesPage(
            Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        var trainee = authenticatedUserService.requireRole(authentication, RoleName.PRACTICANTE);
        return activityService.listForTraineePage(trainee.getId(), from, to, page, size);
    }

    @GetMapping("/activities/pending")
    public List<ActivityResponse> listPendingForTrainee(Authentication authentication) {
        var trainee = authenticatedUserService.requireRole(authentication, RoleName.PRACTICANTE);
        return activityService.listPendingForTrainee(trainee.getId()).stream()
                .map(ActivityResponse::from)
                .toList();
    }

    @PostMapping("/activities")
    @ResponseStatus(HttpStatus.CREATED)
    public ActivityResponse createDraft(
            Authentication authentication, @Valid @RequestBody ActivityRequest request) {
        var trainee = authenticatedUserService.requireRole(authentication, RoleName.PRACTICANTE);
        return ActivityResponse.from(activityService.createDraft(
                trainee.getId(), request.date(), request.description(), request.durationHours()));
    }

    @PostMapping("/activities/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ActivityResponse> createDrafts(
            Authentication authentication, @Valid @RequestBody ActivityBatchRequest request) {
        var trainee = authenticatedUserService.requireRole(authentication, RoleName.PRACTICANTE);
        List<ActivityDraftInput> activities = request.activities().stream()
                .map(activity -> new ActivityDraftInput(activity.description(), activity.durationHours()))
                .toList();
        return activityService.createDrafts(trainee.getId(), request.date(), activities).stream()
                .map(ActivityResponse::from)
                .toList();
    }

    @PutMapping("/activities/{activityId}")
    public ActivityResponse updateDraft(
            Authentication authentication,
            @PathVariable Long activityId,
            @Valid @RequestBody ActivityRequest request) {
        var trainee = authenticatedUserService.requireRole(authentication, RoleName.PRACTICANTE);
        return ActivityResponse.from(activityService.updateDraft(
                trainee.getId(), activityId, request.date(), request.description(), request.durationHours()));
    }

    @DeleteMapping("/activities/{activityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDraft(Authentication authentication, @PathVariable Long activityId) {
        var trainee = authenticatedUserService.requireRole(authentication, RoleName.PRACTICANTE);
        activityService.deleteDraft(trainee.getId(), activityId);
    }

    @PostMapping("/activities/{activityId}/submissions")
    @ResponseStatus(HttpStatus.CREATED)
    public ActivitySubmissionResponse submit(Authentication authentication, @PathVariable Long activityId) {
        var trainee = authenticatedUserService.requireRole(authentication, RoleName.PRACTICANTE);
        return ActivitySubmissionResponse.from(activityService.submit(trainee.getId(), activityId));
    }

    @GetMapping("/tutor/submissions/pending")
    public List<ActivitySubmissionResponse> listPending(Authentication authentication) {
        var tutor = authenticatedUserService.requireRole(authentication, RoleName.TUTOR_ESPE);
        return activityService.listPendingForTutor(tutor.getId()).stream()
                .map(ActivitySubmissionResponse::from)
                .toList();
    }

    @GetMapping("/tutor/pending-counts")
    public TutorPendingCountsResponse pendingCounts(Authentication authentication) {
        var tutor = authenticatedUserService.requireRole(authentication, RoleName.TUTOR_ESPE);
        return activityService.pendingCountsForTutor(tutor.getId());
    }

    @GetMapping("/tutor/trainees")
    public List<TutorTraineeResponse> listAssignedTrainees(Authentication authentication) {
        var tutor = authenticatedUserService.requireRole(authentication, RoleName.TUTOR_ESPE);
        return activityService.listAssignedTrainees(tutor.getId());
    }

    @GetMapping("/tutor/trainees/page")
    public PagedResponse<TutorTraineeResponse> listAssignedTraineesPage(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        var tutor = authenticatedUserService.requireRole(authentication, RoleName.TUTOR_ESPE);
        return activityService.listAssignedTraineesPage(tutor.getId(), search, page, size);
    }

    @GetMapping("/tutor/trainees/{traineeId}/activities")
    public TutorTraineeActivitiesResponse listTraineeActivities(
            Authentication authentication, @PathVariable Long traineeId) {
        var tutor = authenticatedUserService.requireRole(authentication, RoleName.TUTOR_ESPE);
        return activityService.listActivitiesForTutor(tutor.getId(), traineeId);
    }

    @GetMapping("/tutor/trainees/{traineeId}/activities/page")
    public TutorTraineeActivitiesPageResponse listTraineeActivitiesPage(
            Authentication authentication,
            @PathVariable Long traineeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        var tutor = authenticatedUserService.requireRole(authentication, RoleName.TUTOR_ESPE);
        return activityService.listActivitiesForTutorPage(tutor.getId(), traineeId, page, size);
    }

    @PostMapping("/tutor/submissions/{submissionId}/review")
    public ActivityReviewResponse review(
            Authentication authentication,
            @PathVariable Long submissionId,
            @Valid @RequestBody ReviewRequest request) {
        var tutor = authenticatedUserService.requireRole(authentication, RoleName.TUTOR_ESPE);
        return ActivityReviewResponse.from(activityService.review(
                tutor.getId(), submissionId, request.decision(), request.observation()));
    }
}
