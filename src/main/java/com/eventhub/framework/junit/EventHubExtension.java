package com.eventhub.framework.junit;

import com.eventhub.framework.api.ExchangeRecorder;
import com.eventhub.framework.api.clients.ApiClients;
import com.eventhub.framework.api.clients.BookingsClient;
import com.eventhub.framework.api.clients.EventsClient;
import com.eventhub.framework.auth.TestUsers;
import com.eventhub.framework.cleanup.CleanupRegistry;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The framework's entry point for every test:
 * <ul>
 *   <li>injects API clients for the right identity ({@link AsUser} / {@link Anonymous});</li>
 *   <li>skips (not fails) a test whose required user isn't configured, e.g. the optional second account;</li>
 *   <li>after each test, cleans up everything the test created and, on failure, prints the last HTTP exchanges.</li>
 * </ul>
 */
public final class EventHubExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final Logger LOG = LoggerFactory.getLogger(EventHubExtension.class);
    private static final Set<Class<?>> SUPPORTED = Set.of(BookingsClient.class, EventsClient.class, ApiClients.class,
            CleanupRegistry.class);

    @Override
    public void beforeEach(ExtensionContext context) {
        ExchangeRecorder.reset();
        // The default user is mandatory: if it is missing, TestUsers.get fails loudly with setup instructions.
        // Extra personas are optional: tests that need them are skipped, so a single-account setup still runs.
        requiredUsers(context).stream()
                .filter(key -> !TestUsers.DEFAULT.equals(key))
                .forEach(key -> Assumptions.assumeTrue(TestUsers.isConfigured(key),
                        "Test user '" + key + "' is not configured; skipping. See local.properties.example."));
    }

    @Override
    public void afterEach(ExtensionContext context) {
        if (context.getExecutionException().isPresent()) {
            List<String> exchanges = ExchangeRecorder.drain();
            if (!exchanges.isEmpty()) {
                LOG.error("Last HTTP exchanges of failed test '{}':\n{}", context.getDisplayName(),
                        String.join("\n", exchanges));
            }
        }
        int failed = TestScope.of(context).cleanup().runAll();
        if (failed > 0) {
            LOG.warn("{} cleanup action(s) failed after '{}'; data may be left in the shared environment",
                    failed, context.getDisplayName());
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        return SUPPORTED.contains(parameter.getParameter().getType());
    }

    @Override
    public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        TestScope scope = TestScope.of(context);
        Class<?> type = parameter.getParameter().getType();
        if (type == CleanupRegistry.class) {
            return scope.cleanup();
        }
        ApiClients clients = scope.clients(Identity.forParameter(parameter, context));
        if (type == BookingsClient.class) {
            return clients.bookings();
        }
        if (type == EventsClient.class) {
            return clients.events();
        }
        return clients;
    }

    /** Users the test will need: its own identity plus any named on its parameters. */
    private static Set<String> requiredUsers(ExtensionContext context) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        Identity.forTest(context).ifPresent(keys::add);
        context.getTestMethod().ifPresent(method -> {
            for (Parameter p : method.getParameters()) {
                Optional.ofNullable(p.getAnnotation(AsUser.class)).map(AsUser::value).ifPresent(keys::add);
            }
        });
        return keys;
    }
}
