package com.eventhub.framework;

import com.eventhub.framework.api.Envelope;
import com.eventhub.framework.api.Json;
import com.eventhub.framework.auth.AccessToken;
import com.eventhub.framework.cleanup.CleanupRegistry;
import com.eventhub.framework.config.Config;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests of the framework itself: no network, no credentials. A framework a team relies on for years
 * needs its own safety net, and these run first in CI.
 */
@Tag("framework")
@DisplayName("Framework self-tests")
class FrameworkSelfTest {

    @Test
    void configKeysMapToEnvironmentVariables() {
        assertThat(Config.toEnvVar("users.default.email")).isEqualTo("EVENTHUB_USERS_DEFAULT_EMAIL");
        assertThat(Config.toEnvVar("api.baseUrl")).isEqualTo("EVENTHUB_API_BASEURL");
    }

    @Test
    void defaultConfigurationLoads() {
        assertThat(Config.get().require("api.basePath")).isEqualTo("/api");
        assertThat(Config.get().getList("auth.tokenJsonPaths")).contains("data.token");
    }

    @Test
    void jwtExpiryIsReadFromTheToken() {
        long exp = Instant.now().plus(Duration.ofHours(2)).getEpochSecond();
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"exp\":" + exp + "}").getBytes(StandardCharsets.UTF_8));
        AccessToken token = AccessToken.of("header." + payload + ".signature", Duration.ofMinutes(1));

        assertThat(token.expiresAt()).isEqualTo(Instant.ofEpochSecond(exp));
        assertThat(token.isUsable()).isTrue();
    }

    @Test
    void opaqueTokenFallsBackToConfiguredTtl() {
        AccessToken token = AccessToken.of("opaque-token", Duration.ofSeconds(30));
        assertThat(token.isUsable()).as("30s TTL is inside the 60s refresh margin").isFalse();
        assertThat(token.toString()).doesNotContain("opaque-token");
    }

    @Test
    void envelopeReadsDottedPaths() {
        var root = Json.read("{\"data\":{\"booking\":{\"id\":42}}}");
        assertThat(Envelope.at(root, "data.booking.id").asLong()).isEqualTo(42);
        assertThat(Envelope.at(root, "data.nope.id").isMissingNode()).isTrue();
    }

    @Test
    void cleanupRunsNewestFirstAndSurvivesFailures() {
        CleanupRegistry registry = new CleanupRegistry();
        List<String> order = new ArrayList<>();
        registry.register("a", () -> order.add("a"));
        registry.register("b", () -> {
            throw new IllegalStateException("boom");
        });
        registry.register("c", () -> order.add("c"));
        registry.register("c", () -> order.add("duplicate"));
        registry.unregister("missing");

        int failures = registry.runAll();

        assertThat(order).containsExactly("c", "a");
        assertThat(failures).isEqualTo(1);
    }
}
