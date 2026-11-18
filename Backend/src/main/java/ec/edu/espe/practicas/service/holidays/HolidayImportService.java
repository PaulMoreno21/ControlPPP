package ec.edu.espe.practicas.service.holidays;

import ec.edu.espe.practicas.model.AuditLog;
import ec.edu.espe.practicas.model.Holiday;
import ec.edu.espe.practicas.model.RoleName;
import ec.edu.espe.practicas.repository.AuditLogRepository;
import ec.edu.espe.practicas.repository.HolidayRepository;
import ec.edu.espe.practicas.repository.UserRepository;
import ec.edu.espe.practicas.repository.UserRoleRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.HashSet;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HolidayImportService {
    public static final String COMPLETED_ACTION = "PRECARGA_ANUAL_FERIADOS";
    private final HolidayRepository holidays;
    private final UserRepository users;
    private final UserRoleRepository roles;
    private final AuditLogRepository audits;
    private final Clock clock;

    public HolidayImportService(HolidayRepository holidays, UserRepository users,
            UserRoleRepository roles, AuditLogRepository audits, Clock clock) {
        this.holidays = holidays;
        this.users = users;
        this.roles = roles;
        this.audits = audits;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<LocalDateTime> completion(int year) {
        return audits.findFirstByActionAndEntityIdOrderByIdDesc(COMPLETED_ACTION, (long) year)
                .map(AuditLog::getOccurredAt);
    }

    /** Completion marker and holidays commit together, so restarts never undo manual corrections. */
    @Transactional
    @CacheEvict(cacheNames = "holidayRanges", allEntries = true)
    public int importYear(int year, List<NationalHoliday> incoming, long administratorId) {
        var owner = users.findByIdForUpdate(administratorId)
                .orElseThrow(HolidayImportService::invalidAdministrator);
        if (audits.findFirstByActionAndEntityIdOrderByIdDesc(COMPLETED_ACTION, (long) year).isPresent()) return 0;
        boolean global = roles.findByUserId(administratorId).stream().anyMatch(role ->
                role.getRole().getName() == RoleName.ADMIN_GENERAL && role.getLaboratory() == null);
        if (!Integer.valueOf(1).equals(owner.getActive()) || !global) throw invalidAdministrator();
        var existing = new HashSet<LocalDate>();
        holidays.findByDateBetweenOrderByDateAsc(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
                .forEach(holiday -> existing.add(holiday.getDate()));
        int added = 0;
        var now = LocalDateTime.now(clock);
        for (NationalHoliday holiday : incoming) {
            if (holiday.date().getYear() != year) throw new IllegalArgumentException("Año de feriado inválido.");
            // Includes inactive/manual records: never overwrite or reactivate an existing date.
            if (!existing.add(holiday.date())) continue;
            holidays.save(Holiday.create(holiday.date(), holiday.name(), owner, now));
            added++;
        }
        audits.save(AuditLog.record(null, COMPLETED_ACTION, "CALENDARIO_FERIADOS", (long) year, now,
                "El Sistema completó la precarga de feriados nacionales del año " + year
                        + ". Feriados nuevos registrados: " + added + "."));
        return added;
    }

    private static IllegalStateException invalidAdministrator() {
        return new IllegalStateException("La cuenta responsable de los feriados no está disponible.");
    }
}
