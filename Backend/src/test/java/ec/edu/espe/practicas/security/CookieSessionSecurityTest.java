package ec.edu.espe.practicas.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ec.edu.espe.practicas.config.RateLimitConfiguration;
import ec.edu.espe.practicas.config.SecurityConfiguration;
import ec.edu.espe.practicas.controller.AuthenticationController;
import ec.edu.espe.practicas.dto.LoginResponse;
import ec.edu.espe.practicas.model.AccountType;
import ec.edu.espe.practicas.service.AuthenticationService;
import ec.edu.espe.practicas.service.PasswordManagementService;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class CookieSessionSecurityTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;

    static class MutableClock extends Clock {
        final AtomicReference<Instant> time = new AtomicReference<>(Instant.now());
        public Instant instant() { return time.get(); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
    }

    @Configuration @EnableWebSecurity @EnableWebMvc
    @Import({SecurityConfiguration.class, RateLimitConfiguration.class, ActiveSessionRegistry.class, SessionCookieService.class})
    static class Config {
        @Bean MutableClock clock() { return new MutableClock(); }
        @Bean AuthenticationService authenticationService() { return mock(AuthenticationService.class); }
        @Bean PasswordManagementService passwords() { return mock(PasswordManagementService.class); }
        @Bean AuthenticationController controller(AuthenticationService auth, PasswordManagementService passwords,
                SessionCookieService cookies, CsrfTokenRepository csrf) {
            return new AuthenticationController(auth, passwords, cookies, csrf);
        }
        @Bean PrivateEndpoints endpoints() { return new PrivateEndpoints(); }
    }

    @RestController static class PrivateEndpoints {
        @RequestMapping(value = "/api/private", method = {RequestMethod.GET, RequestMethod.POST})
        Map<String, String> privateData() { return Map.of("result", "ok"); }
    }

    @BeforeEach void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertyValues.of("app.jwt.secret-base64=" + Base64.getEncoder().encodeToString(new byte[32]),
                "app.session.cookie-secure=true").applyTo(context);
        context.register(Config.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach void close() { context.close(); }

    private LoginResponse issue(boolean register) {
        return issue(register, 1L);
    }

    private LoginResponse issue(boolean register, Long owner) {
        return issue(register, owner, 900);
    }

    private LoginResponse issue(boolean register, Long owner, long seconds) {
        var id = UUID.randomUUID().toString();
        var expiry = Instant.now().plusSeconds(seconds);
        var claims = JwtClaimsSet.builder().subject("person@example.com").claim("email", "person@example.com")
                .id(id).issuedAt(Instant.now()).expiresAt(expiry).build();
        String token = context.getBean(JwtEncoder.class).encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        Instant idle = register ? context.getBean(ActiveSessionRegistry.class).register(id, owner, expiry) : expiry;
        return new LoginResponse(token, "Cookie", seconds, expiry, idle,
                new LoginResponse.AuthenticatedUser(1L, "Persona", "person@example.com", List.of("PRACTICANTE"), AccountType.LOCAL, false));
    }

    private Cookie csrfCookie() throws Exception {
        return mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    @Test void loginSetsHttpOnlySecureCookieWithoutLeakingTokenInJson() throws Exception {
        var issued = issue(true);
        when(context.getBean(AuthenticationService.class).login(any())).thenReturn(issued);
        var csrf = csrfCookie();
        var result = mvc.perform(post("/api/v1/auth/login").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"person@example.com\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.id").value(1))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.tokenType").doesNotExist()).andReturn();
        Cookie cookie = result.getResponse().getCookie(SessionCookieService.COOKIE_NAME);
        assertNotNull(cookie);
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.getSecure());
        assertEquals("Strict", cookie.getAttribute("SameSite"));
        assertFalse(result.getResponse().getContentAsString().contains(issued.accessToken()));
        mvc.perform(get("/api/private").cookie(cookie)).andExpect(status().isOk());
    }

    @Test void logoutRevokesEvenCopiedCookieAndRequiresCsrf() throws Exception {
        var issued = issue(true);
        var cookie = new Cookie(SessionCookieService.COOKIE_NAME, issued.accessToken());
        var csrf = csrfCookie();
        mvc.perform(post("/api/v1/auth/logout").cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(get("/api/private").cookie(cookie)).andExpect(status().isOk());
        var result = mvc.perform(post("/api/v1/auth/logout").cookie(cookie, csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent()).andReturn();
        assertEquals(0, result.getResponse().getCookie(SessionCookieService.COOKIE_NAME).getMaxAge());
        mvc.perform(get("/api/private").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test void mutationRequiresMatchingCsrfCookieAndHeader() throws Exception {
        var issued = issue(true);
        var cookie = new Cookie(SessionCookieService.COOKIE_NAME, issued.accessToken());
        var csrf = csrfCookie();
        mvc.perform(post("/api/private").cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(post("/api/private").cookie(cookie, csrf).header("X-XSRF-TOKEN", "wrong"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/private").cookie(cookie, csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isOk());
    }

    @Test void loginWithoutCsrfIsRejectedBeforeCheckingCredentials() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"person@example.com\",\"password\":\"test-password\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(context.getBean(AuthenticationService.class));
    }

    @Test void oldBearerAndUnregisteredSignedCookieCannotAuthenticate() throws Exception {
        var issued = issue(true);
        mvc.perform(get("/api/private").header("Authorization", "Bearer " + issued.accessToken()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/private").cookie(new Cookie(SessionCookieService.COOKIE_NAME, issue(false).accessToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test void replacingLoginRevokesPreviousSession() throws Exception {
        var previous = issue(true);
        var next = issue(true);
        when(context.getBean(AuthenticationService.class).login(any())).thenReturn(next);
        var csrf = csrfCookie();
        mvc.perform(post("/api/v1/auth/login").cookie(csrf, new Cookie(SessionCookieService.COOKIE_NAME, previous.accessToken()))
                .header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"person@example.com\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/private").cookie(new Cookie(SessionCookieService.COOKIE_NAME, previous.accessToken())))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/private").cookie(new Cookie(SessionCookieService.COOKIE_NAME, next.accessToken())))
                .andExpect(status().isOk());
    }

    @Test void registryEnforcesAbsoluteExpiryWithoutJwtClockTolerance() throws Exception {
        var issued = issue(true);
        context.getBean(MutableClock.class).time.set(issued.expiresAt().plusSeconds(1));
        mvc.perform(get("/api/private").cookie(new Cookie(SessionCookieService.COOKIE_NAME, issued.accessToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test void backgroundReadsDoNotKeepAnEightHourTokenAlivePastIdleExpiry() throws Exception {
        var issued = issue(true, 1L, 28800);
        var cookie = new Cookie(SessionCookieService.COOKIE_NAME, issued.accessToken());
        var clock = context.getBean(MutableClock.class);
        Instant start = clock.instant();
        clock.time.set(start.plusSeconds(600));
        mvc.perform(get("/api/private").cookie(cookie)).andExpect(status().isOk());
        clock.time.set(start.plusSeconds(900));
        mvc.perform(get("/api/private").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test void activityRequiresAuthenticationAndMatchingCsrf() throws Exception {
        var issued = issue(true, 1L, 28800);
        var cookie = new Cookie(SessionCookieService.COOKIE_NAME, issued.accessToken());
        var csrf = csrfCookie();
        mvc.perform(post("/api/v1/auth/activity").cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/activity").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isUnauthorized());
        verify(context.getBean(AuthenticationService.class), never()).recordActivity(any());
    }

    @Test void activityExtendsIdleButDoesNotReturnATokenOrReviveLogout() throws Exception {
        var issued = issue(true, 1L, 28800);
        var cookie = new Cookie(SessionCookieService.COOKIE_NAME, issued.accessToken());
        var csrf = csrfCookie();
        var clock = context.getBean(MutableClock.class);
        Instant start = clock.instant();
        when(context.getBean(AuthenticationService.class).recordActivity(any())).thenAnswer(invocation -> {
            var authentication = (org.springframework.security.core.Authentication) invocation.getArgument(0);
            var jwt = (org.springframework.security.oauth2.jwt.Jwt) authentication.getPrincipal();
            return new ec.edu.espe.practicas.dto.SessionActivityResponse(jwt.getExpiresAt(),
                    context.getBean(ActiveSessionRegistry.class).touch(jwt.getId()));
        });
        clock.time.set(start.plusSeconds(800));
        mvc.perform(post("/api/v1/auth/activity").cookie(cookie, csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idleExpiresAt").exists())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        clock.time.set(start.plusSeconds(1000));
        mvc.perform(get("/api/private").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/logout").cookie(cookie, csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/activity").cookie(cookie, csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isUnauthorized());
    }

    @Test void passwordChangeClearsCookieAndRejectsAllCopiesForThatAccount() throws Exception {
        var first = issue(true);
        var second = issue(true);
        var other = issue(true, 2L);
        when(context.getBean(PasswordManagementService.class).changeOwnPassword(any(), any()))
                .thenAnswer(invocation -> {
                    context.getBean(ActiveSessionRegistry.class).revokeAllAfterCommit(1L);
                    return new ec.edu.espe.practicas.dto.PasswordChangeResponse(false);
                });
        var csrf = csrfCookie();
        var result = mvc.perform(post("/api/v1/auth/password/change")
                        .cookie(csrf, new Cookie(SessionCookieService.COOKIE_NAME, first.accessToken()))
                        .header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"current-password\",\"newPassword\":\"New-password-123\"}"))
                .andExpect(status().isOk()).andReturn();
        assertEquals(0, result.getResponse().getCookie(SessionCookieService.COOKIE_NAME).getMaxAge());
        for (var previous : List.of(first, second)) {
            mvc.perform(get("/api/private").cookie(new Cookie(SessionCookieService.COOKIE_NAME, previous.accessToken())))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/private").cookie(new Cookie(SessionCookieService.COOKIE_NAME, other.accessToken())))
                .andExpect(status().isOk());
    }
}
