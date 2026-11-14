package com.eventhub.framework.ui.auth;

import com.eventhub.framework.auth.TestUser;
import com.eventhub.framework.config.Config;
import com.microsoft.playwright.BrowserContext;

/**
 * How a browser context becomes "logged in". Chosen by {@code ui.auth.strategy}; tests never log in themselves.
 */
public interface UiAuthStrategy {

    void authenticate(BrowserContext context, TestUser user);

    static UiAuthStrategy fromConfig() {
        String name = Config.get().require("ui.auth.strategy");
        return switch (name) {
            case "token-injection" -> new TokenInjectionStrategy();
            case "login-form" -> new LoginFormStrategy();
            default -> throw new IllegalStateException("Unknown ui.auth.strategy '" + name + "'");
        };
    }
}
