package ec.edu.espe.practicas.security;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SessionIdleTimeoutTest {
    private static class TestClock extends Clock {
        final AtomicReference<Instant> time = new AtomicReference<>(Instant.parse("2026-11-10T12:00:00Z"));
        public Instant instant() { return time.get(); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        void advance(long seconds) { time.updateAndGet(value -> value.plusSeconds(seconds)); }
    }

    @Test void ordinaryReadsDoNotRenewIdleDeadline() {
        var clock = new TestClock(); var sessions = new ActiveSessionRegistry(clock, 28800, 10, 900);
        Instant deadline = sessions.register("id", 1L, clock.instant().plusSeconds(28800));
        clock.advance(600); assertTrue(sessions.isActive("id"));
        assertEquals(deadline, sessions.idleExpiresAt("id"));
        clock.advance(299); assertTrue(sessions.isActive("id"));
        clock.advance(1); assertFalse(sessions.isActive("id"));
        assertNull(sessions.touch("id"));
    }

    @Test void explicitActivityKeepsSessionAfterFifteenMinutes() {
        var clock = new TestClock(); var sessions = new ActiveSessionRegistry(clock, 28800, 10, 900);
        sessions.register("id", 1L, clock.instant().plusSeconds(28800));
        clock.advance(600); assertEquals(clock.instant().plusSeconds(900), sessions.touch("id"));
        clock.advance(600); assertTrue(sessions.isActive("id"));
        clock.advance(300); assertFalse(sessions.isActive("id"));
    }

    @Test void repeatedActivityCannotPassEightHourAbsoluteMaximum() {
        var clock = new TestClock(); var sessions = new ActiveSessionRegistry(clock, 28800, 10, 900);
        Instant absolute = clock.instant().plusSeconds(28800);
        sessions.register("id", 1L, absolute);
        for (int i = 0; i < 32; i++) { clock.advance(899); assertNotNull(sessions.touch("id")); }
        assertEquals(absolute, sessions.idleExpiresAt("id"));
        clock.time.set(absolute);
        assertFalse(sessions.isActive("id")); assertNull(sessions.touch("id"));
    }

    @Test void revokedAndUnknownSessionsCannotBeRevivedByActivity() {
        var clock = new TestClock(); var sessions = new ActiveSessionRegistry(clock, 28800, 10, 900);
        sessions.register("one", 1L, clock.instant().plusSeconds(28800));
        sessions.register("two", 1L, clock.instant().plusSeconds(28800));
        sessions.register("other", 2L, clock.instant().plusSeconds(28800));
        sessions.revoke("one"); assertNull(sessions.touch("one"));
        sessions.revokeAll(1L); assertNull(sessions.touch("two"));
        assertNotNull(sessions.touch("other")); assertNull(sessions.touch("missing"));
    }

    @Test void capacityReclaimsIdleExpiredSessionsWithoutEvictingLiveSessions() {
        var clock = new TestClock(); var sessions = new ActiveSessionRegistry(clock, 28800, 1, 900);
        Instant absolute = clock.instant().plusSeconds(28800);
        sessions.register("old", 1L, absolute);
        assertThrows(RuntimeException.class, () -> sessions.register("blocked", 2L, absolute));
        clock.advance(900); sessions.register("new", 2L, absolute);
        assertFalse(sessions.isActive("old")); assertTrue(sessions.isActive("new"));
    }

    @Test void touchAndGlobalRevocationCannotResurrectASession() throws Exception {
        var clock = new TestClock(); var sessions = new ActiveSessionRegistry(clock, 28800, 100, 900);
        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int round = 0; round < 25; round++) {
                String id = "session-" + round;
                sessions.register(id, 1L, clock.instant().plusSeconds(28800));
                var start = new CountDownLatch(1);
                var touch = executor.submit(() -> { start.await(); return sessions.touch(id); });
                var revoke = executor.submit(() -> { start.await(); sessions.revokeAll(1L); return null; });
                start.countDown(); touch.get(); revoke.get();
                assertFalse(sessions.isActive(id));
            }
        }
    }

    @Test void rejectsInvalidTimeoutConfiguration() {
        var clock = new TestClock();
        assertThrows(IllegalArgumentException.class, () -> new ActiveSessionRegistry(clock, 600, 10, 900));
        assertThrows(IllegalArgumentException.class, () -> new ActiveSessionRegistry(clock, 28800, 10, 0));
        var sessions = new ActiveSessionRegistry(clock, 28800, 10, 900);
        assertThrows(IllegalArgumentException.class, () -> sessions.register("past", 1L, clock.instant()));
    }

    @Test void registrationCannotExceedConfiguredLifetimeEvenWithALongerToken() {
        var clock = new TestClock(); var sessions = new ActiveSessionRegistry(clock, 1800, 10, 900);
        Instant absolute = clock.instant().plusSeconds(1800);
        sessions.register("long", 1L, clock.instant().plusSeconds(28800));
        clock.advance(800); sessions.touch("long");
        clock.advance(800); assertEquals(absolute, sessions.touch("long"));
        clock.time.set(absolute); assertFalse(sessions.isActive("long"));
    }
}
