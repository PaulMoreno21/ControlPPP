package ec.edu.espe.practicas.service.holidays;

import ec.edu.espe.practicas.config.HolidaySynchronizationSettings;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Read-only client. Redirects are disabled so the credential cannot leave this origin. */
@Component
public class FeriadosIoClient {
    private static final int MAX_RESPONSE_LENGTH = 65_536;
    private final HttpClient http;
    private final URI base;
    private final Duration timeout;
    private final String apiKey;
    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired
    public FeriadosIoClient(HolidaySynchronizationSettings settings) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build(),
                URI.create("https://api.feriados.io/"), Duration.ofSeconds(8), settings.apiKey());
    }

    // Package visibility permits real HTTP tests against loopback; production origin is fixed.
    FeriadosIoClient(HttpClient http, URI base, Duration timeout, String apiKey) {
        this.http = http;
        this.base = base;
        this.timeout = timeout;
        this.apiKey = apiKey;
    }

    public List<NationalHoliday> holidays(int year) {
        if (year < 1900 || year > 2100 || apiKey.isBlank()) throw unavailable();
        HttpRequest request = HttpRequest.newBuilder(base.resolve("v1/EC/holidays/" + year))
                .timeout(timeout).header("Accept", "application/json")
                .header("Authorization", "Bearer " + apiKey).GET().build();
        var pending = http.sendAsync(request, info -> new LimitedBodySubscriber());
        try {
            // This deadline includes reading the body, not just receiving the response headers.
            HttpResponse<String> response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200) {
                throw unavailable();
            }
            return parse(response.body(), year);
        } catch (InterruptedException exception) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (ExecutionException | TimeoutException exception) {
            pending.cancel(true);
            throw unavailable();
        }
    }

    private List<NationalHoliday> parse(String body, int year) {
        try {
            JsonNode root = json.readTree(body);
            JsonNode data = root.path("data");
            JsonNode meta = root.path("meta");
            if (!root.path("success").isBoolean() || !root.path("success").booleanValue()
                    || !data.isArray() || data.isEmpty() || data.size() > 100
                    || !"EC".equals(meta.path("country").asString(""))
                    || !meta.path("year").isIntegralNumber() || meta.path("year").intValue() != year
                    || (meta.has("total") && (!meta.path("total").isIntegralNumber()
                            || meta.path("total").intValue() != data.size()))) {
                throw unavailable();
            }
            TreeMap<LocalDate, String> dates = new TreeMap<>();
            for (JsonNode item : data) {
                if (!item.path("type").isString()) throw unavailable();
                String type = item.path("type").asString("");
                if (type.isBlank()) throw unavailable();
                if (!"national".equalsIgnoreCase(type)) continue;
                if (!item.path("date").isString() || !item.path("name").isString()) throw unavailable();
                LocalDate date = LocalDate.parse(item.path("date").asString(""));
                String name = item.path("name").asString("").trim();
                if (date.getYear() != year || name.isBlank() || name.length() > 150
                        || name.chars().anyMatch(Character::isISOControl)) throw unavailable();
                dates.merge(date, name, (first, second) -> first.equals(second) ? first : first + " / " + second);
                if (dates.get(date).length() > 150) throw unavailable();
            }
            if (dates.isEmpty()) throw unavailable();
            return dates.entrySet().stream().map(entry -> new NationalHoliday(entry.getKey(), entry.getValue())).toList();
        } catch (RuntimeException exception) {
            // Do not retain provider bodies, headers or the credential in exception messages.
            throw unavailable();
        }
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("No se pudieron obtener los feriados nacionales.");
    }

    @PreDestroy
    public void close() { http.close(); }

    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<String> {
        private final HttpResponse.BodySubscriber<String> delegate =
                HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
        private Flow.Subscription subscription;
        private long received;
        private boolean failed;

        @Override public CompletionStage<String> getBody() { return delegate.getBody(); }
        @Override public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            delegate.onSubscribe(subscription);
        }
        @Override public void onNext(List<ByteBuffer> chunks) {
            if (failed) return;
            for (ByteBuffer chunk : chunks) received += chunk.remaining();
            if (received > MAX_RESPONSE_LENGTH) {
                failed = true;
                subscription.cancel();
                delegate.onError(new IOException("Respuesta de feriados demasiado grande."));
            } else {
                delegate.onNext(chunks);
            }
        }
        @Override public void onError(Throwable error) { delegate.onError(error); }
        @Override public void onComplete() { if (!failed) delegate.onComplete(); }
    }
}
