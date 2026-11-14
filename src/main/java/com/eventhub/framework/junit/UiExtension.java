package com.eventhub.framework.junit;

import com.eventhub.framework.api.clients.BookingsClient;
import com.eventhub.framework.auth.TestUsers;
import com.eventhub.framework.config.Config;
import com.eventhub.framework.ui.BrowserFactory;
import com.eventhub.framework.ui.UiSession;
import com.eventhub.framework.ui.auth.UiAuthStrategy;
import com.eventhub.framework.ui.pages.BasePage;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Browser lifecycle for UI tests. Before each test: a new context, authenticated as the test's user by the
 * configured {@link UiAuthStrategy}. After each test: screenshot + trace on failure, context closed.
 * Bookings the UI creates are routed into the same cleanup registry the API tests use.
 */
public final class UiExtension implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final String SESSION = "ui-session";

    @Override
    public void beforeAll(ExtensionContext context) {
        context.getRoot().getStore(TestScope.NAMESPACE)
                .getOrComputeIfAbsent("browser-shutdown", k -> new BrowserShutdown(), BrowserShutdown.class);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        Optional<String> userKey = Identity.forTest(context);
        UiSession session = UiSession.start(BrowserFactory.browser(), userKey.map(TestUsers::get),
                UiAuthStrategy.fromConfig());

        // UI-created bookings are deleted after the test, as the same user, through the API.
        if (userKey.isPresent()) {
            BookingsClient bookings = TestScope.of(context).clients(userKey).bookings();
            session.onBookingCreated(bookings::track);
        }
        context.getStore(TestScope.NAMESPACE).put(SESSION, session);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        UiSession session = context.getStore(TestScope.NAMESPACE).remove(SESSION, UiSession.class);
        if (session != null) {
            session.close(context.getExecutionException().isPresent(), artifactDir(context));
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        Class<?> type = parameter.getParameter().getType();
        return type == UiSession.class || type == Page.class || BasePage.class.isAssignableFrom(type);
    }

    @Override
    public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        UiSession session = context.getStore(TestScope.NAMESPACE).get(SESSION, UiSession.class);
        Class<?> type = parameter.getParameter().getType();
        if (type == UiSession.class) {
            return session;
        }
        if (type == Page.class) {
            return session.page();
        }
        return instantiatePage(type, session);
    }

    private static Object instantiatePage(Class<?> type, UiSession session) {
        try {
            try {
                Constructor<?> c = type.getConstructor(UiSession.class);
                return c.newInstance(session);
            } catch (NoSuchMethodException noSessionConstructor) {
                Constructor<?> c = type.getConstructor(Page.class);
                return c.newInstance(session.page());
            }
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new ParameterResolutionException("Cannot create page object " + type.getSimpleName()
                    + "; it needs a public (UiSession) or (Page) constructor", e);
        }
    }

    private static Path artifactDir(ExtensionContext context) {
        String cls = context.getRequiredTestClass().getSimpleName();
        String test = context.getDisplayName().replaceAll("[^A-Za-z0-9._-]+", "_");
        return Path.of(Config.get().require("ui.artifactsDir"), cls, test);
    }

    /** Closes every worker's browser once the whole run is over. */
    private static final class BrowserShutdown implements ExtensionContext.Store.CloseableResource {
        @Override
        public void close() {
            BrowserFactory.shutdownAll();
        }
    }
}
