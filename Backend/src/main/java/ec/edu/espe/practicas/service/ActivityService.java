package ec.edu.espe.practicas.service;

import ec.edu.espe.practicas.model.Activity;
import ec.edu.espe.practicas.model.AuditLog;
import ec.edu.espe.practicas.model.ActivityReview;
import ec.edu.espe.practicas.model.ActivityStatus;
import ec.edu.espe.practicas.model.ActivitySubmission;
import ec.edu.espe.practicas.model.Assignment;
import ec.edu.espe.practicas.model.ReviewDecision;
import ec.edu.espe.practicas.model.User;
import ec.edu.espe.practicas.dto.TutorActivityResponse;
import ec.edu.espe.practicas.dto.ActivityResponse;
import ec.edu.espe.practicas.dto.ActivityDateRange;
import ec.edu.espe.practicas.dto.ActivityReviewSummary;
import ec.edu.espe.practicas.dto.TutorPendingCountsResponse;
import ec.edu.espe.practicas.dto.PagedResponse;
import ec.edu.espe.practicas.dto.TutorTraineeActivitiesPageResponse;
import ec.edu.espe.practicas.dto.TutorTraineeActivitiesResponse;
import ec.edu.espe.practicas.dto.TutorTraineeResponse;
import ec.edu.espe.practicas.repository.ActivityRepository;
import ec.edu.espe.practicas.repository.AuditLogRepository;
import ec.edu.espe.practicas.repository.ActivityReviewRepository;
import ec.edu.espe.practicas.repository.ActivitySubmissionRepository;
import ec.edu.espe.practicas.repository.AssignmentRepository;
import ec.edu.espe.practicas.repository.HolidayRepository;
import ec.edu.espe.practicas.repository.UserRepository;
import ec.edu.espe.practicas.service.exception.BusinessRuleViolationException;
import ec.edu.espe.practicas.service.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ActivityService {
    private static final BigDecimal MAX_DAILY_HOURS = BigDecimal.valueOf(6);
    private static final Integer CURRENT_ASSIGNMENT = 1;
    private static final DateTimeFormatter AUDIT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ActivityRepository activityRepository;
    private final AuditLogRepository auditLogRepository;
    private final ActivitySubmissionRepository submissionRepository;
    private final ActivityReviewRepository reviewRepository;
    private final AssignmentRepository assignmentRepository;
    private final HolidayRepository holidayRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public ActivityService(
            ActivityRepository activityRepository,
            AuditLogRepository auditLogRepository,
            ActivitySubmissionRepository submissionRepository,
            ActivityReviewRepository reviewRepository,
            AssignmentRepository assignmentRepository,
            HolidayRepository holidayRepository,
            UserRepository userRepository,
            Clock clock) {
        this.activityRepository = activityRepository;
        this.auditLogRepository = auditLogRepository;
        this.submissionRepository = submissionRepository;
        this.reviewRepository = reviewRepository;
        this.assignmentRepository = assignmentRepository;
        this.holidayRepository = holidayRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    public Activity createDraft(Long traineeId, LocalDate date, String description, BigDecimal durationHours) {
        User trainee = findActiveUser(traineeId);
        validateCreationDate(date);
        validateActivityInput(date, description, durationHours);
        validateDailyHours(traineeId, date, null, durationHours);

        Activity activity = activityRepository.save(
                Activity.draft(trainee, date, description.trim(), durationHours));
        auditLogRepository.save(AuditLog.record(
                trainee, "CREAR_ACTIVIDAD", "TCWACTIVIDADES", activity.getId(),
                LocalDateTime.now(clock), "El practicante " + fullName(trainee)
                        + " registró la actividad «" + activity.getDescription() + "» para el "
                        + formatAuditDate(date) + "."));
        return activity;
    }

    public List<Activity> createDrafts(Long traineeId, LocalDate date, List<ActivityDraftInput> drafts) {
        User trainee = findActiveUser(traineeId);
        if (drafts == null || drafts.isEmpty()) {
            throw new BusinessRuleViolationException("Debe ingresar al menos una actividad.");
        }
        validateCreationDate(date);
        drafts.forEach(draft -> validateActivityInput(
                date, draft.description(), draft.durationHours()));

        BigDecimal requestedHours = drafts.stream()
                .map(ActivityDraftInput::durationHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        validateDailyHours(traineeId, date, null, requestedHours);

        LocalDateTime now = LocalDateTime.now(clock);
        return drafts.stream()
                .map(draft -> {
                    Activity activity = activityRepository.save(Activity.draft(
                            trainee, date, draft.description().trim(), draft.durationHours()));
                    auditLogRepository.save(AuditLog.record(
                            trainee, "CREAR_ACTIVIDAD", "TCWACTIVIDADES", activity.getId(),
                            now, "El practicante " + fullName(trainee)
                                    + " registró la actividad «" + activity.getDescription() + "» para el "
                                    + formatAuditDate(date) + "."));
                    return activity;
                })
                .toList();
    }

    public Activity updateDraft(
            Long traineeId,
            Long activityId,
            LocalDate date,
            String description,
            BigDecimal durationHours) {
        Activity activity = findOwnedActivity(traineeId, activityId);
        if (activity.getStatus() != ActivityStatus.BORRADOR
                && activity.getStatus() != ActivityStatus.RECHAZADA) {
            throw new BusinessRuleViolationException(
                    "Solo se pueden editar actividades en borrador o rechazadas.");
        }

        validateActivityInput(date, description, durationHours);
        validateDailyHours(traineeId, date, activityId, durationHours);

        activity.setDate(date);
        activity.setDescription(description.trim());
        activity.setDurationHours(durationHours);
        Activity savedActivity = activityRepository.save(activity);
        auditLogRepository.save(AuditLog.record(
                activity.getTrainee(), "EDITAR_ACTIVIDAD", "TCWACTIVIDADES", activity.getId(),
                LocalDateTime.now(clock), "El practicante " + fullName(activity.getTrainee())
                        + " actualizó la actividad «" + savedActivity.getDescription() + "» para el "
                        + formatAuditDate(date) + "."));
        return savedActivity;
    }

    public void deleteDraft(Long traineeId, Long activityId) {
        Activity activity = findOwnedActivity(traineeId, activityId);
        if (activity.getStatus() != ActivityStatus.BORRADOR) {
            throw new BusinessRuleViolationException(
                    "Solo se pueden eliminar actividades que aún están en borrador.");
        }
        if (!submissionRepository.findByActivityIdOrderBySubmissionNumberDesc(activityId).isEmpty()) {
            throw new BusinessRuleViolationException(
                    "No se puede eliminar una actividad que conserva historial de revisión.");
        }

        auditLogRepository.save(AuditLog.record(
                activity.getTrainee(), "ELIMINAR_ACTIVIDAD", "TCWACTIVIDADES", activity.getId(),
                LocalDateTime.now(clock), "El practicante " + fullName(activity.getTrainee())
                        + " eliminó la actividad «" + activity.getDescription() + "» del "
                        + formatAuditDate(activity.getDate()) + "."));
        activityRepository.delete(activity);
    }

    public ActivitySubmission submit(Long traineeId, Long activityId) {
        Activity activity = findOwnedActivity(traineeId, activityId);
        if (activity.getStatus() != ActivityStatus.BORRADOR
                && activity.getStatus() != ActivityStatus.RECHAZADA) {
            throw new BusinessRuleViolationException(
                    "Solo se pueden enviar actividades en borrador o rechazadas.");
        }

        validateActivityDate(activity.getDate());
        validateDailyHours(traineeId, activity.getDate(), null, BigDecimal.ZERO);

        Assignment assignment = assignmentRepository
                .findByTraineeIdAndCurrent(traineeId, CURRENT_ASSIGNMENT)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "El practicante no tiene un tutor asignado vigente."));

        int submissionNumber = submissionRepository
                .findFirstByActivityIdOrderBySubmissionNumberDesc(activityId)
                .map(previous -> previous.getSubmissionNumber() + 1)
                .orElse(1);
        LocalDateTime submittedAt = LocalDateTime.now(clock);

        activity.submit(submittedAt);
        ActivitySubmission submission = submissionRepository.save(ActivitySubmission.create(
                activity, assignment.getTutor(), submissionNumber, submittedAt));
        activityRepository.save(activity);
        auditLogRepository.save(AuditLog.record(
                activity.getTrainee(), "ENVIAR_ACTIVIDAD", "TCWACTIVIDADES", activity.getId(),
                submittedAt, "El practicante " + fullName(activity.getTrainee())
                        + " envió a revisión la actividad «" + activity.getDescription() + "» del "
                        + formatAuditDate(activity.getDate()) + " (envío " + submissionNumber + ")."));
        return submission;
    }

    @Transactional(readOnly = true)
    public List<Activity> listForTrainee(Long traineeId, LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new BusinessRuleViolationException("El rango de fechas de consulta no es válido.");
        }
        return activityRepository.findByTraineeIdAndDateGreaterThanEqualAndDateLessThanOrderByDateAsc(
                traineeId, from, to.plusDays(1));
    }

    @Transactional(readOnly = true)
    public ActivityDateRange dateRangeForTrainee(Long traineeId) {
        LocalDate today = LocalDate.now(clock);
        return new ActivityDateRange(
                activityRepository.findFirstActivityDateByTraineeId(traineeId).orElse(today), today);
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> listResponsesForTrainee(Long traineeId, LocalDate from, LocalDate to) {
        var activities = listForTrainee(traineeId, from, to);
        var reviews = latestReviews(activities);
        return activities.stream().map(activity -> ActivityResponse.from(activity, reviews.get(activity.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PagedResponse<ActivityResponse> listForTraineePage(
            Long traineeId, LocalDate from, LocalDate to, int page, int size) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new BusinessRuleViolationException("El rango de fechas de consulta no es válido.");
        }
        var activities = activityRepository.findPageForTraineeWithPending(
                traineeId, from, to.plusDays(1), ActivityStatus.ENVIADA,
                PageableFactory.create(page, size, Sort.by(Sort.Order.asc("date"), Sort.Order.asc("id"))));
        var reviews = latestReviews(activities.getContent());
        return PagedResponse.from(activities, activity -> ActivityResponse.from(activity, reviews.get(activity.getId())));
    }

    private Map<Long, ActivityReviewSummary> latestReviews(List<Activity> activities) {
        if (activities.isEmpty()) return Map.of();
        return reviewRepository.findLatestForActivityIds(activities.stream().map(Activity::getId).toList())
                .stream().collect(Collectors.toMap(ActivityReviewSummary::activityId, review -> review));
    }

    @Transactional(readOnly = true)
    public List<Activity> listPendingForTrainee(Long traineeId) {
        return activityRepository.findByTraineeIdAndStatusOrderByDateDescIdDesc(
                traineeId, ActivityStatus.ENVIADA);
    }

    @Transactional(readOnly = true)
    public TutorPendingCountsResponse pendingCountsForTutor(Long tutorId) {
        return TutorPendingCountsResponse.from(
                submissionRepository.findPendingCountsByTutorId(tutorId, ActivityStatus.ENVIADA));
    }

    @Transactional(readOnly = true)
    public List<ActivitySubmission> listPendingForTutor(Long tutorId) {
        return submissionRepository.findPendingByTutorId(tutorId);
    }

    @Transactional(readOnly = true)
    public List<TutorTraineeResponse> listAssignedTrainees(Long tutorId) {
        return assignmentRepository.findByTutorIdAndCurrent(tutorId, CURRENT_ASSIGNMENT).stream()
                .map(TutorTraineeResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PagedResponse<TutorTraineeResponse> listAssignedTraineesPage(
            Long tutorId, String search, int page, int size) {
        String normalizedSearch = search == null || search.isBlank() ? null : "%" + search.trim() + "%";
        return PagedResponse.from(assignmentRepository.findActiveByTutorIdAndTraineeSearch(
                tutorId, normalizedSearch,
                PageableFactory.create(page, size, Sort.by(Sort.Order.asc("trainee.lastName"),
                        Sort.Order.asc("trainee.firstName"), Sort.Order.asc("id")))),
                TutorTraineeResponse::from);
    }

    @Transactional(readOnly = true)
    public TutorTraineeActivitiesResponse listActivitiesForTutor(Long tutorId, Long traineeId) {
        Assignment assignment = assignmentRepository
                .findByTraineeIdAndCurrent(traineeId, CURRENT_ASSIGNMENT)
                .filter(current -> Objects.equals(current.getTutor().getId(), tutorId))
                .orElseThrow(() -> new AccessDeniedException(
                        "El practicante no está asignado a este tutor."));

        List<TutorActivityResponse> activities = activityRepository
                .findByTraineeIdAndStatusNotOrderByDateAscIdAsc(traineeId, ActivityStatus.BORRADOR)
                .stream()
                .map(activity -> {
                    ActivitySubmission submission = submissionRepository
                            .findFirstByActivityIdOrderBySubmissionNumberDesc(activity.getId())
                            .orElse(null);
                    ActivityReview review = submission == null
                            ? null
                            : reviewRepository.findBySubmissionId(submission.getId()).orElse(null);
                    return TutorActivityResponse.from(activity, submission, review);
                })
                .toList();
        BigDecimal acceptedHours = activities.stream()
                .filter(activity -> ActivityStatus.ACEPTADA.name().equals(activity.status()))
                .map(TutorActivityResponse::durationHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new TutorTraineeActivitiesResponse(
                TutorTraineeResponse.from(assignment), acceptedHours, activities);
    }

    @Transactional(readOnly = true)
    public TutorTraineeActivitiesPageResponse listActivitiesForTutorPage(
            Long tutorId, Long traineeId, int page, int size) {
        Assignment assignment = assignmentRepository
                .findByTraineeIdAndCurrent(traineeId, CURRENT_ASSIGNMENT)
                .filter(current -> Objects.equals(current.getTutor().getId(), tutorId))
                .orElseThrow(() -> new AccessDeniedException(
                        "El practicante no está asignado a este tutor."));

        var activityPage = activityRepository.findByTraineeIdAndStatusNot(
                traineeId, ActivityStatus.BORRADOR,
                PageableFactory.create(page, size,
                        Sort.by(Sort.Order.asc("date"), Sort.Order.asc("id"))));
        var activityResponses = activityPage.map(activity -> {
            ActivitySubmission submission = submissionRepository
                    .findFirstByActivityIdOrderBySubmissionNumberDesc(activity.getId())
                    .orElse(null);
            ActivityReview review = submission == null
                    ? null
                    : reviewRepository.findBySubmissionId(submission.getId()).orElse(null);
            return TutorActivityResponse.from(activity, submission, review);
        });
        var acceptedHours = activityRepository.sumDurationHoursByTraineeIdAndStatus(
                traineeId, ActivityStatus.ACEPTADA);
        long acceptedCount = activityRepository.countByTraineeIdAndStatus(traineeId, ActivityStatus.ACEPTADA);
        long pendingCount = activityRepository.countByTraineeIdAndStatus(traineeId, ActivityStatus.ENVIADA);
        return new TutorTraineeActivitiesPageResponse(
                TutorTraineeResponse.from(assignment), acceptedHours, acceptedCount, pendingCount,
                PagedResponse.from(activityResponses, response -> response));
    }

    public ActivityReview review(
            Long tutorId, Long submissionId, ReviewDecision decision, String observation) {
        ActivitySubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el envío indicado."));
        if (reviewRepository.findBySubmissionId(submissionId).isPresent()) {
            throw new BusinessRuleViolationException("Este envío ya tiene una revisión registrada.");
        }
        if (decision == null) {
            throw new BusinessRuleViolationException("Debe seleccionar una decisión de revisión.");
        }
        if (decision == ReviewDecision.RECHAZADA && (observation == null || observation.isBlank())) {
            throw new BusinessRuleViolationException("El rechazo requiere una observación.");
        }

        Long traineeId = submission.getActivity().getTrainee().getId();
        Assignment assignment = assignmentRepository
                .findByTraineeIdAndCurrent(traineeId, CURRENT_ASSIGNMENT)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "El practicante no tiene un tutor asignado vigente."));
        if (!assignment.getTutor().getId().equals(tutorId)
                || !submission.getTutor().getId().equals(tutorId)) {
            throw new BusinessRuleViolationException(
                    "El envío solo puede ser revisado por el tutor asignado.");
        }

        LocalDateTime reviewedAt = LocalDateTime.now(clock);
        String normalizedObservation = observation == null || observation.isBlank()
                ? null
                : observation.trim();
        ActivityReview review = reviewRepository.save(ActivityReview.create(
                submission, assignment.getTutor(), decision, normalizedObservation, reviewedAt));
        Activity activity = submission.getActivity();
        activity.recordDecision(decision);
        activityRepository.save(activity);
        auditLogRepository.save(AuditLog.record(
                assignment.getTutor(),
                decision == ReviewDecision.ACEPTADA ? "ACEPTAR_ACTIVIDAD" : "RECHAZAR_ACTIVIDAD",
                "TCWACTIVIDADES",
                activity.getId(),
                reviewedAt,
                "El tutor " + fullName(assignment.getTutor())
                        + (decision == ReviewDecision.ACEPTADA ? " aceptó" : " rechazó")
                        + " la actividad «" + activity.getDescription() + "» enviada por el practicante "
                        + fullName(activity.getTrainee()) + " del " + formatAuditDate(activity.getDate())
                        + (decision == ReviewDecision.RECHAZADA
                                ? ". Observación: " + normalizedObservation
                                : ".")));
        return review;
    }

    private User findActiveUser(Long traineeId) {
        User trainee = userRepository.findById(traineeId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el practicante indicado."));
        if (!Integer.valueOf(1).equals(trainee.getActive())) {
            throw new BusinessRuleViolationException("La cuenta del practicante está inactiva.");
        }
        return trainee;
    }

    private Activity findOwnedActivity(Long traineeId, Long activityId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe la actividad indicada."));
        if (!activity.getTrainee().getId().equals(traineeId)) {
            throw new BusinessRuleViolationException("La actividad no pertenece al practicante indicado.");
        }
        return activity;
    }

    private void validateCreationDate(LocalDate date) {
        if (date != null && date.isAfter(LocalDate.now(clock))) {
            throw new BusinessRuleViolationException("No se pueden crear actividades en fechas futuras.");
        }
    }

    private void validateActivityInput(LocalDate date, String description, BigDecimal durationHours) {
        if (date == null) {
            throw new BusinessRuleViolationException("La fecha de la actividad es obligatoria.");
        }
        validateActivityDate(date);
        if (description == null || description.isBlank()) {
            throw new BusinessRuleViolationException("La descripción de la actividad es obligatoria.");
        }
        if (durationHours == null || durationHours.signum() <= 0) {
            throw new BusinessRuleViolationException("La duración debe ser mayor que cero.");
        }
    }

    private String fullName(User user) {
        return user.getFirstName() + " " + user.getLastName();
    }

    private String formatAuditDate(LocalDate date) {
        return date.format(AUDIT_DATE_FORMAT);
    }

    private void validateActivityDate(LocalDate date) {
        if (date.getDayOfWeek().getValue() > 5) {
            throw new BusinessRuleViolationException(
                    "No se pueden registrar actividades en fines de semana.");
        }
        if (holidayRepository.existsByDateAndActive(date, 1)) {
            throw new BusinessRuleViolationException(
                    "No se pueden registrar actividades en un día feriado.");
        }
    }

    private void validateDailyHours(
            Long traineeId,
            LocalDate date,
            Long excludedActivityId,
            BigDecimal additionalHours) {
        List<Activity> activities = activityRepository
                .findByTraineeIdAndDateGreaterThanEqualAndDateLessThanOrderByIdAsc(
                        traineeId, date, date.plusDays(1));
        BigDecimal totalHours = activities.stream()
                .filter(activity -> !activity.getId().equals(excludedActivityId))
                .map(Activity::getDurationHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(additionalHours);

        if (totalHours.compareTo(MAX_DAILY_HOURS) > 0) {
            throw new BusinessRuleViolationException("Las actividades no pueden superar seis horas diarias.");
        }
    }
}
