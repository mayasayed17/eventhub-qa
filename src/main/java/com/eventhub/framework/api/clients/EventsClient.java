package com.eventhub.framework.api.clients;

import com.eventhub.framework.api.ApiAssert;
import com.eventhub.framework.api.Envelope;
import com.eventhub.framework.api.models.Event;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import static io.restassured.RestAssured.given;

/** {@code /events} endpoints, plus test-data selection helpers. */
public final class EventsClient {

    private final RequestSpecification spec;

    public EventsClient(RequestSpecification spec) {
        this.spec = spec;
    }

    public Response listRaw() {
        return given().spec(spec).queryParam("page", 1).queryParam("limit", 100).get("/events");
    }

    public Response getRaw(long id) {
        return given().spec(spec).pathParam("id", id).get("/events/{id}");
    }

    public List<Event> list() {
        Response response = listRaw();
        ApiAssert.status(response, 200, "list events");
        return Envelope.list(response, Event.class, "data", "data.events", "data.items", "events");
    }

    public Event get(long id) {
        Response response = getRaw(id);
        ApiAssert.status(response, 200, "get event " + id);
        return Envelope.object(response, Event.class, "data.event", "data", "event");
    }

    /**
     * Picks a live event that can take {@code quantity} tickets: upcoming, paid, with seats to spare.
     * Chooses the one with the most free seats to minimise collisions with other people using the shared
     * environment. Tests never hard-code event ids, because the shared data changes.
     */
    public Event findBookable(int quantity) {
        return list().stream()
                .filter(Event::isUpcoming)
                .filter(e -> e.hasSeatsFor(quantity + 5))
                .filter(e -> e.price() != null && e.price().compareTo(BigDecimal.ZERO) > 0)
                .max(Comparator.comparing(Event::availableSeats))
                .orElseThrow(() -> new IllegalStateException(
                        "No upcoming paid event with at least " + (quantity + 5) + " free seats was found"));
    }
}
