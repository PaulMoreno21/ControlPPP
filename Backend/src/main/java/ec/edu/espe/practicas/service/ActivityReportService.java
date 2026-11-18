package ec.edu.espe.practicas.service;

import ec.edu.espe.practicas.model.Activity;
import ec.edu.espe.practicas.model.ActivityStatus;
import ec.edu.espe.practicas.model.AuditLog;
import ec.edu.espe.practicas.model.RoleName;
import ec.edu.espe.practicas.model.User;
import ec.edu.espe.practicas.repository.ActivityRepository;
import ec.edu.espe.practicas.repository.AuditLogRepository;
import ec.edu.espe.practicas.repository.AttendanceRepository;
import ec.edu.espe.practicas.repository.UserRepository;
import ec.edu.espe.practicas.repository.UserRoleRepository;
import ec.edu.espe.practicas.service.exception.BusinessRuleViolationException;
import ec.edu.espe.practicas.service.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ActivityReportService {
    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final AuthenticatedUserService authenticatedUserService;
    private final ActivityRepository activityRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public ActivityReportService(
            AuthenticatedUserService authenticatedUserService,
            ActivityRepository activityRepository,
            AttendanceRepository attendanceRepository,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            AuditLogRepository auditLogRepository,
            Clock clock) {
        this.authenticatedUserService = authenticatedUserService;
        this.activityRepository = activityRepository;
        this.attendanceRepository = attendanceRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true, timeout = 45)
    public BitacoraExportData getAcceptedActivities(
            Authentication authentication, Long traineeId, LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new BusinessRuleViolationException("El rango de fechas del reporte no es válido.");
        }
        if (from.plusDays(366).isBefore(to)) {
            throw new BusinessRuleViolationException("Selecciona un rango de hasta 366 dias para exportar.");
        }
        User requester = authenticatedUserService.requireAuthenticatedUser(authentication);
        authorizeReport(requester, traineeId);
        User trainee = userRepository.findById(traineeId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el practicante indicado."));

        List<Activity> acceptedActivities = activityRepository
                .findByTraineeIdAndDateGreaterThanEqualAndDateLessThanAndStatusOrderByDateAscIdAsc(
                        traineeId, from, to.plusDays(1), ActivityStatus.ACEPTADA,
                        org.springframework.data.domain.Limit.of(10001));
        if (acceptedActivities.size() > 10000) {
            throw new BusinessRuleViolationException("La bitacora contiene demasiadas actividades. Reduce el rango de fechas.");
        }
        Map<LocalDate, String[]> attendanceByDate = attendanceRepository
                .findByTraineeIdAndDateBetweenOrderByDateAsc(traineeId, from, to).stream()
                .collect(Collectors.toMap(
                        attendance -> attendance.getDate(),
                        attendance -> new String[] {
                                attendance.getCheckIn() == null ? "" : attendance.getCheckIn().format(DISPLAY_DATE_TIME),
                                attendance.getCheckOut() == null ? "" : attendance.getCheckOut().format(DISPLAY_DATE_TIME)
                        },
                        (first, ignored) -> first));

        String traineeName = trainee.getFirstName() + " " + trainee.getLastName();
        List<BitacoraExportData.ActivityRow> rows = acceptedActivities.stream()
                .map(activity -> {
                    String[] attendance = attendanceByDate.getOrDefault(activity.getDate(), new String[] {"", ""});
                    return new BitacoraExportData.ActivityRow(
                            activity.getDate(), traineeName, activity.getDescription(),
                            activity.getDurationHours(), attendance[0], attendance[1]);
                })
                .toList();
        return new BitacoraExportData(traineeId, traineeName, from, to, rows);
    }

    public void recordExport(Authentication authentication, Long traineeId, LocalDate from, LocalDate to, String format) {
        User requester = authenticatedUserService.requireAuthenticatedUser(authentication);
        authorizeReport(requester, traineeId);
        auditLogRepository.save(AuditLog.record(
                requester,
                "EXPORTAR_BITACORA_" + format.toUpperCase(),
                "BITACORA",
                traineeId,
                LocalDateTime.now(clock),
                AuditMessages.export(AuditMessages.name(requester), from, to, format)));
    }

    private void authorizeReport(User requester, Long traineeId) {
        boolean isTrainee = userRoleRepository.findByUserId(requester.getId()).stream()
                .anyMatch(role -> role.getRole().getName() == RoleName.PRACTICANTE);
        if (!isTrainee || !requester.getId().equals(traineeId)) {
            throw new AccessDeniedException("Solo los practicantes pueden exportar su propia bitacora.");
        }
    }
}
