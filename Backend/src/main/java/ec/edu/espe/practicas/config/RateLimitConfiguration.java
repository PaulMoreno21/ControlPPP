package ec.edu.espe.practicas.config;

import com.github.benmanes.caffeine.cache.Ticker;
import ec.edu.espe.practicas.security.ClientIpResolver;
import ec.edu.espe.practicas.security.RequestRateLimiter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfiguration {
    @Bean
    RequestRateLimiter requestRateLimiter(RateLimitProperties properties) {
        return new RequestRateLimiter(properties, Ticker.systemTicker());
    }

    @Bean
    ClientIpResolver clientIpResolver(RateLimitProperties properties) {
        return new ClientIpResolver(properties.trustedProxyIps());
    }
}
