package com.eventhub.framework.junit;

import com.eventhub.framework.api.clients.ApiClients;
import com.eventhub.framework.auth.TestUsers;
import com.eventhub.framework.cleanup.CleanupRegistry;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Per-test state shared by the API and UI extensions: the cleanup registry and API clients per identity. */
final class TestScope {

    static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create("eventhub");
    private static final String ANONYMOUS = "<anonymous>";

    private final CleanupRegistry cleanup = new CleanupRegistry();
    private final Map<String, ApiClients> clients = new ConcurrentHashMap<>();

    static TestScope of(ExtensionContext context) {
        return context.getStore(NAMESPACE).getOrComputeIfAbsent(TestScope.class, k -> new TestScope(), TestScope.class);
    }

    CleanupRegistry cleanup() {
        return cleanup;
    }

    ApiClients clients(Optional<String> userKey) {
        return clients.computeIfAbsent(userKey.orElse(ANONYMOUS),
                k -> ApiClients.create(userKey.map(TestUsers::get), cleanup));
    }
}
