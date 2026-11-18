package ec.edu.espe.practicas.security;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ec.edu.espe.practicas.config.RateLimitConfiguration;
import ec.edu.espe.practicas.config.SecurityConfiguration;
import java.time.Instant;
import java.time.Clock;
import java.util.UUID;
import jakarta.servlet.http.Cookie;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class ApiRateLimitTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    @Import({SecurityConfiguration.class, RateLimitConfiguration.class, ActiveSessionRegistry.class, SessionCookieService.class})
    static class TestConfiguration {
        @Bean TestEndpoints endpoints() { return new TestEndpoints(); }
        @Bean Clock clock() { return Clock.systemUTC(); }
    }

    @RestController
    static class TestEndpoints {
        @GetMapping({"/api/test", "/actuator/health"})
        Map<String, String> get() { return Map.of("status", "ok"); }
        @PostMapping("/api/v1/auth/login")
        Map<String, String> login() { return Map.of("status", "ok"); }
        @GetMapping("/api/test/async")
        Callable<Map<String, String>> async() { return () -> Map.of("status", "ok"); }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertyValues.of("app.jwt.secret-base64=" + Base64.getEncoder().encodeToString(new byte[32]),
                "app.security.identity-claim=email", "app.rate-limit.ip-requests=3",
                "app.rate-limit.user-requests=2", "app.rate-limit.login-requests=2",
                "app.rate-limit.window-seconds=60").applyTo(context);
        context.register(TestConfiguration.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach void close() { context.close(); }

    private String token(String email) {
        String id = UUID.randomUUID().toString();
        var expiry = Instant.now().plusSeconds(300);
        var claims = JwtClaimsSet.builder().subject(email).claim("email", email).id(id)
                .issuedAt(Instant.now()).expiresAt(expiry).build();
        context.getBean(ActiveSessionRegistry.class).register(id, 1L, expiry);
        return context.getBean(JwtEncoder.class).encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private MockHttpServletRequestBuilder api(String email, String ip) {
        return get("/api/test").cookie(new Cookie(SessionCookieService.COOKIE_NAME, token(email)))
                .with(request -> { request.setRemoteAddr(ip); return request; });
    }

    @Test
    void ipBudgetAppliesAcrossUsersAndCannotBeSpoofedWithHeaders() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(api("person" + i + "@example.com", "192.0.2.1")
                    .header("X-Real-IP", "198.51.100." + i)).andExpect(status().isOk());
        }
        mvc.perform(api("other@example.com", "192.0.2.1").header("X-Forwarded-For", "203.0.113.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));
        mvc.perform(api("other@example.com", "192.0.2.2")).andExpect(status().isOk());
    }

    @Test
    void userBudgetIsSharedAcrossIpsAndFreshTokens() throws Exception {
        mvc.perform(api("person@example.com", "192.0.2.1")).andExpect(status().isOk());
        mvc.perform(api("PERSON@example.com", "192.0.2.2")).andExpect(status().isOk());
        mvc.perform(api("person@example.com", "192.0.2.3")).andExpect(status().isTooManyRequests());
        mvc.perform(api("other@example.com", "192.0.2.3")).andExpect(status().isOk());
    }

    @Test
    void loginHasStricterIpBudgetBeforeController() throws Exception {
        mvc.perform(post("/api/v1/auth/login").with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login").with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login").with(csrf())).andExpect(status().isTooManyRequests());
    }

    @Test
    void invalidAuthenticationStillRequiresJwtAndConsumesIpBudget() throws Exception {
        mvc.perform(get("/api/test")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/test").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(api("person@example.com", "127.0.0.1")).andExpect(status().isOk());
        mvc.perform(api("person@example.com", "127.0.0.1")).andExpect(status().isTooManyRequests());
    }

    @Test
    void asyncDispatchDoesNotConsumeAnotherRequest() throws Exception {
        var result = mvc.perform(get("/api/test/async").cookie(new Cookie(SessionCookieService.COOKIE_NAME, token("person@example.com"))))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk());
        mvc.perform(api("person@example.com", "127.0.0.1")).andExpect(status().isOk());
        mvc.perform(api("person@example.com", "127.0.0.1")).andExpect(status().isTooManyRequests());
    }

    @Test
    void healthChecksDoNotConsumeApiBudget() throws Exception {
        for (int i = 0; i < 5; i++) mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(api("person@example.com", "127.0.0.1")).andExpect(status().isOk());
    }
}
