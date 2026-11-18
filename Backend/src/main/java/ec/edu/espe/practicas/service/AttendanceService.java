package ec.edu.espe.practicas.service;

import ec.edu.espe.practicas.model.Attendance;
import ec.edu.espe.practicas.dto.AttendanceResponse;
import ec.edu.espe.practicas.dto.PagedResponse;
import ec.edu.espe.practicas.model.AuditLog;
import ec.edu.espe.practicas.model.User;
import ec.edu.espe.practicas.repository.AttendanceRepository;
import ec.edu.espe.practicas.repository.AuditLogRepository;
import ec.edu.espe.practicas.repository.HolidayRepository;
import ec.edu.espe.practicas.repository.UserRepository;
import ec.edu.espe.practicas.service.exception.BusinessRuleViolationException;
import ec.edu.espe.practicas.service.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AttendanceService {
    private static final Duration MAX_DAILY_DURATION = Duration.ofHours(6);

    private final AttendanceRepository attendanceRepository;
    private final AuditLogRepository auditLogRepository;
    private final HolidayRepository holidayRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public AttendanceService(
            AttendanceRepository attendanceRepository,
            AuditLogRepository auditLogRepository,
            HolidayRepository holidayRepository,
            UserRepository userRepository,
            Clock clock) {
        this.attendanceRepository = attendanceRepository;
        this.auditLogRepository = auditLogRepository;
        this.holidayRepository = holidayRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    public Attendance registerCheckIn(Long traineeId) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate date = now.toLocalDate();
        validateWorkday(date);
        if (!now.toLocalTime().isBefore(LocalTime.of(18, 0))) {
            throw new BusinessRuleViolationException(
                    "No se permite registrar la entrada a partir de las 18:00.");
        }

        User trainee = userRepository.findById(traineeId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el usuario indicado."));
        if (!Integer.valueOf(1).equals(trainee.getActive())) {
            throw new BusinessRuleViolationException("La cuenta del practicante está inactiva.");
        }
        if (findAttendanceForDate(traineeId, date).isPresent()) {
            throw new BusinessRuleViolationException("Ya existe una asistencia para el día de hoy.");
        }

        Attendance attendance = attendanceRepository.save(Attendance.start(trainee, date, now));
        auditLogRepository.save(AuditLog.record(
                trainee, "REGISTRAR_ENTRADA", "TCWASISTENCIAS", attendance.getId(), now,
                AuditMessages.attendance("REGISTRAR_ENTRADA", AuditMessages.name(trainee), date, now, null, false)));
        return attendance;
    }

    @Transactional(readOnly = true)
    public Optional<Attendance> findTodayAttendance(Long traineeId) {
        LocalDate today = LocalDate.now(clock);
        return findAttendanceForDate(traineeId, today);
    }

    @Transactional(readOnly = true)
    public List<Attendance> findAttendanceHistory(Long traineeId) {
        return attendanceRepository.findByTraineeIdOrderByDateDesc(traineeId);
    }

    @Transactional(readOnly = true)
    public PagedResponse<AttendanceResponse> findAttendanceHistoryPage(Long traineeId, int page, int size) {
        return PagedResponse.from(attendanceRepository.findByTraineeId(traineeId,
                PageableFactory.create(page, size,
                        Sort.by(Sort.Order.desc("date"), Sort.Order.desc("id")))), AttendanceResponse::from);
    }

    public Attendance registerCheckOut(Long traineeId) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate date = now.toLocalDate();
        validateWorkday(date);
        Attendance attendance = attendanceRepository.findLockedByTraineeIdAndDateRange(
                        traineeId, date, date.plusDays(1))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No existe una entrada registrada para el día de hoy."));

        if (attendance.getCheckOut() != null) {
            throw new BusinessRuleViolationException("La salida de hoy ya fue registrada.");
        }
        if (!now.isAfter(attendance.getCheckIn())) {
            throw new BusinessRuleViolationException("La hora de salida debe ser posterior a la entrada.");
        }
        if (Duration.between(attendance.getCheckIn(), now).compareTo(MAX_DAILY_DURATION) >= 0) {
            closeAutomatically(attendance, automaticCheckOutTime(attendance));
            return attendance;
        }

        attendance.setCheckOut(now);
        Attendance savedAttendance = attendanceRepository.save(attendance);
        auditLogRepository.save(AuditLog.record(
                attendance.getTrainee(), "REGISTRAR_SALIDA", "TCWASISTENCIAS",
                attendance.getId(), now, AuditMessages.attendance("REGISTRAR_SALIDA",
                        AuditMessages.name(attendance.getTrainee()), date, attendance.getCheckIn(), now, false)));
        return savedAttendance;
    }

    public void closeOverdueAttendances() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Attendance> dueAttendance = attendanceRepository
                .findOpenAttendanceAtOrBefore(now.minus(MAX_DAILY_DURATION));
        for (Attendance attendance : dueAttendance) {
            if (attendance.getCheckOut() == null) {
                closeAutomatically(attendance, automaticCheckOutTime(attendance));
            }
        }
    }

    private void closeAutomatically(Attendance attendance, LocalDateTime checkOutTime) {
        attendance.setCheckOut(checkOutTime);
        attendanceRepository.save(attendance);
        boolean sameDayAdjustment = checkOutTime.isBefore(attendance.getCheckIn().plus(MAX_DAILY_DURATION));
        auditLogRepository.save(AuditLog.record(
                attendance.getTrainee(), "CIERRE_AUTOMATICO_ASISTENCIA", "TCWASISTENCIAS",
                attendance.getId(), checkOutTime,
                AuditMessages.attendance("CIERRE_AUTOMATICO_ASISTENCIA", AuditMessages.name(attendance.getTrainee()),
                        attendance.getDate(), attendance.getCheckIn(), checkOutTime, sameDayAdjustment)));
    }

    private LocalDateTime automaticCheckOutTime(Attendance attendance) {
        LocalDateTime sixHoursLater = attendance.getCheckIn().plus(MAX_DAILY_DURATION);
        if (sixHoursLater.toLocalDate().isAfter(attendance.getDate())) {
            return attendance.getDate().atTime(23, 59, 59);
        }
        return sixHoursLater;
    }

    private void validateWorkday(LocalDate date) {
        if (date.getDayOfWeek().getValue() > 5) {
            throw new BusinessRuleViolationException("No se permite registrar asistencia en fines de semana.");
        }
        if (holidayRepository.existsByDateAndActive(date, 1)) {
            throw new BusinessRuleViolationException("No se permite registrar asistencia en un día feriado.");
        }
    }

    private Optional<Attendance> findAttendanceForDate(Long traineeId, LocalDate date) {
        return attendanceRepository.findFirstByTraineeIdAndDateGreaterThanEqualAndDateLessThanOrderByDateAsc(
                traineeId, date, date.plusDays(1));
    }
}
