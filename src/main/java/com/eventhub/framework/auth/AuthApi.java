package com.eventhub.framework.auth;

import com.eventhub.framework.api.ApiSpecFactory;
import com.eventhub.framework.api.Envelope;
import com.eventhub.framework.api.Json;
import com.eventhub.framework.config.Config;
import io.restassured.response.Response;

import java.time.Duration;
import java.util.Map;

import static io.restassured.RestAssured.given;

/** Talks to the login endpoint. Only {@link TokenProvider} should need this. */
public final class AuthApi {

    private final Config cfg = Config.get();

    public Response loginRaw(String email, String password) {
        return given()
                .spec(ApiSpecFactory.anonymous())
                .body(Json.write(Map.of("email", email, "password", password)))
                .post(cfg.require("auth.loginPath"));
    }

    public AccessToken login(TestUser user) {
        Response response = loginRaw(user.email(), user.password());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Login failed for " + user + ": HTTP " + response.statusCode()
                    + " " + Json.abbreviate(response.asString()));
        }
        String token = Envelope.text(response, cfg.getList("auth.tokenJsonPaths"))
                .orElseThrow(() -> new IllegalStateException("Login for " + user + " succeeded but no token was found at "
                        + cfg.require("auth.tokenJsonPaths") + ". Adjust auth.tokenJsonPaths. Response: "
                        + Json.abbreviate(response.asString())));
        return AccessToken.of(token, Duration.ofMinutes(cfg.getInt("auth.fallbackTokenTtlMinutes")));
    }
}
