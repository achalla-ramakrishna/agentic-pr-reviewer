package com.skyroute.auth;

import com.skyroute.common.config.SkyRouteProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies HS256-signed JWT access tokens.
 * Claims: sub = username, roles = [AGENT|ADMIN], iss, iat, exp.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String ROLES_CLAIM = "roles";

    private final SecretKey key;
    private final String issuer;
    private final Duration ttl;
    private final Clock clock;

    public JwtService(SkyRouteProperties properties, Clock clock) {
        // Secret is Base64 and must decode to >= 256 bits for HS256; jjwt enforces this.
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.jwt().secret()));
        this.issuer = properties.jwt().issuer();
        this.ttl = Optional.ofNullable(properties.jwt().ttl()).orElse(Duration.ofHours(1));
        this.clock = clock;
    }

    public IssuedToken issue(String username, Collection<String> roles) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        String token = Jwts.builder()
                .subject(username)
                .issuer(issuer)
                .claim(ROLES_CLAIM, List.copyOf(roles))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(token, ttl.toSeconds());
    }

    /** @return the verified principal, or empty for a missing/expired/tampered token. */
    public Optional<TokenPrincipal> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            List<?> rawRoles = claims.get(ROLES_CLAIM, List.class);
            List<String> roles = rawRoles == null ? List.of() : rawRoles.stream().map(String::valueOf).toList();
            return Optional.of(new TokenPrincipal(claims.getSubject(), roles));
        } catch (JwtException | IllegalArgumentException ex) {
            // DEBUG, not WARN: bad tokens are client errors and would flood the logs.
            log.debug("Rejected JWT: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public record IssuedToken(String accessToken, long expiresInSeconds) {
    }

    public record TokenPrincipal(String username, List<String> roles) {
    }
}
