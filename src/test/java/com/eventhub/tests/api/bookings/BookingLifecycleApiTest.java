package com.eventhub.tests.api.bookings;

import com.eventhub.framework.api.ApiAssert;
import com.eventhub.framework.api.Envelope;
import com.eventhub.framework.api.clients.BookingsClient;
import com.eventhub.framework.api.clients.EventsClient;
import com.eventhub.framework.api.models.Booking;
import com.eventhub.framework.api.models.Event;
import com.eventhub.framework.data.TestData;
import com.eventhub.framework.junit.ApiTest;
import com.eventhub.framework.junit.Smoke;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** US-114 AC3 (booking is listed for the user) and AC4 (booking can be cancelled), through the API. */
@ApiTest
@DisplayName("API: booking retrieval and cancellation")
class BookingLifecycleApiTest {

    @Test
    @DisplayName("AC1: a booking can be looked up by its reference")
    void bookingIsRetrievableByReference(EventsClient events, BookingsClient bookings) {
        Booking created = bookings.create(TestData.bookingFor(events.findBookable(1), 1));

        Booking found = bookings.getByRef(created.bookingRef());

        assertThat(found.id()).isEqualTo(created.id());
        assertThat(found.quantity()).isEqualTo(created.quantity());
        assertThat(found.totalPrice()).isEqualByComparingTo(created.totalPrice());
    }

    @Test
    @Smoke
    @DisplayName("AC3: a new booking appears in the user's bookings")
    void newBookingAppearsInMyBookings(EventsClient events, BookingsClient bookings) {
        Booking created = bookings.create(TestData.bookingFor(events.findBookable(1), 1));

        List<Booking> mine = bookings.listMine();

        assertThat(mine).extracting(Booking::bookingRef).contains(created.bookingRef());
    }

    @Test
    @Smoke
    @DisplayName("AC4: cancelling a booking removes it and gives the seats back")
    void cancellingBookingReleasesSeats(EventsClient events, BookingsClient bookings) {
        Event event = events.findBookable(2);
        Booking created = bookings.create(TestData.bookingFor(event, 2));
        int seatsAfterBooking = events.get(event.id()).availableSeats();

        ApiAssert.status(bookings.cancelRaw(created.id()), 200, "cancel booking");

        assertThatBookingIsCancelledOrGone(bookings, created);
        assertThat(bookings.listMine()).extracting(Booking::bookingRef).doesNotContain(created.bookingRef());
        // We use the event with the most free seats, so a concurrent booking by someone else in this window is unlikely.
        assertThat(events.get(event.id()).availableSeats())
                .as("available seats after cancelling 2 tickets")
                .isGreaterThanOrEqualTo(seatsAfterBooking + 2);
    }

    @Test
    @DisplayName("AC4: a booking cannot be cancelled twice")
    void cancellingTwiceIsRejected(EventsClient events, BookingsClient bookings) {
        Booking created = bookings.create(TestData.bookingFor(events.findBookable(1), 1));
        bookings.cancel(created.id());

        Response second = bookings.cancelRaw(created.id());

        ApiAssert.statusIn(second, "second cancel", 400, 404, 409);
    }

    /**
     * The story doesn't say whether a cancelled booking is deleted or kept with a "cancelled" status
     * (question for the PO in the README). Today the API deletes it; either behaviour satisfies AC4.
     */
    private static void assertThatBookingIsCancelledOrGone(BookingsClient bookings, Booking booking) {
        Response lookup = bookings.getByRefRaw(booking.bookingRef());
        if (lookup.statusCode() == 404) {
            return;
        }
        ApiAssert.status(lookup, 200, "get cancelled booking");
        Booking after = Envelope.object(lookup, Booking.class, "data.booking", "data", "booking");
        assertThat(after.status()).isEqualToIgnoringCase("cancelled");
    }
}
