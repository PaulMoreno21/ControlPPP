package ec.edu.espe.practicas.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

public class CookieSessionAuthenticationFilter extends OncePerRequestFilter {
    private final SessionCookieService cookies;
    private final JwtDecoder decoder;
    private final ActiveSessionRegistry sessions;
    private final RequestAttributeSecurityContextRepository contexts = new RequestAttributeSecurityContextRepository();

    public CookieSessionAuthenticationFilter(SessionCookieService cookies, JwtDecoder decoder, ActiveSessionRegistry sessions) {
        this.cookies = cookies;
        this.decoder = decoder;
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = cookies.read(request);
        if (token != null) {
            try {
                var jwt = decoder.decode(token);
                if (sessions.isActive(jwt.getId())) {
                    var context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of()));
                    SecurityContextHolder.setContext(context);
                    contexts.saveContext(context, request, response);
                }
            } catch (JwtException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
