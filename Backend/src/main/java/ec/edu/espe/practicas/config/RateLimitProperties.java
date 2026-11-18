package ec.edu.espe.practicas.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(
        @DefaultValue("300") @Min(1) @Max(100000) int ipRequests,
        @DefaultValue("120") @Min(1) @Max(100000) int userRequests,
        @DefaultValue("10") @Min(1) @Max(100000) int loginRequests,
        @DefaultValue("60") @Min(1) @Max(3600) int windowSeconds,
        @DefaultValue("50000") @Min(1) @Max(1000000) int maxKeys,
        List<String> trustedProxyIps) {
    public RateLimitProperties {
        trustedProxyIps = trustedProxyIps == null ? List.of() : List.copyOf(trustedProxyIps);
    }
}
