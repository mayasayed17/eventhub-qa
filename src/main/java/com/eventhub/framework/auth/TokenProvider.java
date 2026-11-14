package com.eventhub.framework.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The only place that logs in. Logs each user in once per run, caches the token, and logs in again
 * shortly before the token expires. Thread-safe, so parallel tests share one login per user.
 */
public final class TokenProvider {

    private static final Logger LOG = LoggerFactory.getLogger(TokenProvider.class);
    private static final TokenProvider INSTANCE = new TokenProvider(new AuthApi());

    private final AuthApi authApi;
    private final Map<String, AccessToken> cache = new ConcurrentHashMap<>();

    TokenProvider(AuthApi authApi) {
        this.authApi = authApi;
    }

    public static TokenProvider instance() {
        return INSTANCE;
    }

    public AccessToken tokenFor(TestUser user) {
        return cache.compute(user.key(), (key, current) -> {
            if (current != null && current.isUsable()) {
                return current;
            }
            LOG.info("Logging in as {}", user);
            return authApi.login(user);
        });
    }

    /** Forces a fresh login next time, e.g. after a test deliberately invalidates a session. */
    public void invalidate(TestUser user) {
        cache.remove(user.key());
    }
}
