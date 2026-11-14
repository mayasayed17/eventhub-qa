package com.eventhub.framework.api.clients;

import com.eventhub.framework.api.ApiSpecFactory;
import com.eventhub.framework.auth.TestUser;
import com.eventhub.framework.auth.TestUsers;
import com.eventhub.framework.cleanup.CleanupRegistry;
import io.restassured.specification.RequestSpecification;

import java.util.Optional;

/**
 * All API clients for one identity (a test user, or anonymous). Adding an endpoint group means adding a
 * client class and one accessor here; the JUnit extension then injects it into tests by type.
 */
public final class ApiClients {

    private final BookingsClient bookings;
    private final EventsClient events;

    private ApiClients(BookingsClient bookings, EventsClient events) {
        this.bookings = bookings;
        this.events = events;
    }

    public static ApiClients create(Optional<TestUser> user, CleanupRegistry cleanup) {
        RequestSpecification spec = ApiSpecFactory.forUser(user);
        // Anything created by an anonymous call (which shouldn't succeed) is still cleaned up as the default user.
        RequestSpecification cleanupSpec = user.isPresent() || !TestUsers.isConfigured(TestUsers.DEFAULT)
                ? spec
                : ApiSpecFactory.authenticatedAs(TestUsers.get(TestUsers.DEFAULT));
        return new ApiClients(new BookingsClient(spec, cleanupSpec, cleanup), new EventsClient(spec));
    }

    public BookingsClient bookings() {
        return bookings;
    }

    public EventsClient events() {
        return events;
    }
}
