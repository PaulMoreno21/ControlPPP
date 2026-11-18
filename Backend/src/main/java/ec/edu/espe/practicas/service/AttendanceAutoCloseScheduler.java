package ec.edu.espe.practicas.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AttendanceAutoCloseScheduler {
    private final AttendanceService attendanceService;

    public AttendanceAutoCloseScheduler(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @Scheduled(fixedDelay = 5_000)
    public void closeOverdueAttendances() {
        attendanceService.closeOverdueAttendances();
    }
}
