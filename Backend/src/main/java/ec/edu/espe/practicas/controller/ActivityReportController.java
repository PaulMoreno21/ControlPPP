package ec.edu.espe.practicas.controller;

import ec.edu.espe.practicas.service.ActivityReportService;
import ec.edu.espe.practicas.service.BitacoraExportData;
import ec.edu.espe.practicas.service.BitacoraExporter;
import ec.edu.espe.practicas.service.BitacoraFormat;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ActivityReportController {
    private final ActivityReportService activityReportService;
    private final Map<BitacoraFormat, BitacoraExporter> exporters;

    public ActivityReportController(ActivityReportService activityReportService, List<BitacoraExporter> exporters) {
        this.activityReportService = activityReportService;
        this.exporters = new EnumMap<>(BitacoraFormat.class);
        exporters.forEach(exporter -> this.exporters.put(exporter.format(), exporter));
    }

    @GetMapping("/trainees/{traineeId}/bitacora")
    public java.util.concurrent.Callable<ResponseEntity<byte[]>> exportBitacora(
            Authentication authentication,
            @PathVariable Long traineeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "csv") String format) {
        BitacoraFormat parsed = BitacoraFormat.parse(format);
        return () -> export(authentication, traineeId, from, to, parsed);
    }

    /** Backward-compatible CSV route. */
    @GetMapping("/trainees/{traineeId}/bitacora.csv")
    public java.util.concurrent.Callable<ResponseEntity<byte[]>> exportCsv(
            Authentication authentication,
            @PathVariable Long traineeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return () -> export(authentication, traineeId, from, to, BitacoraFormat.CSV);
    }

    private ResponseEntity<byte[]> export(
            Authentication authentication, Long traineeId, LocalDate from, LocalDate to, BitacoraFormat format) {
        BitacoraExporter exporter = exporters.get(format);
        if (exporter == null) {
            throw new IllegalStateException("No existe un generador registrado para " + format + ".");
        }
        BitacoraExportData data = activityReportService.getAcceptedActivities(authentication, traineeId, from, to);
        byte[] content = exporter.export(data);
        activityReportService.recordExport(authentication, traineeId, from, to, format.name());
        String filename = "bitacora_" + traineeId + "_" + from + "_" + to + "." + format.extension();
        return ResponseEntity.ok()
                .contentType(format.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(content);
    }
}
