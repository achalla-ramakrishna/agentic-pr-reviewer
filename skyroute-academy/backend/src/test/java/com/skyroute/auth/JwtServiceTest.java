package com.skyroute.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.skyroute.common.config.SkyRouteProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtMzI=";
    private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");

    private static JwtService serviceAt(Instant now, String secret) {
        var props = new SkyRouteProperties(new SkyRouteProperties.Jwt(secret, "test", Duration.ofMinutes(30)),
                null, null, null);
        return new JwtService(props, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void roundTripsUsernameAndRoles() {
        var service = serviceAt(T0, SECRET);
        var token = service.issue("agent", List.of("AGENT"));

        var principal = service.verify(token.accessToken());

        assertThat(principal).hasValueSatisfying(p -> {
            assertThat(p.username()).isEqualTo("agent");
            assertThat(p.roles()).containsExactly("AGENT");
        });
        assertThat(token.expiresInSeconds()).isEqualTo(1800);
    }

    @Test
    void rejectsExpiredToken() {
        var token = serviceAt(T0, SECRET).issue("agent", List.of("AGENT")).accessToken();
        assertThat(serviceAt(T0.plus(Duration.ofMinutes(31)), SECRET).verify(token)).isEmpty();
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        String otherSecret = "b3RoZXItc2VjcmV0LW90aGVyLXNlY3JldC1vdGhlci1zZWNyZXQ=";
        var forged = serviceAt(T0, otherSecret).issue("admin", List.of("ADMIN")).accessToken();
        assertThat(serviceAt(T0, SECRET).verify(forged)).isEmpty();
    }

    @Test
    void rejectsGarbage() {
        assertThat(serviceAt(T0, SECRET).verify("not.a.jwt")).isEmpty();
    }
}
