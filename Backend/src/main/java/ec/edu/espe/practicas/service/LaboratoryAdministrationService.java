package ec.edu.espe.practicas.service;

import ec.edu.espe.practicas.dto.LaboratoryRequest;
import ec.edu.espe.practicas.dto.LaboratorySummaryResponse;
import ec.edu.espe.practicas.dto.PagedResponse;
import ec.edu.espe.practicas.model.AuditLog;
import ec.edu.espe.practicas.model.Laboratory;
import ec.edu.espe.practicas.model.RoleName;
import ec.edu.espe.practicas.model.User;
import ec.edu.espe.practicas.repository.AssignmentRepository;
import ec.edu.espe.practicas.repository.AuditLogRepository;
import ec.edu.espe.practicas.repository.LaboratoryRepository;
import ec.edu.espe.practicas.repository.UserRoleRepository;
import ec.edu.espe.practicas.service.exception.BusinessRuleViolationException;
import ec.edu.espe.practicas.service.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LaboratoryAdministrationService {
    private final AuthenticatedUserService authenticatedUserService;
    private final LaboratoryRepository laboratoryRepository;
    private final UserRoleRepository userRoleRepository;
    private final AssignmentRepository assignmentRepository;
    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public LaboratoryAdministrationService(
            AuthenticatedUserService authenticatedUserService,
            LaboratoryRepository laboratoryRepository,
            UserRoleRepository userRoleRepository,
            AssignmentRepository assignmentRepository,
            AuditLogRepository auditLogRepository,
            Clock clock) {
        this.authenticatedUserService = authenticatedUserService;
        this.laboratoryRepository = laboratoryRepository;
        this.userRoleRepository = userRoleRepository;
        this.assignmentRepository = assignmentRepository;
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<LaboratorySummaryResponse> listLaboratories(Authentication authentication) {
        authenticatedUserService.requireGlobalRole(authentication, RoleName.ADMIN_GENERAL);
        return laboratoryRepository.findAll(Sort.by(Sort.Direction.ASC, "name")).stream()
                .map(LaboratorySummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PagedResponse<LaboratorySummaryResponse> listLaboratoriesPage(
            Authentication authentication, int page, int size) {
        authenticatedUserService.requireGlobalRole(authentication, RoleName.ADMIN_GENERAL);
        return PagedResponse.from(
                laboratoryRepository.findAll(PageableFactory.create(page, size,
                        Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")))),
                LaboratorySummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public List<LaboratorySummaryResponse> listAccessibleLaboratories(Authentication authentication) {
        User administrator = authenticatedUserService.requireAuthenticatedUser(authentication);
        if (authenticatedUserService.hasGlobalRole(administrator, RoleName.ADMIN_GENERAL)) {
            return laboratoryRepository.findAll(Sort.by(Sort.Direction.ASC, "name")).stream()
                    .map(LaboratorySummaryResponse::from)
                    .toList();
        }

        List<LaboratorySummaryResponse> laboratories = userRoleRepository.findByUserId(administrator.getId()).stream()
                .filter(userRole -> userRole.getRole().getName() == RoleName.ADMIN_LABORATORIO)
                .map(userRole -> userRole.getLaboratory())
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparing(Laboratory::getName))
                .map(LaboratorySummaryResponse::from)
                .toList();
        if (laboratories.isEmpty()) {
            throw new AccessDeniedException("Se requiere el rol ADMIN_LABORATORIO o ADMIN_GENERAL.");
        }
        return laboratories;
    }

    public LaboratorySummaryResponse createLaboratory(
            Authentication authentication, LaboratoryRequest request) {
        User administrator = authenticatedUserService
                .requireGlobalRole(authentication, RoleName.ADMIN_GENERAL);
        String name = request.name().trim();
        String location = normalizeLocation(request.location());
        if (laboratoryRepository.existsByNameIgnoreCase(name)) {
            throw new BusinessRuleViolationException("Ya existe un laboratorio con ese nombre.");
        }

        Laboratory laboratory = laboratoryRepository.save(Laboratory.create(name, location));
        LocalDateTime now = LocalDateTime.now(clock);
        auditLogRepository.save(AuditLog.record(
                administrator, "CREAR_LABORATORIO", "TCWLABORATORIOS", laboratory.getId(), now,
                AuditMessages.laboratory("CREAR_LABORATORIO", name, location)));
        return LaboratorySummaryResponse.from(laboratory);
    }

    public LaboratorySummaryResponse updateLaboratory(
            Authentication authentication, Long laboratoryId, LaboratoryRequest request) {
        User administrator = authenticatedUserService
                .requireGlobalRole(authentication, RoleName.ADMIN_GENERAL);
        Laboratory laboratory = findLaboratory(laboratoryId);
        String name = request.name().trim();
        String location = normalizeLocation(request.location());
        if (laboratoryRepository.existsByNameIgnoreCaseAndIdNot(name, laboratoryId)) {
            throw new BusinessRuleViolationException("Ya existe otro laboratorio con ese nombre.");
        }

        String previousName = laboratory.getName();
        String previousLocation = laboratory.getLocation();
        laboratory.setName(name);
        laboratory.setLocation(location);
        Laboratory savedLaboratory = laboratoryRepository.save(laboratory);
        auditLogRepository.save(AuditLog.record(
                administrator, "EDITAR_LABORATORIO", "TCWLABORATORIOS", laboratoryId,
                LocalDateTime.now(clock), AuditMessages.laboratoryEdit(previousName, previousLocation, name, location)));
        return LaboratorySummaryResponse.from(savedLaboratory);
    }

    public void deleteLaboratory(Authentication authentication, Long laboratoryId) {
        User administrator = authenticatedUserService
                .requireGlobalRole(authentication, RoleName.ADMIN_GENERAL);
        Laboratory laboratory = findLaboratory(laboratoryId);
        if (userRoleRepository.existsByLaboratoryId(laboratoryId)
                || assignmentRepository.existsByLaboratoryId(laboratoryId)) {
            throw new BusinessRuleViolationException(
                    "No se puede eliminar el laboratorio porque tiene usuarios o asignaciones asociadas.");
        }

        auditLogRepository.save(AuditLog.record(
                administrator, "ELIMINAR_LABORATORIO", "TCWLABORATORIOS", laboratoryId,
                LocalDateTime.now(clock), AuditMessages.laboratory("ELIMINAR_LABORATORIO",
                        laboratory.getName(), laboratory.getLocation())));
        laboratoryRepository.delete(laboratory);
    }

    private Laboratory findLaboratory(Long laboratoryId) {
        return laboratoryRepository.findById(laboratoryId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el laboratorio indicado."));
    }

    private String normalizeLocation(String location) {
        return location == null || location.isBlank() ? null : location.trim();
    }
}
