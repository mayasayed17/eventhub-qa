package com.eventhub.framework.ui;

import com.eventhub.framework.config.Config;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * One Playwright + Browser per worker thread (Playwright objects are not thread-safe), reused across that
 * thread's tests. Each test gets its own isolated BrowserContext from {@link UiSession}.
 */
public final class BrowserFactory {

    private static final ThreadLocal<Browser> BROWSER = new ThreadLocal<>();
    private static final Queue<Playwright> ALL = new ConcurrentLinkedQueue<>();

    private BrowserFactory() {
    }

    public static Browser browser() {
        Browser browser = BROWSER.get();
        if (browser == null || !browser.isConnected()) {
            Playwright playwright = Playwright.create();
            ALL.add(playwright);
            browser = launch(playwright, Config.get());
            BROWSER.set(browser);
        }
        return browser;
    }

    public static void shutdownAll() {
        Playwright playwright;
        while ((playwright = ALL.poll()) != null) {
            try {
                playwright.close();
            } catch (RuntimeException ignored) {
                // closing at JVM end; nothing useful to do
            }
        }
    }

    private static Browser launch(Playwright playwright, Config cfg) {
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(cfg.getBoolean("ui.headless"))
                .setSlowMo(cfg.getInt("ui.slowMoMs"));
        String name = cfg.require("ui.browser").toLowerCase(Locale.ROOT);
        return switch (name) {
            case "chromium" -> playwright.chromium().launch(options);
            case "chrome", "msedge" -> playwright.chromium().launch(options.setChannel(name));
            case "firefox" -> playwright.firefox().launch(options);
            case "webkit" -> playwright.webkit().launch(options);
            default -> throw new IllegalStateException("Unsupported ui.browser '" + name + "'");
        };
    }
}
