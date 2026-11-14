package com.eventhub.framework.auth;

import com.eventhub.framework.config.Config;

import java.util.Optional;

/**
 * Resolves logical user names to credentials from configuration:
 * {@code users.<key>.email} / {@code users.<key>.password}.
 * Adding a new persona (e.g. an admin) is a config change, not a code change.
 */
public final class TestUsers {

    public static final String DEFAULT = "default";
    public static final String SECONDARY = "secondary";

    private TestUsers() {
    }

    public static boolean isConfigured(String key) {
        return find(key).isPresent();
    }

    public static TestUser get(String key) {
        return find(key).orElseThrow(() -> new IllegalStateException(
                "No credentials configured for test user '" + key + "'. Set users." + key + ".email and users." + key
                        + ".password in local.properties, or " + Config.toEnvVar("users." + key + ".email") + " and "
                        + Config.toEnvVar("users." + key + ".password") + "."));
    }

    private static Optional<TestUser> find(String key) {
        Config cfg = Config.get();
        Optional<String> email = cfg.find("users." + key + ".email");
        Optional<String> password = cfg.find("users." + key + ".password");
        if (email.isEmpty() || password.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new TestUser(key, email.get(), password.get()));
    }
}
