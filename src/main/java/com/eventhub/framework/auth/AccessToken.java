package com.eventhub.framework.auth;

import com.eventhub.framework.api.Json;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/** A bearer token plus when it stops being usable. */
public record AccessToken(String value, Instant expiresAt) {

    private static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);

    public boolean isUsable() {
        return Instant.now().plus(REFRESH_MARGIN).isBefore(expiresAt);
    }

    /** Uses the JWT {@code exp} claim when the token is a JWT, otherwise the fallback TTL. */
    public static AccessToken of(String value, Duration fallbackTtl) {
        Instant expiry = jwtExpiry(value).orElseGet(() -> Instant.now().plus(fallbackTtl));
        return new AccessToken(value, expiry);
    }

    static Optional<Instant> jwtExpiry(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonNode exp = Json.read(payload).path("exp");
            return exp.canConvertToLong() ? Optional.of(Instant.ofEpochSecond(exp.asLong())) : Optional.empty();
        } catch (RuntimeException notAJwt) {
            return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return "AccessToken[***, expiresAt=" + expiresAt + "]";
    }
}
