package com.eventhub.tests.api.bookings;

import com.eventhub.framework.api.ApiAssert;
import com.eventhub.framework.api.clients.BookingsClient;
import com.eventhub.framework.api.clients.EventsClient;
import com.eventhub.framework.api.models.Booking;
import com.eventhub.framework.data.TestData;
import com.eventhub.framework.junit.Anonymous;
import com.eventhub.framework.junit.ApiTest;
import com.eventhub.framework.junit.AsUser;
import com.eventhub.framework.auth.TestUsers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bookings carry personal data (name, email, phone), so "my bookings" must really be mine.
 * Note how identities are chosen by annotation only; no test touches a token.
 */
@ApiTest
@DisplayName("API: booking authorization")
class BookingAuthorizationApiTest {

    @Test
    @DisplayName("creating a booking requires authentication")
    void createRequiresAuthentication(EventsClient events, @Anonymous BookingsClient anonymous) {
        ApiAssert.status(anonymous.createRaw(TestData.bookingFor(events.findBookable(1), 1)), 401,
                "anonymous create booking");
    }

    @Test
    @DisplayName("listing bookings requires authentication")
    void listRequiresAuthentication(@Anonymous BookingsClient anonymous) {
        ApiAssert.status(anonymous.listRaw(), 401, "anonymous list bookings");
    }

    @Test
    @DisplayName("another user can neither see nor cancel my booking (needs users.secondary)")
    void otherUsersCannotSeeOrCancelMyBooking(EventsClient events, BookingsClient mine,
                                              @AsUser(TestUsers.SECONDARY) BookingsClient theirs) {
        Booking booking = mine.create(TestData.bookingFor(events.findBookable(1), 1));

        ApiAssert.statusIn(theirs.getByIdRaw(booking.id()), "other user reads my booking", 403, 404);
        ApiAssert.statusIn(theirs.cancelRaw(booking.id()), "other user cancels my booking", 403, 404);
        assertThat(theirs.listMine()).extracting(Booking::bookingRef).doesNotContain(booking.bookingRef());

        // ...and it is untouched for its owner
        assertThat(mine.getByRef(booking.bookingRef()).id()).isEqualTo(booking.id());
    }
}
