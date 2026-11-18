package ec.edu.espe.practicas.controller;

import ec.edu.espe.practicas.dto.HolidaySynchronizationStatus;
import ec.edu.espe.practicas.model.RoleName;
import ec.edu.espe.practicas.service.AuthenticatedUserService;
import ec.edu.espe.practicas.service.holidays.HolidaySynchronizationService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HolidaySynchronizationController {
    private final AuthenticatedUserService users;
    private final HolidaySynchronizationService synchronization;

    public HolidaySynchronizationController(AuthenticatedUserService users, HolidaySynchronizationService synchronization) {
        this.users = users;
        this.synchronization = synchronization;
    }

    @GetMapping("/api/v1/admin/holidays/synchronization")
    public ResponseEntity<HolidaySynchronizationStatus> status(Authentication authentication) {
        users.requireGlobalRole(authentication, RoleName.ADMIN_GENERAL);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(synchronization.status());
    }
}
