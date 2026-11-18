package ec.edu.espe.practicas.controller;

import ec.edu.espe.practicas.dto.AttendanceResponse;
import ec.edu.espe.practicas.dto.PagedResponse;
import ec.edu.espe.practicas.model.RoleName;
import ec.edu.espe.practicas.service.AttendanceService;
import ec.edu.espe.practicas.service.AuthenticatedUserService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/attendance")
public class AttendanceController {
    private final AttendanceService attendanceService;
    private final AuthenticatedUserService authenticatedUserService;

    public AttendanceController(
            AttendanceService attendanceService,
            AuthenticatedUserService authenticatedUserService) {
        this.attendanceService = attendanceService;
        this.authenticatedUserService = authenticatedUserService;
    }

    @PostMapping("/check-in")
    @ResponseStatus(HttpStatus.CREATED)
    public AttendanceResponse checkIn(Authentication authentication) {
        var trainee = authenticatedUserService.requireRole(authentication, RoleName.PRACTICANTE);
        return AttendanceResponse.from(attendanceService.registerCheckIn(trainee.getId()));
    }

    @PostMapping("/check-out")
    public AttendanceResponse checkOut(Authentication authentication) {
        var trainee = authenticatedUserService.requireRole(authentication, RoleName.PRACTICANTE);
        return AttendanceResponse.from(attendanceService.registerCheckOut(trainee.getId()));
    }

    @GetMapping("/today")
    public ResponseEntity<AttendanceResponse> today(Authentication authentication) {
        var trainee = authenticatedUserService.requireRole(authentication, RoleName.PRACTICANTE);
        return attendanceService.findTodayAttendance(trainee.getId())
                .map(AttendanceResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/history")
    public List<AttendanceResponse> history(Authentication authentication) {
        var trainee = authenticatedUserService.requireRole(authentication, RoleName.PRACTICANTE);
        return attendanceService.findAttendanceHistory(trainee.getId()).stream()
                .map(AttendanceResponse::from)
                .toList();
    }

    @GetMapping("/history/page")
    public PagedResponse<AttendanceResponse> historyPage(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        var trainee = authenticatedUserService.requireRole(authentication, RoleName.PRACTICANTE);
        return attendanceService.findAttendanceHistoryPage(trainee.getId(), page, size);
    }
}
