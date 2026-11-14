package com.eventhub.framework.ui.auth;

import com.eventhub.framework.api.Json;
import com.eventhub.framework.auth.TestUser;
import com.eventhub.framework.auth.TokenProvider;
import com.eventhub.framework.config.Config;
import com.microsoft.playwright.BrowserContext;

/**
 * Default. Re-uses the API token (one login per user per run, shared with API tests) and writes it to
 * localStorage before any page script runs. Fast, and keeps the login page out of every UI test.
 * The login page itself deserves its own dedicated tests rather than being exercised implicitly by all of them.
 */
final class TokenInjectionStrategy implements UiAuthStrategy {

    @Override
    public void authenticate(BrowserContext context, TestUser user) {
        String key = Config.get().require("ui.auth.tokenStorageKey");
        String token = TokenProvider.instance().tokenFor(user).value();
        context.addInitScript("window.localStorage.setItem(" + Json.write(key) + ", " + Json.write(token) + ");");
    }
}
