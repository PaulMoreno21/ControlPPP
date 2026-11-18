package ec.edu.espe.practicas.security;

import static org.junit.jupiter.api.Assertions.*;

import ec.edu.espe.practicas.config.RateLimitProperties;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RequestRateLimiterTest {
    @Test
    void resetsAtExpiryAndRejectedRequestsDoNotExtendWindow() {
        var time = new AtomicLong();
        var limiter = new RequestRateLimiter(new RateLimitProperties(3, 2, 1, 60, 100, List.of()), time::get);
        assertTrue(limiter.consume("user", "one", 1).allowed());
        time.set(TimeUnit.SECONDS.toNanos(59) + 1);
        assertEquals(1, limiter.consume("user", "one", 1).retryAfterSeconds());
        time.set(TimeUnit.SECONDS.toNanos(60));
        assertTrue(limiter.consume("user", "one", 1).allowed());
    }

    @Test
    void capacityCannotEvictLiveCountersToBypassLimits() {
        var time = new AtomicLong();
        var limiter = new RequestRateLimiter(new RateLimitProperties(3, 2, 1, 60, 1, List.of()), time::get);
        assertTrue(limiter.consume("ip", "one", 1).allowed());
        assertFalse(limiter.consume("ip", "two", 1).allowed());
        assertFalse(limiter.consume("ip", "one", 1).allowed());
        time.set(TimeUnit.SECONDS.toNanos(60));
        assertTrue(limiter.consume("ip", "two", 1).allowed());
    }

    @Test
    void concurrentRequestsCannotExceedBudget() throws Exception {
        var limiter = new RequestRateLimiter(new RateLimitProperties(3, 2, 1, 60, 100, List.of()), () -> 0);
        var accepted = new AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 200; i++) {
                executor.submit(() -> {
                    if (limiter.consume("user", "one", 10).allowed()) accepted.incrementAndGet();
                });
            }
        }
        assertEquals(10, accepted.get());
    }

    @Test
    void proxyHeadersAreOnlyAcceptedFromExplicitTrustedPeers() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.1");
        request.addHeader("X-Real-IP", "198.51.100.5");
        request.addHeader("X-Forwarded-For", "203.0.113.5");
        assertEquals("192.0.2.1", new ClientIpResolver(List.of()).resolve(request));
        assertEquals("198.51.100.5", new ClientIpResolver(List.of("192.0.2.1")).resolve(request));
        request.removeHeader("X-Real-IP");
        request.addHeader("X-Real-IP", "untrusted.example");
        assertEquals("192.0.2.1", new ClientIpResolver(List.of("192.0.2.1")).resolve(request));
    }
}
