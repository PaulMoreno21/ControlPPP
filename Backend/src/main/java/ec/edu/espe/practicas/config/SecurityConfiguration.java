package ec.edu.espe.practicas.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import ec.edu.espe.practicas.security.ApiRateLimitFilter;
import ec.edu.espe.practicas.security.ClientIpResolver;
import ec.edu.espe.practicas.security.RequestRateLimiter;
import ec.edu.espe.practicas.security.ActiveSessionRegistry;
import ec.edu.espe.practicas.security.CookieSessionAuthenticationFilter;
import ec.edu.espe.practicas.security.SessionCookieService;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
    @Bean
    SecretKey jwtSigningKey(@Value("${app.jwt.secret-base64}") String encodedSecret) {
        byte[] secret = Base64.getDecoder().decode(encodedSecret);
        if (secret.length < 32) {
            throw new IllegalStateException("APP_JWT_SECRET_BASE64 debe contener al menos 32 bytes aleatorios.");
        }
        return new SecretKeySpec(secret, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository(@Value("${app.session.cookie-secure:true}") boolean secure) {
        var repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.secure(secure).sameSite("Strict").path("/"));
        return repository;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, RequestRateLimiter limiter,
            RateLimitProperties rateLimits, ClientIpResolver ipResolver,
            JwtDecoder decoder, SessionCookieService cookies, ActiveSessionRegistry sessions,
            CsrfTokenRepository csrfTokens,
            @Value("${app.security.identity-claim:email}") String identityClaim) throws Exception {
        var csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);
        return http
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens).csrfTokenRequestHandler(csrfHandler))
                .logout(AbstractHttpConfigurer::disable)
                .securityContext(context -> context.securityContextRepository(new RequestAttributeSecurityContextRepository()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/csrf", "/api/v1/auth/logout", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(403);
                    response.setContentType("application/problem+json;charset=UTF-8");
                    response.getWriter().write("{\"status\":403,\"detail\":\"No se pudo verificar la solicitud. Actualiza la página e intenta nuevamente.\"}");
                }).authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType("application/problem+json;charset=UTF-8");
                    response.getWriter().write("{\"status\":401,\"detail\":\"La sesión terminó. Ingresa nuevamente.\"}");
                }))
                .addFilterBefore(new ApiRateLimitFilter(limiter, rateLimits, ipResolver, identityClaim, false),
                        CsrfFilter.class)
                .addFilterAt(new CookieSessionAuthenticationFilter(cookies, decoder, sessions), BasicAuthenticationFilter.class)
                .addFilterAfter(new ApiRateLimitFilter(limiter, rateLimits, ipResolver, identityClaim, true),
                        BasicAuthenticationFilter.class)
                .build();
    }
}
