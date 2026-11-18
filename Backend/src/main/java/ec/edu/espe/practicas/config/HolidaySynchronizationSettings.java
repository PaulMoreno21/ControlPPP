package ec.edu.espe.practicas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Credentials stay on the server and are never part of a response or log. */
@Component
public class HolidaySynchronizationSettings {
    private final boolean enabled;
    private final String apiKey;
    private final long administratorId;

    public HolidaySynchronizationSettings(
            @Value("${app.holidays.sync.enabled:true}") boolean enabled,
            @Value("${app.holidays.sync.api-key:}") String apiKey,
            @Value("${app.holidays.sync.administrator-id:0}") long administratorId) {
        this.enabled = enabled;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.administratorId = administratorId;
    }

    public boolean enabled() { return enabled; }
    public String apiKey() { return apiKey; }
    public long administratorId() { return administratorId; }
    public boolean configured() { return !apiKey.isBlank() && administratorId > 0; }
}
