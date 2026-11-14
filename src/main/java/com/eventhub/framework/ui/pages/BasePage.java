package com.eventhub.framework.ui.pages;

import com.eventhub.framework.ui.UiSession;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Page objects hold locators and user actions. They don't hold test assertions about business rules -
 * those stay in tests - but they do wait for the page to be in the state the action needs.
 * Locator preference: data-testid, then role/label, then CSS as a last resort.
 * <p>
 * The UI extension can inject any subclass into a test method parameter, as long as it has a
 * {@code (UiSession)} or {@code (Page)} constructor.
 */
public abstract class BasePage {

    protected final Page page;
    protected final UiSession session;

    protected BasePage(UiSession session) {
        this.session = session;
        this.page = session.page();
    }

    /** For pages used before a session exists (login). */
    protected BasePage(Page page) {
        this.session = null;
        this.page = page;
    }

    protected Locator byTestId(String testId) {
        return page.getByTestId(testId);
    }

    public Page page() {
        return page;
    }
}
