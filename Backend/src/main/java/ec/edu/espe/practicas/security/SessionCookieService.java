package ec.edu.espe.practicas.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

@Component
public class SessionCookieService {
    public static final String COOKIE_NAME = "PPP_SESSION";
    private final boolean secure;
    private final JwtDecoder decoder;
    private final ActiveSessionRegistry sessions;

    public SessionCookieService(@Value("${app.session.cookie-secure:true}") boolean secure,
            JwtDecoder decoder, ActiveSessionRegistry sessions) {
        this.secure = secure;
        this.decoder = decoder;
        this.sessions = sessions;
    }

    public String read(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (var cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    public void write(HttpServletResponse response, String token, long seconds) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(token, seconds).toString());
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        revokeCurrent(request);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", 0).toString());
    }

    public void revokeCurrent(HttpServletRequest request) {
        String token = read(request);
        if (token == null) return;
        try {
            sessions.revoke(decoder.decode(token).getId());
        } catch (JwtException ignored) {
            // Invalid/expired cookies cannot authenticate and must still be removable.
        }
    }

    private ResponseCookie cookie(String value, long seconds) {
        var builder = ResponseCookie.from(COOKIE_NAME, value).httpOnly(true).secure(secure)
                .sameSite("Strict").path("/");
        if (seconds == 0) builder.maxAge(Duration.ZERO);
        return builder.build();
    }
}
