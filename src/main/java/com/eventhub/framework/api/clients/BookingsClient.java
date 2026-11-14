package com.eventhub.framework.api.clients;

import com.eventhub.framework.api.ApiAssert;
import com.eventhub.framework.api.Envelope;
import com.eventhub.framework.api.Json;
import com.eventhub.framework.api.models.Booking;
import com.eventhub.framework.api.models.BookingRequest;
import com.eventhub.framework.cleanup.CleanupRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.List;

import static io.restassured.RestAssured.given;

/**
 * {@code /bookings} endpoints. Two kinds of method:
 * <ul>
 *   <li>{@code ...Raw} returns the HTTP response untouched, for tests that assert on status codes and errors;</li>
 *   <li>the typed methods assert success and return models, for tests that use the API as setup.</li>
 * </ul>
 * Any booking that comes back successfully, from either kind, is registered for cleanup automatically.
 */
public final class BookingsClient {

    private static final String BOOKINGS = "/bookings";
    private static final String BOOKING_BY_ID = "/bookings/{id}";
    private static final String BOOKING_BY_REF = "/bookings/ref/{ref}";

    private final RequestSpecification spec;
    private final RequestSpecification cleanupSpec;
    private final CleanupRegistry cleanup;

    public BookingsClient(RequestSpecification spec, RequestSpecification cleanupSpec, CleanupRegistry cleanup) {
        this.spec = spec;
        this.cleanupSpec = cleanupSpec;
        this.cleanup = cleanup;
    }

    // ---------- raw ----------

    public Response createRaw(Object body) {
        Response response = given().spec(spec).body(body instanceof String s ? s : Json.write(body)).post(BOOKINGS);
        if (response.statusCode() / 100 == 2) {
            idOf(response).ifPresent(this::track);
        }
        return response;
    }

    public Response getByIdRaw(long id) {
        return given().spec(spec).pathParam("id", id).get(BOOKING_BY_ID);
    }

    public Response getByRefRaw(String ref) {
        return given().spec(spec).pathParam("ref", ref).get(BOOKING_BY_REF);
    }

    public Response listRaw() {
        return given().spec(spec).queryParam("limit", 100).get(BOOKINGS);
    }

    public Response cancelRaw(long id) {
        Response response = given().spec(spec).pathParam("id", id).delete(BOOKING_BY_ID);
        if (response.statusCode() / 100 == 2) {
            cleanup.unregister(key(id));
        }
        return response;
    }

    // ---------- typed ----------

    public Booking create(BookingRequest request) {
        Response response = createRaw(request);
        ApiAssert.status(response, 201, "create booking");
        return Envelope.object(response, Booking.class, "data.booking", "data", "booking");
    }

    public Booking getByRef(String ref) {
        Response response = getByRefRaw(ref);
        ApiAssert.status(response, 200, "get booking by ref " + ref);
        return Envelope.object(response, Booking.class, "data.booking", "data", "booking");
    }

    public List<Booking> listMine() {
        Response response = listRaw();
        ApiAssert.status(response, 200, "list bookings");
        return Envelope.list(response, Booking.class, "data", "data.bookings", "data.items", "bookings");
    }

    public void cancel(long id) {
        ApiAssert.status(cancelRaw(id), 200, "cancel booking " + id);
    }

    /** Registers a booking created elsewhere (e.g. through the UI) for cleanup. */
    public void track(long bookingId) {
        cleanup.register(key(bookingId), () -> {
            int status = given().spec(cleanupSpec).pathParam("id", bookingId).delete(BOOKING_BY_ID).statusCode();
            if (status / 100 != 2 && status != 404) {
                throw new IllegalStateException("DELETE booking " + bookingId + " returned " + status);
            }
        });
    }

    private static String key(long id) {
        return "booking:" + id;
    }

    private static java.util.Optional<Long> idOf(Response response) {
        JsonNode root = Json.read(response.asString());
        for (String path : List.of("data.booking.id", "data.id", "booking.id", "id")) {
            JsonNode node = Envelope.at(root, path);
            if (node.canConvertToLong()) {
                return java.util.Optional.of(node.asLong());
            }
        }
        return java.util.Optional.empty();
    }
}
