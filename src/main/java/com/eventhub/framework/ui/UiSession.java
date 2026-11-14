package com.eventhub.framework.ui;

import com.eventhub.framework.auth.TestUser;
import com.eventhub.framework.config.Config;
import com.eventhub.framework.ui.auth.UiAuthStrategy;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.assertions.PlaywrightAssertions;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongConsumer;

/**
 * One test's browser state: an isolated context (cookies/storage), one page, tracing, and a hook that
 * reports bookings created through the UI so the framework can clean them up.
 */
public final class UiSession {

    private final BrowserContext context;
    private final Page page;
    private final String traceMode;
    private final List<LongConsumer> bookingCreatedListeners = new CopyOnWriteArrayList<>();
    private final List<String> requests = new CopyOnWriteArrayList<>();

    private UiSession(BrowserContext context, Page page, String traceMode) {
        this.context = context;
        this.page = page;
        this.traceMode = traceMode;
    }

    public static UiSession start(Browser browser, Optional<TestUser> user, UiAuthStrategy authStrategy) {
        Config cfg = Config.get();
        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setBaseURL(cfg.require("ui.baseUrl"))
                .setViewportSize(cfg.getInt("ui.viewportWidth"), cfg.getInt("ui.viewportHeight")));
        int timeout = cfg.getInt("ui.defaultTimeoutMs");
        context.setDefaultTimeout(timeout);
        PlaywrightAssertions.setDefaultAssertionTimeout(timeout);

        String traceMode = cfg.require("ui.trace");
        if (!"off".equals(traceMode)) {
            context.tracing().start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true).setSources(false));
        }

        user.ifPresent(u -> authStrategy.authenticate(context, u));

        Page page = context.newPage();
        UiSession session = new UiSession(context, page, traceMode);
        page.onRequest(request -> session.requests.add(request.method() + " " + request.url()));
        return session;
    }

    public Page page() {
        return page;
    }

    public BrowserContext context() {
        return context;
    }

    /** Method + URL of every request the page made, e.g. to assert that invalid input never reached the API. */
    public List<String> requests() {
        return List.copyOf(requests);
    }

    public void onBookingCreated(LongConsumer listener) {
        bookingCreatedListeners.add(listener);
    }

    /** Called by page objects when the app reports a new booking. */
    public void bookingCreated(long bookingId) {
        bookingCreatedListeners.forEach(l -> l.accept(bookingId));
    }

    /** Closes the context; keeps a trace and a screenshot in {@code artifactDir} when the test failed. */
    public void close(boolean failed, Path artifactDir) {
        try {
            if (failed) {
                page.screenshot(new Page.ScreenshotOptions().setPath(artifactDir.resolve("failure.png")).setFullPage(true));
            }
        } catch (RuntimeException ignored) {
            // a crashed page can't be screenshotted; the trace still has the history
        }
        try {
            if ("always".equals(traceMode) || (failed && "retain-on-failure".equals(traceMode))) {
                context.tracing().stop(new Tracing.StopOptions().setPath(artifactDir.resolve("trace.zip")));
            } else if (!"off".equals(traceMode)) {
                context.tracing().stop();
            }
        } finally {
            context.close();
        }
    }
}
