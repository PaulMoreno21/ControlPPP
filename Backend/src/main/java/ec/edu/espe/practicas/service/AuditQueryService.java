package ec.edu.espe.practicas.service;

import ec.edu.espe.practicas.dto.AuditLogResponse;
import ec.edu.espe.practicas.dto.PagedResponse;
import ec.edu.espe.practicas.model.Activity;
import ec.edu.espe.practicas.model.AuditLog;
import ec.edu.espe.practicas.model.RoleName;
import ec.edu.espe.practicas.repository.ActivityRepository;
import ec.edu.espe.practicas.repository.AuditLogRepository;
import ec.edu.espe.practicas.service.exception.BusinessRuleViolationException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuditQueryService {
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final AuthenticatedUserService authenticatedUserService;
    private final AuditLogRepository auditLogRepository;
    private final ActivityRepository activityRepository;
    private final AuditDetailsFormatter detailsFormatter;

    public AuditQueryService(
            AuthenticatedUserService authenticatedUserService,
            AuditLogRepository auditLogRepository,
            ActivityRepository activityRepository,
            AuditDetailsFormatter detailsFormatter) {
        this.authenticatedUserService = authenticatedUserService;
        this.auditLogRepository = auditLogRepository;
        this.activityRepository = activityRepository;
        this.detailsFormatter = detailsFormatter;
    }

    public PagedResponse<AuditLogResponse> listPage(
            Authentication authentication, LocalDate from, LocalDate to, int page, int size) {
        authenticatedUserService.requireGlobalRole(authentication, RoleName.ADMIN_GENERAL);
        validateRange(from, to);
        var pageable = PageableFactory.create(page, size,
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")));
        var logs = auditLogRepository.findByOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay(), pageable);
        List<Long> activityIds = logs.getContent().stream()
                .filter(log -> "TCWACTIVIDADES".equals(log.getEntityName()))
                .map(AuditLog::getEntityId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Activity> activities = activityRepository.findAllById(activityIds).stream()
                .collect(Collectors.toMap(Activity::getId, activity -> activity));
        Map<AuditLog, String> readableDetails = detailsFormatter.formatPage(logs.getContent());
        return PagedResponse.from(logs, log -> AuditLogResponse.from(log,
                "TCWACTIVIDADES".equals(log.getEntityName())
                        ? activityDetails(log, activities.get(log.getEntityId())) : readableDetails.get(log)));
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new BusinessRuleViolationException("El rango de fechas de auditoría no es válido.");
        }
    }

    private String activityDetails(AuditLog log, Activity activity) {
        if (!"TCWACTIVIDADES".equals(log.getEntityName())) return log.getDetails();
        String savedDetails = log.getDetails();
        if (savedDetails != null
                && (savedDetails.startsWith("El practicante ") || savedDetails.startsWith("El tutor "))) {
            return savedDetails;
        }
        String actor = log.getUser().getFirstName() + " " + log.getUser().getLastName();
        if (activity == null) {
            return switch (log.getAction()) {
                case "ELIMINAR_ACTIVIDAD" -> "El practicante " + actor + " eliminó una actividad.";
                default -> "Se registró una acción sobre una actividad que ya no está disponible.";
            };
        }

        String trainee = activity.getTrainee().getFirstName() + " " + activity.getTrainee().getLastName();
        String description = "la actividad «" + activity.getDescription() + "» del "
                + activity.getDate().format(DISPLAY_DATE);
        return switch (log.getAction()) {
            case "CREAR_ACTIVIDAD" -> "El practicante " + actor + " registró " + description + ".";
            case "EDITAR_ACTIVIDAD" -> "El practicante " + actor + " actualizó " + description + ".";
            case "ELIMINAR_ACTIVIDAD" -> "El practicante " + actor + " eliminó " + description + ".";
            case "ENVIAR_ACTIVIDAD" -> "El practicante " + actor + " envió a revisión " + description + ".";
            case "ACEPTAR_ACTIVIDAD" -> "El tutor " + actor + " aceptó " + description
                    + " enviada por el practicante " + trainee + ".";
            case "RECHAZAR_ACTIVIDAD" -> "El tutor " + actor + " rechazó " + description
                    + " enviada por el practicante " + trainee + ".";
            default -> log.getDetails();
        };
    }
}
