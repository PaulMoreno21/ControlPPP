package ec.edu.espe.practicas.controller;

import ec.edu.espe.practicas.dto.HolidayRequest;
import ec.edu.espe.practicas.dto.HolidayResponse;
import ec.edu.espe.practicas.dto.PagedResponse;
import ec.edu.espe.practicas.dto.HolidayStatusRequest;
import ec.edu.espe.practicas.service.HolidayAdministrationService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/holidays")
public class HolidayAdministrationController {
    private final HolidayAdministrationService holidayAdministrationService;

    public HolidayAdministrationController(HolidayAdministrationService holidayAdministrationService) {
        this.holidayAdministrationService = holidayAdministrationService;
    }

    @GetMapping
    public List<HolidayResponse> listHolidays(
            Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return holidayAdministrationService.listHolidays(authentication, from, to);
    }

    @GetMapping("/page")
    public PagedResponse<HolidayResponse> listHolidaysPage(
            Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        return holidayAdministrationService.listHolidaysPage(authentication, from, to, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HolidayResponse createHoliday(
            Authentication authentication, @Valid @RequestBody HolidayRequest request) {
        return holidayAdministrationService.createHoliday(authentication, request);
    }

    @PutMapping("/{holidayId}")
    public HolidayResponse updateHoliday(
            Authentication authentication, @PathVariable Long holidayId,
            @Valid @RequestBody HolidayRequest request) {
        return holidayAdministrationService.updateHoliday(authentication, holidayId, request);
    }

    @PatchMapping("/{holidayId}/status")
    public HolidayResponse updateStatus(
            Authentication authentication,
            @PathVariable Long holidayId,
            @Valid @RequestBody HolidayStatusRequest request) {
        return holidayAdministrationService.updateHolidayStatus(
                authentication, holidayId, request.active());
    }

    @DeleteMapping("/{holidayId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHoliday(Authentication authentication, @PathVariable Long holidayId) {
        holidayAdministrationService.deleteHoliday(authentication, holidayId);
    }
}
