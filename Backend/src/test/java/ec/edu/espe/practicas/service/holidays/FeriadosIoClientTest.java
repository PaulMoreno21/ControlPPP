package ec.edu.espe.practicas.service.holidays;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Exercises actual HTTP, authorization, serialization and timeouts without provider credentials. */
class FeriadosIoClientTest {
    private HttpServer server;
    private ExecutorService executor;
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> requestPath = new AtomicReference<>();
    private final AtomicInteger requests = new AtomicInteger();
    private volatile int status = 200;
    private volatile String body;
    private volatile int delay;
    private volatile int bodyDelay;
    private FeriadosIoClient client;
    private final List<FeriadosIoClient> clients = new ArrayList<>();

    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        body = envelope("{\"date\":\"2026-05-25\",\"name\":\"Batalla de Pichincha\",\"type\":\"national\"}");
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestPath.set(exchange.getRequestURI().getPath());
            try {
                if (delay > 0) Thread.sleep(delay);
                if (status == 302) exchange.getResponseHeaders().set("Location", "/must-not-receive-key");
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                if (bodyDelay > 0) Thread.sleep(bodyDelay);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally { exchange.close(); }
        });
        server.start();
        client = client(Duration.ofSeconds(3), "frd_fixture_not_real");
    }

    private FeriadosIoClient client(Duration timeout, String key) {
        var created = new FeriadosIoClient(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"), timeout, key);
        clients.add(created);
        return created;
    }

    @AfterEach void stop() { server.stop(0); executor.shutdownNow(); clients.forEach(FeriadosIoClient::close); }

    private static String envelope(String rows) {
        return "{\"success\":true,\"data\":[" + rows + "],\"meta\":{\"country\":\"EC\",\"year\":2026}}";
    }

    @Test void readsNationalDatesAndDoesNotCalculateTransfersOrImportObservances() {
        body = envelope("{\"date\":\"2026-05-25\",\"name\":\"Batalla de Pichincha\",\"type\":\"national\"},"
                + "{\"date\":\"2026-12-06\",\"name\":\"Local\",\"type\":\"regional\"}");
        var result = client.holidays(2026);
        assertEquals(1, result.size());
        assertEquals(LocalDate.of(2026, 5, 25), result.getFirst().date());
        assertEquals("Bearer frd_fixture_not_real", authorization.get());
        assertEquals("/v1/EC/holidays/2026", requestPath.get());
        assertEquals(1, requests.get());
    }

    @Test void mergesNationalNamesThatShareTheSameDate() {
        body = envelope("{\"date\":\"2026-01-01\",\"name\":\"Uno\",\"type\":\"national\"},"
                + "{\"date\":\"2026-01-01\",\"name\":\"Dos\",\"type\":\"national\"}");
        assertEquals("Uno / Dos", client.holidays(2026).getFirst().name());
    }

    @ParameterizedTest @ValueSource(ints = {302, 400, 401, 403, 429, 500, 503})
    void rejectsUpstreamErrorsWithoutLeakingBodyOrFollowingRedirects(int code) {
        status = code; body = "frd_fixture_not_real upstream internal details";
        var error = assertThrows(IllegalStateException.class, () -> client.holidays(2026));
        assertEquals("No se pudieron obtener los feriados nacionales.", error.getMessage());
        assertNull(error.getCause());
        assertEquals(1, requests.get());
    }

    @ParameterizedTest @ValueSource(strings = {
            "not JSON", "{}", "{\"success\":false,\"data\":[]}",
            "{\"success\":true,\"data\":[],\"meta\":{\"country\":\"EC\",\"year\":2026}}",
            "{\"success\":true,\"data\":[{}],\"meta\":{\"country\":\"CO\",\"year\":2026}}"
    })
    void rejectsMalformedEmptyOrWrongCountryResponses(String invalid) {
        body = invalid;
        assertThrows(IllegalStateException.class, () -> client.holidays(2026));
    }

    @ParameterizedTest @ValueSource(strings = {"2027-01-01", "2026-02-30", "2026-01-01T00:00:00Z"})
    void rejectsInvalidOrWrongYearDates(String date) {
        body = envelope("{\"date\":\"" + date + "\",\"name\":\"Nombre\",\"type\":\"national\"}");
        assertThrows(IllegalStateException.class, () -> client.holidays(2026));
    }

    @Test void rejectsWrongYearMetadataAndOversizedDescriptions() {
        body = body.replace("\"year\":2026", "\"year\":2027");
        assertThrows(IllegalStateException.class, () -> client.holidays(2026));
        body = envelope("{\"date\":\"2026-01-01\",\"name\":\"" + "x".repeat(151) + "\",\"type\":\"national\"}");
        assertThrows(IllegalStateException.class, () -> client.holidays(2026));
    }

    @Test void emptyKeyNeverContactsProvider() {
        assertThrows(IllegalStateException.class, () -> client(Duration.ofSeconds(1), "").holidays(2026));
        assertEquals(0, requests.get());
    }

    @Test void slowProviderTimesOutInsteadOfBlockingTheApplication() {
        delay = 1000;
        var error = assertThrows(IllegalStateException.class,
                () -> client(Duration.ofMillis(150), "frd_fixture_not_real").holidays(2026));
        assertEquals("No se pudieron obtener los feriados nacionales.", error.getMessage());
    }

    @Test void responseBodyAlsoHasADeadlineAfterHeadersHaveArrived() {
        bodyDelay = 1000;
        var limited = client(Duration.ofMillis(150), "frd_fixture_not_real");
        try {
            assertThrows(IllegalStateException.class, () -> limited.holidays(2026));
        } finally { limited.close(); }
    }

    @Test void oversizedBodyIsCancelledBeforeParsing() {
        body = "x".repeat(70000);
        assertThrows(IllegalStateException.class, () -> client.holidays(2026));
    }
}
