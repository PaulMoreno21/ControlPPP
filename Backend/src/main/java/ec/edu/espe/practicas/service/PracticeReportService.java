package ec.edu.espe.practicas.service;

import ec.edu.espe.practicas.dto.LaboratorySummaryResponse;
import ec.edu.espe.practicas.dto.PagedResponse;
import ec.edu.espe.practicas.dto.PracticeReportResponse;
import ec.edu.espe.practicas.dto.PracticeReportRow;
import ec.edu.espe.practicas.dto.ReportActivityRow;
import ec.edu.espe.practicas.dto.ReportScopeResponse;
import ec.edu.espe.practicas.dto.ReportTraineeOption;
import ec.edu.espe.practicas.model.Activity;
import ec.edu.espe.practicas.model.Assignment;
import ec.edu.espe.practicas.model.Attendance;
import ec.edu.espe.practicas.model.ActivityStatus;
import ec.edu.espe.practicas.model.Laboratory;
import ec.edu.espe.practicas.model.RoleName;
import ec.edu.espe.practicas.model.User;
import ec.edu.espe.practicas.model.UserRole;
import ec.edu.espe.practicas.repository.ActivityRepository;
import ec.edu.espe.practicas.repository.AssignmentRepository;
import ec.edu.espe.practicas.repository.AttendanceRepository;
import ec.edu.espe.practicas.repository.LaboratoryRepository;
import ec.edu.espe.practicas.repository.UserRoleRepository;
import ec.edu.espe.practicas.service.exception.BusinessRuleViolationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, timeout = 45)
public class PracticeReportService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final AuthenticatedUserService authenticatedUserService;
    private final UserRoleRepository userRoleRepository;
    private final AssignmentRepository assignmentRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final AttendanceRepository attendanceRepository;
    private final ActivityRepository activityRepository;

    public PracticeReportService(
            AuthenticatedUserService authenticatedUserService,
            UserRoleRepository userRoleRepository,
            AssignmentRepository assignmentRepository,
            LaboratoryRepository laboratoryRepository,
            AttendanceRepository attendanceRepository,
            ActivityRepository activityRepository) {
        this.authenticatedUserService = authenticatedUserService;
        this.userRoleRepository = userRoleRepository;
        this.assignmentRepository = assignmentRepository;
        this.laboratoryRepository = laboratoryRepository;
        this.attendanceRepository = attendanceRepository;
        this.activityRepository = activityRepository;
    }

    public ReportScopeResponse scope(Authentication authentication, Long laboratoryId) {
        User requester = authenticatedUserService.requireAuthenticatedUser(authentication);
        List<UserRole> roles = userRoleRepository.findByUserId(requester.getId());
        boolean generalAdmin = roles.stream().anyMatch(role -> role.getRole().getName() == RoleName.ADMIN_GENERAL
                && role.getLaboratory() == null);
        List<Laboratory> laboratories = new ArrayList<>();
        List<ReportTraineeOption> trainees = new ArrayList<>();
        String role;
        boolean canChooseLaboratory = false;
        boolean canChooseTrainee = false;

        if (generalAdmin) {
            role = RoleName.ADMIN_GENERAL.name();
            canChooseLaboratory = true;
            laboratories = laboratoryRepository.findAll(Sort.by(Sort.Order.asc("name")));
            validateSelectedLaboratory(laboratoryId, laboratories);
            if (laboratoryId == null) {
                for (UserRole traineeRole : userRoleRepository.findByRoleName(RoleName.PRACTICANTE)) {
                    if (active(traineeRole.getUser())) {
                        trainees.add(toOption(traineeRole));
                    }
                }
            } else {
                trainees.addAll(traineeOptionsForLaboratory(laboratoryId));
            }
            canChooseTrainee = true;
        } else {
            List<Laboratory> assignedLaboratories = roles.stream()
                    .filter(userRole -> userRole.getRole().getName() == RoleName.ADMIN_LABORATORIO)
                    .map(UserRole::getLaboratory)
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toMap(Laboratory::getId, lab -> lab,
                            (first, ignored) -> first, LinkedHashMap::new))
                    .values().stream().sorted(Comparator.comparing(Laboratory::getName)).toList();
            if (!assignedLaboratories.isEmpty()) {
                role = RoleName.ADMIN_LABORATORIO.name();
                canChooseLaboratory = true;
                laboratories = assignedLaboratories;
                validateSelectedLaboratory(laboratoryId, laboratories);
                List<Laboratory> selected = laboratoryId == null
                        ? laboratories
                        : laboratories.stream().filter(lab -> lab.getId().equals(laboratoryId)).toList();
                for (Laboratory lab : selected) {
                    trainees.addAll(traineeOptionsForLaboratory(lab.getId()));
                }
                canChooseTrainee = true;
            } else if (roles.stream().anyMatch(userRole -> userRole.getRole().getName() == RoleName.TUTOR_ESPE)) {
                role = RoleName.TUTOR_ESPE.name();
                List<Assignment> assignments = assignmentRepository.findByTutorIdAndCurrent(requester.getId(), 1);
                if (laboratoryId != null && assignments.stream().noneMatch(a -> a.getLaboratory().getId().equals(laboratoryId))) {
                    throw new AccessDeniedException("No tienes practicantes asignados en ese laboratorio.");
                }
                for (Assignment assignment : assignments) {
                    if ((laboratoryId == null || assignment.getLaboratory().getId().equals(laboratoryId))
                            && active(assignment.getTrainee())) {
                        trainees.add(ReportTraineeOption.of(assignment.getTrainee(),
                                assignment.getLaboratory().getId(), assignment.getLaboratory().getName()));
                    }
                }
                laboratories = assignments.stream().map(Assignment::getLaboratory).distinct()
                        .sorted(Comparator.comparing(Laboratory::getName)).toList();
                canChooseTrainee = true;
            } else if (roles.stream().anyMatch(userRole -> userRole.getRole().getName() == RoleName.PRACTICANTE)) {
                role = RoleName.PRACTICANTE.name();
                UserRole traineeRole = roles.stream()
                        .filter(userRole -> userRole.getRole().getName() == RoleName.PRACTICANTE)
                        .findFirst().orElseThrow();
                trainees.add(toOption(traineeRole));
                if (traineeRole.getLaboratory() != null) {
                    laboratories = List.of(traineeRole.getLaboratory());
                }
                if (laboratoryId != null && (traineeRole.getLaboratory() == null
                        || !traineeRole.getLaboratory().getId().equals(laboratoryId))) {
                    throw new AccessDeniedException("Solo puedes consultar tus propios registros.");
                }
            } else {
                throw new AccessDeniedException("No tienes un rol habilitado para consultar reportes.");
            }
        }

        trainees = trainees.stream().collect(java.util.stream.Collectors.toMap(
                        ReportTraineeOption::id, option -> option, (first, ignored) -> first, LinkedHashMap::new))
                .values().stream().sorted(Comparator.comparing(ReportTraineeOption::name,
                        String.CASE_INSENSITIVE_ORDER)).toList();
        return new ReportScopeResponse(role,
                laboratories.stream().map(LaboratorySummaryResponse::from).toList(), trainees,
                canChooseLaboratory, canChooseTrainee);
    }

    public PracticeReportResponse report(
            Authentication authentication,
            LocalDate from,
            LocalDate to,
            Long laboratoryId,
            Long traineeId,
            int page,
            int size) {
        validateDates(from, to);
        ReportScopeResponse scope = scope(authentication, laboratoryId);
        if (traineeId != null && scope.trainees().stream().noneMatch(option -> option.id().equals(traineeId))) {
            throw new AccessDeniedException("No tienes permiso para consultar los registros de ese practicante.");
        }
        List<ReportTraineeOption> selectedTrainees = scope.trainees().stream()
                .filter(option -> traineeId == null || option.id().equals(traineeId))
                .toList();
        List<Long> traineeIds = selectedTrainees.stream().map(ReportTraineeOption::id).toList();
        if (traineeIds.isEmpty()) {
            return new PracticeReportResponse(new PagedResponse<>(List.of(), 0, size, 0, 0, true, true),
                    0, ZERO, ZERO);
        }

        Map<Long, ReportTraineeOption> optionsByTrainee = new LinkedHashMap<>();
        selectedTrainees.forEach(option -> optionsByTrainee.put(option.id(), option));
        Map<ReportKey, RowAccumulator> grouped = new TreeMap<>();
        int loadedRecords = 0;
        for (int offset = 0; offset < traineeIds.size(); offset += 500) {
            List<Long> chunk = traineeIds.subList(offset, Math.min(offset + 500, traineeIds.size()));
            var attendanceRows = attendanceRepository.findByTraineeIdInAndDateBetweenOrderByDateAsc(
                    chunk, from, to, org.springframework.data.domain.Limit.of(10001));
            var activityRows = activityRepository.findByTraineeIdInAndDateGreaterThanEqualAndDateLessThanOrderByDateAscIdAsc(
                    chunk, from, to.plusDays(1), org.springframework.data.domain.Limit.of(10001));
            loadedRecords += attendanceRows.size() + activityRows.size();
            if (loadedRecords > 10000) {
                throw new BusinessRuleViolationException("La consulta contiene demasiados registros. Reduce el rango de fechas o selecciona un practicante.");
            }
            attendanceRows
                    .forEach(attendance -> grouped.computeIfAbsent(
                            new ReportKey(attendance.getTrainee().getId(), attendance.getDate()),
                            key -> new RowAccumulator(optionsByTrainee.get(key.traineeId()))).attendance(attendance));
            activityRows
                    .forEach(activity -> grouped.computeIfAbsent(
                            new ReportKey(activity.getTrainee().getId(), activity.getDate()),
                            key -> new RowAccumulator(optionsByTrainee.get(key.traineeId()))).activity(activity));
        }

        List<PracticeReportRow> allRows = grouped.entrySet().stream()
                .map(entry -> entry.getValue().toResponse(entry.getKey().date()))
                .sorted(Comparator.comparing(PracticeReportRow::date, Comparator.reverseOrder())
                        .thenComparing(PracticeReportRow::traineeName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(PracticeReportRow::traineeId))
                .toList();
        int safeSize = Math.max(1, Math.min(size, 100));
        int pageCount = allRows.isEmpty() ? 0 : (int) Math.ceil((double) allRows.size() / safeSize);
        int safePage = Math.max(0, Math.min(page, Math.max(0, pageCount - 1)));
        int start = safePage * safeSize;
        List<PracticeReportRow> pageRows = allRows.subList(start, Math.min(start + safeSize, allRows.size()));
        PagedResponse<PracticeReportRow> pageResponse = PagedResponse.from(
                new PageImpl<>(pageRows, PageRequest.of(safePage, safeSize,
                        Sort.by(Sort.Order.desc("date"), Sort.Order.asc("traineeName"))), allRows.size()),
                row -> row);
        BigDecimal attendanceHours = allRows.stream().map(PracticeReportRow::attendanceHours)
                .reduce(ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal acceptedHours = allRows.stream().map(PracticeReportRow::acceptedActivityHours)
                .reduce(ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        long attendanceDays = allRows.stream().filter(row -> row.checkIn() != null).count();
        return new PracticeReportResponse(pageResponse, attendanceDays, attendanceHours, acceptedHours);
    }

    private List<ReportTraineeOption> traineeOptionsForLaboratory(Long laboratoryId) {
        return userRoleRepository.findByRoleNameAndLaboratoryId(RoleName.PRACTICANTE, laboratoryId).stream()
                .filter(role -> active(role.getUser())).map(this::toOption).toList();
    }

    private ReportTraineeOption toOption(UserRole role) {
        Laboratory laboratory = role.getLaboratory();
        return ReportTraineeOption.of(role.getUser(), laboratory == null ? null : laboratory.getId(),
                laboratory == null ? "" : laboratory.getName());
    }

    private boolean active(User user) {
        return Integer.valueOf(1).equals(user.getActive());
    }

    private void validateSelectedLaboratory(Long laboratoryId, List<Laboratory> authorizedLaboratories) {
        if (laboratoryId == null) return;
        if (authorizedLaboratories.stream().noneMatch(lab -> lab.getId().equals(laboratoryId))) {
            throw new AccessDeniedException("No tienes permiso para consultar ese laboratorio.");
        }
    }

    private void validateDates(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new BusinessRuleViolationException("El rango de fechas no es válido.");
        }
        if (from.plusDays(366).isBefore(to)) {
            throw new BusinessRuleViolationException("El rango de consulta no puede superar 366 días.");
        }
    }

    private record ReportKey(Long traineeId, LocalDate date) implements Comparable<ReportKey> {
        @Override
        public int compareTo(ReportKey other) {
            int byDate = other.date.compareTo(date);
            return byDate != 0 ? byDate : traineeId.compareTo(other.traineeId);
        }
    }

    private static final class RowAccumulator {
        private final ReportTraineeOption trainee;
        private Attendance attendance;
        private final List<Activity> activities = new ArrayList<>();

        private RowAccumulator(ReportTraineeOption trainee) { this.trainee = trainee; }
        private RowAccumulator attendance(Attendance attendance) { this.attendance = attendance; return this; }
        private RowAccumulator activity(Activity activity) { activities.add(activity); return this; }

        private PracticeReportRow toResponse(LocalDate date) {
            LocalDateTimeValues times = attendance == null
                    ? new LocalDateTimeValues(null, null, ZERO)
                    : new LocalDateTimeValues(attendance.getCheckIn(), attendance.getCheckOut(),
                            duration(attendance));
            BigDecimal accepted = activities.stream()
                    .filter(activity -> activity.getStatus() == ActivityStatus.ACEPTADA)
                    .map(Activity::getDurationHours).reduce(ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            return new PracticeReportRow(trainee.id(), trainee.name(), trainee.email(), trainee.laboratoryName(),
                    date, times.checkIn(), times.checkOut(), times.hours(), accepted,
                    activities.stream().map(ReportActivityRow::from).toList());
        }

        private static BigDecimal duration(Attendance attendance) {
            if (attendance.getCheckIn() == null || attendance.getCheckOut() == null) return ZERO;
            long seconds = Math.max(0, Duration.between(attendance.getCheckIn(), attendance.getCheckOut()).getSeconds());
            return BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP);
        }
    }

    private record LocalDateTimeValues(
            java.time.LocalDateTime checkIn, java.time.LocalDateTime checkOut, BigDecimal hours) { }
}
