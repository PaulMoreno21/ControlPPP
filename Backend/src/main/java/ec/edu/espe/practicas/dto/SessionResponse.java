package ec.edu.espe.practicas.dto;

import java.time.Instant;

/** Public browser response; never serializes an authentication credential. */
public record SessionResponse(Instant expiresAt, Instant idleExpiresAt, LoginResponse.AuthenticatedUser user) { }
