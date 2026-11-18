package ec.edu.espe.practicas.service.holidays;

import ec.edu.espe.practicas.config.HolidaySynchronizationSettings;
import ec.edu.espe.practicas.dto.HolidaySynchronizationStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;

@Service
public class HolidaySynchronizationService {
    private final HolidaySynchronizationSettings settings;
    private final FeriadosIoClient client;
    private final HolidayImportService importer;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile HolidaySynchronizationStatus status;
    private volatile int completedYear;

    public HolidaySynchronizationService(HolidaySynchronizationSettings settings, FeriadosIoClient client,
            HolidayImportService importer, Clock clock) {
        this.settings = settings;
        this.client = client;
        this.importer = importer;
        this.clock = clock;
        status = initialStatus();
    }

    public HolidaySynchronizationStatus status() { return status; }

    public void synchronize() {
        if (!settings.enabled()) { status = initialStatus(); return; }
        if (!running.compareAndSet(false, true)) return;
        var attempt = clock.instant();
        try {
            int year = LocalDate.now(clock).getYear();
            if (completedYear == year) return;
            var previous = importer.completion(year);
            if (previous.isPresent()) {
                completedYear = year;
                var completedAt = previous.get().atZone(clock.getZone()).toInstant();
                status = new HolidaySynchronizationStatus("SUCCESS", completedAt, completedAt, 0,
                        "La precarga de feriados de " + year + " ya está completada. Tus cambios se conservan.");
                return;
            }
            if (!settings.configured()) { status = initialStatus(); return; }
            status = new HolidaySynchronizationStatus("RUNNING", attempt, null, 0,
                    "Se están precargando los feriados nacionales de " + year + ".");
            var incoming = client.holidays(year);
            int added = importer.importYear(year, incoming, settings.administratorId());
            completedYear = year;
            status = new HolidaySynchronizationStatus("SUCCESS", attempt, clock.instant(), added,
                    "Precarga de " + year + " completada. Nuevos registros: " + added
                            + ". Este año no se volverá a importar automáticamente.");
        } catch (RuntimeException exception) {
            System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,
                    "No se pudo completar la precarga anual de feriados nacionales.");
            status = new HolidaySynchronizationStatus("ERROR", attempt, null, 0,
                    "No se pudo completar la precarga de feriados. Se conservan los registros existentes.");
        } finally { running.set(false); }
    }

    private HolidaySynchronizationStatus initialStatus() {
        if (!settings.enabled()) return new HolidaySynchronizationStatus("DISABLED", null, null, 0,
                "La precarga anual de feriados está desactivada.");
        if (!settings.configured()) return new HolidaySynchronizationStatus("NOT_CONFIGURED", null, null, 0,
                "La precarga anual de feriados todavía no está configurada.");
        return new HolidaySynchronizationStatus("READY", null, null, 0,
                "La precarga de feriados del año está pendiente.");
    }
}
