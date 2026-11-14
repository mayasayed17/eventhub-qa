package com.eventhub.framework.ui.auth;

import com.eventhub.framework.auth.TestUser;
import com.eventhub.framework.ui.pages.LoginPage;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;

/**
 * Fallback: logs in through the real login page. Slower, but independent of how the app stores its session.
 * Switch with {@code -Dui.auth.strategy=login-form}.
 */
final class LoginFormStrategy implements UiAuthStrategy {

    @Override
    public void authenticate(BrowserContext context, TestUser user) {
        Page page = context.newPage();
        try {
            new LoginPage(page).open().loginAs(user.email(), user.password());
        } finally {
            page.close();
        }
    }
}
