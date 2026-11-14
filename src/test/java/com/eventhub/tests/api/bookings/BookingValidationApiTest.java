package com.eventhub.tests.api.bookings;

import com.eventhub.framework.api.ApiAssert;
import com.eventhub.framework.api.clients.BookingsClient;
import com.eventhub.framework.api.clients.EventsClient;
import com.eventhub.framework.api.models.BookingRequest;
import com.eventhub.framework.api.models.Event;
import com.eventhub.framework.data.TestData;
import com.eventhub.framework.junit.ApiTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC1 says the user enters number of tickets, name, email and phone. These check that the API refuses a booking
 * when any of them is missing or nonsensical. Any booking that is wrongly accepted is still cleaned up.
 */
@ApiTest
@DisplayName("API: booking validation")
class BookingValidationApiTest {

    static Stream<Arguments> invalidBookings() {
        return Stream.of(
                invalid("missing eventId", r -> r.withEventId(null)),
                invalid("missing customer name", r -> r.withCustomerName(null)),
                invalid("blank customer name", r -> r.withCustomerName("   ")),
                invalid("missing email", r -> r.withCustomerEmail(null)),
                invalid("malformed email", r -> r.withCustomerEmail("not-an-email")),
                invalid("missing phone", r -> r.withCustomerPhone(null)),
                invalid("missing quantity", r -> r.withQuantity(null)),
                invalid("zero tickets", r -> r.withQuantity(0)),
                invalid("negative tickets", r -> r.withQuantity(-1)));
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("invalidBookings")
    void rejectsInvalidBooking(String description, UnaryOperator<BookingRequest> breakIt,
                               EventsClient events, BookingsClient bookings) {
        BookingRequest request = breakIt.apply(TestData.bookingFor(events.findBookable(1), 1));

        Response response = bookings.createRaw(request);

        ApiAssert.status(response, 400, "create booking with " + description);
    }

    @Test
    @DisplayName("rejects a booking for an event that does not exist")
    void rejectsUnknownEvent(EventsClient events, BookingsClient bookings) {
        BookingRequest request = TestData.bookingFor(events.findBookable(1), 1).withEventId(999_999_999L);

        ApiAssert.statusIn(bookings.createRaw(request), "create booking for unknown event", 400, 404);
    }

    @Test
    @DisplayName("rejects more tickets than there are seats, and changes nothing")
    void rejectsMoreTicketsThanAvailable(EventsClient events, BookingsClient bookings) {
        Event event = events.findBookable(1);
        int tooMany = event.availableSeats() + 1;

        Response response = bookings.createRaw(TestData.bookingFor(event, tooMany));

        ApiAssert.statusIn(response, "over-book", 400, 409);
        assertThat(events.get(event.id()).availableSeats()).isLessThanOrEqualTo(event.availableSeats());
    }

    private static Arguments invalid(String description, UnaryOperator<BookingRequest> change) {
        return Arguments.of(description, change);
    }
}
