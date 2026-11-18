package ec.edu.espe.practicas.controller;

import ec.edu.espe.practicas.dto.LoginRequest;
import ec.edu.espe.practicas.dto.LoginResponse;
import ec.edu.espe.practicas.dto.SessionResponse;
import ec.edu.espe.practicas.dto.SessionActivityResponse;
import ec.edu.espe.practicas.security.SessionCookieService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import ec.edu.espe.practicas.dto.ChangePasswordRequest;
import ec.edu.espe.practicas.dto.PasswordChangeResponse;
import ec.edu.espe.practicas.service.AuthenticationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {
    private final AuthenticationService authenticationService;
    private final ec.edu.espe.practicas.service.PasswordManagementService passwordManagementService;
    private final SessionCookieService cookies;
    private final CsrfTokenRepository csrfTokens;

    public AuthenticationController(
            AuthenticationService authenticationService,
            ec.edu.espe.practicas.service.PasswordManagementService passwordManagementService,
            SessionCookieService cookies, CsrfTokenRepository csrfTokens) {
        this.authenticationService = authenticationService;
        this.passwordManagementService = passwordManagementService;
        this.cookies = cookies;
        this.csrfTokens = csrfTokens;
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public SessionResponse login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest, HttpServletResponse response) {
        LoginResponse result = authenticationService.login(request);
        cookies.revokeCurrent(servletRequest);
        cookies.write(response, result.accessToken(), result.expiresIn());
        csrfTokens.saveToken(csrfTokens.generateToken(servletRequest), servletRequest, response);
        response.setHeader("Cache-Control", "no-store");
        return new SessionResponse(result.expiresAt(), result.idleExpiresAt(), result.user());
    }

    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void csrf(CsrfToken token, HttpServletResponse response) {
        token.getToken();
        response.setHeader("Cache-Control", "no-store");
    }

    @GetMapping("/session")
    public SessionResponse session(org.springframework.security.core.Authentication authentication,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return authenticationService.currentSession(authentication);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        cookies.logout(request, response);
        csrfTokens.saveToken(null, request, response);
        response.setHeader("Cache-Control", "no-store");
    }

    @PostMapping("/activity")
    public SessionActivityResponse activity(org.springframework.security.core.Authentication authentication,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return authenticationService.recordActivity(authentication);
    }

    @PostMapping("/password/change")
    public PasswordChangeResponse changePassword(
            org.springframework.security.core.Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest servletRequest, HttpServletResponse response) {
        PasswordChangeResponse result = passwordManagementService.changeOwnPassword(authentication, request);
        cookies.logout(servletRequest, response);
        csrfTokens.saveToken(null, servletRequest, response);
        response.setHeader("Cache-Control", "no-store");
        return result;
    }
}
