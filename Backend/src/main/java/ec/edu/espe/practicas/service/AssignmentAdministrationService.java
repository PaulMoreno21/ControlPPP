package ec.edu.espe.practicas.service;

import ec.edu.espe.practicas.dto.AssignmentRequest;
import ec.edu.espe.practicas.dto.AssignmentResponse;
import ec.edu.espe.practicas.dto.PagedResponse;
import ec.edu.espe.practicas.model.Assignment;
import ec.edu.espe.practicas.model.AuditLog;
import ec.edu.espe.practicas.model.RoleName;
import ec.edu.espe.practicas.model.User;
import ec.edu.espe.practicas.repository.AssignmentRepository;
import ec.edu.espe.practicas.repository.AuditLogRepository;
import ec.edu.espe.practicas.repository.LaboratoryRepository;
import ec.edu.espe.practicas.repository.UserRepository;
import ec.edu.espe.practicas.repository.UserRoleRepository;
import ec.edu.espe.practicas.service.exception.BusinessRuleViolationException;
import ec.edu.espe.practicas.service.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AssignmentAdministrationService {
    private static final Integer CURRENT = 1;
    private static final Integer HISTORICAL = 0;

    private final AuthenticatedUserService authenticatedUserService;
    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public AssignmentAdministrationService(
            AuthenticatedUserService authenticatedUserService,
            AssignmentRepository assignmentRepository,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            LaboratoryRepository laboratoryRepository,
            AuditLogRepository auditLogRepository,
            Clock clock) {
        this.authenticatedUserService = authenticatedUserService;
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.laboratoryRepository = laboratoryRepository;
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> listAssignments(Authentication authentication, Long laboratoryId) {
        authenticatedUserService.requireAdministratorForLaboratory(authentication, laboratoryId);
        if (!laboratoryRepository.existsById(laboratoryId)) {
            throw new ResourceNotFoundException("No existe el laboratorio indicado.");
        }
        return assignmentRepository.findByLaboratoryIdOrderByAssignedAtDesc(laboratoryId).stream()
                .map(AssignmentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PagedResponse<AssignmentResponse> listAssignmentsPage(
            Authentication authentication, Long laboratoryId, String search, int page, int size) {
        authenticatedUserService.requireAdministratorForLaboratory(authentication, laboratoryId);
        if (!laboratoryRepository.existsById(laboratoryId)) {
            throw new ResourceNotFoundException("No existe el laboratorio indicado.");
        }
        String normalizedSearch = search == null || search.isBlank() ? null : "%" + search.trim() + "%";
        return PagedResponse.from(assignmentRepository.findActiveByLaboratoryIdAndTutorSearch(
                laboratoryId, normalizedSearch,
                PageableFactory.create(page, size,
                        Sort.by(Sort.Order.desc("assignedAt"), Sort.Order.desc("id")))),
                AssignmentResponse::from);
    }

    public AssignmentResponse assignTutor(
            Authentication authentication, Long laboratoryId, AssignmentRequest request) {
        User administrator = authenticatedUserService
                .requireAdministratorForLaboratory(authentication, laboratoryId);
        var laboratory = laboratoryRepository.findById(laboratoryId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el laboratorio indicado."));
        User trainee = findActiveUserForUpdate(request.traineeId(), "practicante");
        User tutor = findActiveUser(request.tutorId(), "tutor");
        requireRoleInLaboratory(trainee.getId(), laboratoryId, RoleName.PRACTICANTE, "practicante");
        requireRoleInLaboratory(tutor.getId(), laboratoryId, RoleName.TUTOR_ESPE, "tutor");

        var currentAssignment = assignmentRepository.findByTraineeIdAndCurrent(trainee.getId(), CURRENT);
        if (currentAssignment.isPresent()) {
            Assignment previous = currentAssignment.get();
            if (previous.getLaboratory().getId().equals(laboratoryId)
                    && previous.getTutor().getId().equals(tutor.getId())) {
                return AssignmentResponse.from(previous);
            }
            if (!previous.getLaboratory().getId().equals(laboratoryId)
                    && !authenticatedUserService.hasGlobalRole(administrator, RoleName.ADMIN_GENERAL)) {
                throw new AccessDeniedException(
                        "Un administrador de laboratorio no puede mover una asignación desde otro laboratorio.");
            }
            previous.setCurrent(HISTORICAL);
            assignmentRepository.save(previous);
        }

        LocalDateTime assignedAt = LocalDateTime.now(clock);
        Assignment assignment = assignmentRepository.save(Assignment.create(
                trainee, tutor, administrator, laboratory, assignedAt));
        String action = currentAssignment.isPresent() ? "REASIGNAR_TUTOR" : "ASIGNAR_TUTOR";
        auditLogRepository.save(AuditLog.record(
                administrator, action, "TCWASIGNACIONES", assignment.getId(), assignedAt,
                AuditMessages.assignment(action, AuditMessages.name(trainee), AuditMessages.name(tutor),
                        laboratory.getName())));
        return AssignmentResponse.from(assignment);
    }

    public void unlinkTutor(Authentication authentication, Long laboratoryId, Long assignmentId) {
        User administrator = authenticatedUserService
                .requireAdministratorForLaboratory(authentication, laboratoryId);
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe la asignación indicada."));
        if (!assignment.getLaboratory().getId().equals(laboratoryId)) {
            throw new ResourceNotFoundException("No existe la asignación en el laboratorio indicado.");
        }
        findActiveUserForUpdate(assignment.getTrainee().getId(), "practicante");
        assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe la asignación indicada."));
        if (!Integer.valueOf(CURRENT).equals(assignment.getCurrent())) {
            throw new BusinessRuleViolationException("La asignación ya no está vigente.");
        }

        assignment.setCurrent(HISTORICAL);
        assignmentRepository.save(assignment);
        LocalDateTime unlinkedAt = LocalDateTime.now(clock);
        auditLogRepository.save(AuditLog.record(
                administrator, "DESVINCULAR_TUTOR", "TCWASIGNACIONES", assignment.getId(), unlinkedAt,
                AuditMessages.assignment("DESVINCULAR_TUTOR", AuditMessages.name(assignment.getTrainee()),
                        AuditMessages.name(assignment.getTutor()), assignment.getLaboratory().getName())));
    }

    private User findActiveUser(Long userId, String description) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el " + description + " indicado."));
        if (!Integer.valueOf(1).equals(user.getActive())) {
            throw new BusinessRuleViolationException("La cuenta del " + description + " está inactiva.");
        }
        return user;
    }

    private User findActiveUserForUpdate(Long userId, String description) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el " + description + " indicado."));
        if (!Integer.valueOf(1).equals(user.getActive())) {
            throw new BusinessRuleViolationException("La cuenta del " + description + " está inactiva.");
        }
        return user;
    }

    private void requireRoleInLaboratory(
            Long userId, Long laboratoryId, RoleName roleName, String description) {
        boolean hasRole = userRoleRepository.findByUserIdAndLaboratoryId(userId, laboratoryId).stream()
                .anyMatch(userRole -> userRole.getRole().getName() == roleName);
        if (!hasRole) {
            throw new BusinessRuleViolationException(
                    "El " + description + " no tiene el rol requerido en este laboratorio.");
        }
    }
}
