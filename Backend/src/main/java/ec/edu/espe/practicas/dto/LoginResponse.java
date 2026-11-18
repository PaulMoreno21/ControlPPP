package ec.edu.espe.practicas.dto;

import ec.edu.espe.practicas.model.AccountType;
import java.util.List;
import java.time.Instant;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Instant expiresAt,
        Instant idleExpiresAt,
        AuthenticatedUser user) {

    public record AuthenticatedUser(
            Long id,
            String name,
            String email,
            List<String> roles,
            AccountType accountType,
            boolean passwordChangeRequired) {
    }
}
