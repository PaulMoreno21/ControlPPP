package ec.edu.espe.practicas.security;

import ec.edu.espe.practicas.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

public final class ApiRateLimitFilter extends OncePerRequestFilter {
    private final RequestRateLimiter limiter;
    private final RateLimitProperties properties;
    private final ClientIpResolver ipResolver;
    private final String identityClaim;
    private final boolean userPhase;

    public ApiRateLimitFilter(RequestRateLimiter limiter, RateLimitProperties properties,
            ClientIpResolver ipResolver, String identityClaim, boolean userPhase) {
        this.limiter = limiter;
        this.properties = properties;
        this.ipResolver = ipResolver;
        this.identityClaim = identityClaim;
        this.userPhase = userPhase;
    }

    @Override
    protected String getAlreadyFilteredAttributeName() {
        return getClass().getName() + (userPhase ? ".user" : ".ip");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equals(request.getMethod()) || !path(request).startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RequestRateLimiter.Decision decision = new RequestRateLimiter.Decision(true, 0);
        if (userPhase) {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && authentication.getPrincipal() instanceof Jwt jwt) {
                String identity = jwt.getClaimAsString(identityClaim);
                if (identity != null && !identity.isBlank()) {
                    decision = limiter.consume("user", identity.trim().toLowerCase(Locale.ROOT), properties.userRequests());
                }
            }
        } else {
            String ip = ipResolver.resolve(request);
            decision = limiter.consume("ip", ip, properties.ipRequests());
            if (decision.allowed() && "POST".equals(request.getMethod())
                    && "/api/v1/auth/login".equals(path(request))) {
                decision = limiter.consume("login-ip", ip, properties.loginRequests());
            }
        }
        if (!decision.allowed()) {
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
            response.setHeader("Cache-Control", "no-store");
            response.setContentType("application/problem+json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Demasiadas solicitudes\","
                    + "\"status\":429,\"detail\":\"Has realizado demasiadas solicitudes. Espera "
                    + decision.retryAfterSeconds() + " segundos y vuelve a intentarlo.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
