package ec.edu.espe.practicas.dto;

import java.time.Instant;

public record SessionActivityResponse(Instant expiresAt, Instant idleExpiresAt) { }
