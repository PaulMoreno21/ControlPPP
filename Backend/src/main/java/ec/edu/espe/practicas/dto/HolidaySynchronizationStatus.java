package ec.edu.espe.practicas.dto;

import java.time.Instant;

public record HolidaySynchronizationStatus(
        String state, Instant lastAttempt, Instant lastSuccess, int added, String message) { }
