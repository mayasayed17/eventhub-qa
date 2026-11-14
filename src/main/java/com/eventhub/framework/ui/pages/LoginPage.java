package com.eventhub.framework.ui.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public final class LoginPage extends BasePage {

    private static final Pattern SUBMIT = Pattern.compile("^(sign in|log in|login)$", Pattern.CASE_INSENSITIVE);

    public LoginPage(Page page) {
        super(page);
    }

    public LoginPage open() {
        page.navigate("/login");
        assertThat(passwordInput()).isVisible();
        return this;
    }

    public void loginAs(String email, String password) {
        emailInput().fill(email);
        passwordInput().fill(password);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SUBMIT)).click();
        assertThat(page).not().hasURL(Pattern.compile(".*/login.*"));
    }

    public boolean isDisplayed() {
        return page.url().contains("/login") && passwordInput().isVisible();
    }

    private Locator emailInput() {
        return page.locator("input[type=email], input[name=email]").first();
    }

    private Locator passwordInput() {
        return page.locator("input[type=password]").first();
    }
}
