package com.skyroute.common.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Type-safe, validated view of the {@code skyroute.*} properties (fails fast at startup). */
@Validated
@ConfigurationProperties(prefix = "skyroute")
public record SkyRouteProperties(Jwt jwt, Grpc grpc, Cors cors, Seed seed) {

    public record Jwt(@NotBlank String secret, @NotBlank String issuer, Duration ttl) {
    }

    public record Grpc(boolean enabled, @Min(1) int port) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Seed(boolean enabled) {
    }
}
