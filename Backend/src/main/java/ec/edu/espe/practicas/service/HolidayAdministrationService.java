package ec.edu.espe.practicas.service;

import ec.edu.espe.practicas.dto.HolidayRequest;
import ec.edu.espe.practicas.dto.HolidayResponse;
import ec.edu.espe.practicas.dto.PagedResponse;
import ec.edu.espe.practicas.model.AuditLog;
import ec.edu.espe.practicas.model.Holiday;
import ec.edu.espe.practicas.model.RoleName;
import ec.edu.espe.practicas.model.User;
import ec.edu.espe.practicas.repository.AuditLogRepository;
import ec.edu.espe.practicas.repository.HolidayRepository;
import ec.edu.espe.practicas.service.exception.BusinessRuleViolationException;
import ec.edu.espe.practicas.service.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class HolidayAdministrationService {
    private final HolidayQueryCache holidayQueryCache;
    private final AuthenticatedUserService authenticatedUserService;
    private final HolidayRepository holidayRepository;
    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public HolidayAdministrationService(
            HolidayQueryCache holidayQueryCache,
            AuthenticatedUserService authenticatedUserService,
            HolidayRepository holidayRepository,
            AuditLogRepository auditLogRepository,
            Clock clock) {
        this.holidayQueryCache = holidayQueryCache;
        this.authenticatedUserService = authenticatedUserService;
        this.holidayRepository = holidayRepository;
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<HolidayResponse> listHolidays(
            Authentication authentication, LocalDate from, LocalDate to) {
        authenticatedUserService.requireGlobalRole(authentication, RoleName.ADMIN_GENERAL);
        if (from == null || to == null || from.isAfter(to)) {
            throw new BusinessRuleViolationException("El rango de fechas de feriados no es válido.");
        }
        return holidayQueryCache.list(from, to);
    }

    @Transactional(readOnly = true)
    public PagedResponse<HolidayResponse> listHolidaysPage(
            Authentication authentication, LocalDate from, LocalDate to, int page, int size) {
        authenticatedUserService.requireGlobalRole(authentication, RoleName.ADMIN_GENERAL);
        if (from == null || to == null || from.isAfter(to)) {
            throw new BusinessRuleViolationException("El rango de fechas de feriados no es válido.");
        }
        return holidayQueryCache.page(from, to, Math.max(0, page), Math.max(1, Math.min(size, 100)));
    }

    @org.springframework.cache.annotation.CacheEvict(cacheNames = "holidayRanges", allEntries = true)
    public HolidayResponse createHoliday(Authentication authentication, HolidayRequest request) {
        User administrator = authenticatedUserService
                .requireGlobalRole(authentication, RoleName.ADMIN_GENERAL);
        if (holidayRepository.findByDate(request.date()).isPresent()) {
            throw new BusinessRuleViolationException("Ya existe un feriado registrado para esa fecha.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Holiday holiday = holidayRepository.save(Holiday.create(
                request.date(), request.description().trim(), administrator, now));
        auditLogRepository.save(AuditLog.record(
                administrator,
                "CREAR_FERIADO",
                "TCWFERIADOS",
                holiday.getId(),
                now,
                AuditMessages.holiday("CREAR_FERIADO", holiday.getDate(), holiday.getDescription())));
        return HolidayResponse.from(holiday);
    }

    @org.springframework.cache.annotation.CacheEvict(cacheNames = "holidayRanges", allEntries = true)
    public HolidayResponse updateHoliday(Authentication authentication, Long holidayId, HolidayRequest request) {
        User administrator = authenticatedUserService.requireGlobalRole(authentication, RoleName.ADMIN_GENERAL);
        Holiday holiday = holidayRepository.findById(holidayId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el feriado indicado."));
        if (holidayRepository.findByDate(request.date())
                .filter(existing -> !existing.getId().equals(holidayId)).isPresent()) {
            throw new BusinessRuleViolationException("Ya existe un feriado registrado para esa fecha.");
        }
        LocalDate previousDate = holiday.getDate();
        String previousDescription = holiday.getDescription();
        String description = request.description().trim();
        if (previousDate.equals(request.date()) && previousDescription.equals(description)) {
            return HolidayResponse.from(holiday);
        }
        holiday.setDate(request.date());
        holiday.setDescription(description);
        Holiday saved = holidayRepository.save(holiday);
        auditLogRepository.save(AuditLog.record(administrator, "EDITAR_FERIADO", "TCWFERIADOS", holidayId,
                LocalDateTime.now(clock), AuditMessages.holidayEdit(previousDate, previousDescription,
                        saved.getDate(), saved.getDescription())));
        return HolidayResponse.from(saved);
    }

    @org.springframework.cache.annotation.CacheEvict(cacheNames = "holidayRanges", allEntries = true)
    public HolidayResponse updateHolidayStatus(
            Authentication authentication, Long holidayId, Boolean active) {
        User administrator = authenticatedUserService
                .requireGlobalRole(authentication, RoleName.ADMIN_GENERAL);
        Holiday holiday = holidayRepository.findById(holidayId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el feriado indicado."));

        int newStatus = Boolean.TRUE.equals(active) ? 1 : 0;
        int previousStatus = holiday.getActive();
        if (previousStatus == newStatus) {
            return HolidayResponse.from(holiday);
        }

        holiday.setActive(newStatus);
        holidayRepository.save(holiday);
        LocalDateTime now = LocalDateTime.now(clock);
        auditLogRepository.save(AuditLog.record(
                administrator,
                newStatus == 1 ? "ACTIVAR_FERIADO" : "DESACTIVAR_FERIADO",
                "TCWFERIADOS",
                holiday.getId(),
                now,
                AuditMessages.holiday(newStatus == 1 ? "ACTIVAR_FERIADO" : "DESACTIVAR_FERIADO",
                        holiday.getDate(), holiday.getDescription())));
        return HolidayResponse.from(holiday);
    }

    @org.springframework.cache.annotation.CacheEvict(cacheNames = "holidayRanges", allEntries = true)
    public void deleteHoliday(Authentication authentication, Long holidayId) {
        User administrator = authenticatedUserService
                .requireGlobalRole(authentication, RoleName.ADMIN_GENERAL);
        Holiday holiday = holidayRepository.findById(holidayId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el feriado indicado."));

        LocalDate date = holiday.getDate();
        String description = holiday.getDescription();
        LocalDateTime now = LocalDateTime.now(clock);
        auditLogRepository.save(AuditLog.record(
                administrator,
                "ELIMINAR_FERIADO",
                "TCWFERIADOS",
                holiday.getId(),
                now,
                AuditMessages.holiday("ELIMINAR_FERIADO", date, description)));
        holidayRepository.delete(holiday);
    }
}
