package ec.edu.espe.practicas.service;

import ec.edu.espe.practicas.dto.LoginRequest;
import ec.edu.espe.practicas.dto.LoginResponse;
import ec.edu.espe.practicas.dto.SessionResponse;
import ec.edu.espe.practicas.dto.SessionActivityResponse;
import ec.edu.espe.practicas.security.ActiveSessionRegistry;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import ec.edu.espe.practicas.model.RoleName;
import ec.edu.espe.practicas.model.AuditLog;
import ec.edu.espe.practicas.model.User;
import ec.edu.espe.practicas.model.UserRole;
import ec.edu.espe.practicas.repository.UserRepository;
import ec.edu.espe.practicas.repository.AuditLogRepository;
import ec.edu.espe.practicas.repository.UserRoleRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthenticationService {
    private static final String LOCAL_ISSUER = "control-ppp-local";

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuthenticationAuditService authenticationAuditService;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final long expirationSeconds;
    private final ActiveSessionRegistry sessions;
    private final AuthenticatedUserService authenticatedUserService;

    public AuthenticationService(
            UserRepository userRepository,
            AuditLogRepository auditLogRepository,
            AuthenticationAuditService authenticationAuditService,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            Clock clock,
            ActiveSessionRegistry sessions,
            AuthenticatedUserService authenticatedUserService,
            @Value("${app.jwt.expiration-seconds:28800}") long expirationSeconds) {
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.authenticationAuditService = authenticationAuditService;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.expirationSeconds = expirationSeconds;
        this.sessions = sessions;
        this.authenticatedUserService = authenticatedUserService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        // Serialize credential verification/session issuance with password updates on the same account.
        User account = userRepository.findByEmailIgnoreCaseForUpdate(request.email()).orElse(null);
        if (account == null || !Integer.valueOf(1).equals(account.getActive())
                || !passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            auditFailedLogin(account, request.email(), "No se validaron las credenciales.");
            throw new BadCredentialsException("Correo o contraseña incorrectos.");
        }
        User user = account;

        List<String> roles = userRoleRepository.findByUserId(user.getId()).stream()
                .map(UserRole::getRole)
                .map(role -> role.getName().name())
                .distinct()
                .sorted()
                .toList();
        if (roles.isEmpty()) {
            auditFailedLogin(user, request.email(), "La cuenta no tiene roles habilitados.");
            throw new AccessDeniedException("La cuenta no tiene roles habilitados.");
        }
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(Duration.ofSeconds(expirationSeconds));
        String sessionId = UUID.randomUUID().toString();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(LOCAL_ISSUER)
                .subject(user.getEmail())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(sessionId)
                .claim("email", user.getEmail())
                .claim("roles", roles)
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        auditLogRepository.save(AuditLog.record(
                user, "LOGIN_LOCAL", "TCWUSUARIOS", user.getId(),
                LocalDateTime.now(clock), "Inicio de sesión local exitoso."));

        Instant idleExpiresAt = sessions.register(sessionId, user.getId(), expiresAt);
        return new LoginResponse(token, "Cookie", expirationSeconds, expiresAt, idleExpiresAt,
                new LoginResponse.AuthenticatedUser(
                        user.getId(), user.getFirstName() + " " + user.getLastName(), user.getEmail(), roles,
                        user.getAccountType(), user.isPasswordChangeRequired()));
    }

    public SessionResponse currentSession(Authentication authentication) {
        User user = authenticatedUserService.requireAuthenticatedUserForPasswordChange(authentication);
        List<String> roles = userRoleRepository.findByUserId(user.getId()).stream()
                .map(UserRole::getRole).map(role -> role.getName().name()).distinct().sorted().toList();
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Instant idleExpiresAt = sessions.idleExpiresAt(jwt.getId());
        if (idleExpiresAt == null) throw new CredentialsExpiredException("La sesión terminó.");
        return new SessionResponse(jwt.getExpiresAt(), idleExpiresAt,
                new LoginResponse.AuthenticatedUser(user.getId(), user.getFirstName() + " " + user.getLastName(),
                        user.getEmail(), roles, user.getAccountType(), user.isPasswordChangeRequired()));
    }

    public SessionActivityResponse recordActivity(Authentication authentication) {
        authenticatedUserService.requireAuthenticatedUserForPasswordChange(authentication);
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Instant idleExpiresAt = sessions.touch(jwt.getId());
        if (idleExpiresAt == null) throw new CredentialsExpiredException("La sesión terminó.");
        return new SessionActivityResponse(jwt.getExpiresAt(), idleExpiresAt);
    }

    private void auditFailedLogin(User knownAccount, String submittedEmail, String reason) {
        try {
            authenticationAuditService.recordFailedLogin(knownAccount, submittedEmail, reason);
        } catch (RuntimeException exception) {
            System.getLogger(AuthenticationService.class.getName()).log(
                    System.Logger.Level.ERROR, "No se pudo guardar la auditoria del inicio de sesion fallido.",
                    exception);
        }
    }
}
