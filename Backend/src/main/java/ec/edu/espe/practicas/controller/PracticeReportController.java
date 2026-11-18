package ec.edu.espe.practicas.controller;

import ec.edu.espe.practicas.dto.PracticeReportResponse;
import ec.edu.espe.practicas.dto.ReportScopeResponse;
import ec.edu.espe.practicas.service.PracticeReportService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports/practice")
public class PracticeReportController {
    private final PracticeReportService practiceReportService;

    public PracticeReportController(PracticeReportService practiceReportService) {
        this.practiceReportService = practiceReportService;
    }

    @GetMapping("/scope")
    public ReportScopeResponse scope(Authentication authentication,
            @RequestParam(required = false) Long laboratoryId) {
        return practiceReportService.scope(authentication, laboratoryId);
    }

    @GetMapping
    public java.util.concurrent.Callable<PracticeReportResponse> report(
            Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long laboratoryId,
            @RequestParam(required = false) Long traineeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        return () -> practiceReportService.report(authentication, from, to, laboratoryId, traineeId, page, size);
    }
}
