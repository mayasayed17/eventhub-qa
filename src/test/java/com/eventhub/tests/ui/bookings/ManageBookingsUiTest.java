package com.eventhub.tests.ui.bookings;

import com.eventhub.framework.api.clients.BookingsClient;
import com.eventhub.framework.api.clients.EventsClient;
import com.eventhub.framework.api.models.Booking;
import com.eventhub.framework.api.models.Event;
import com.eventhub.framework.data.TestData;
import com.eventhub.framework.junit.Anonymous;
import com.eventhub.framework.junit.Smoke;
import com.eventhub.framework.junit.UiTest;
import com.eventhub.framework.ui.pages.BookingDetailPage;
import com.eventhub.framework.ui.pages.MyBookingsPage;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * US-114 AC3 and AC4 in the browser. The booking is created through the API (fast, and not what these tests
 * are about); the behaviour under test is driven through the UI.
 */
@UiTest
@DisplayName("UI: my bookings")
class ManageBookingsUiTest {

    @Test
    @Smoke
    @DisplayName("AC3: a booking is listed in My Bookings with its event and ticket count")
    void bookingIsListedInMyBookings(EventsClient events, BookingsClient bookings, MyBookingsPage myBookings) {
        Event event = events.findBookable(2);
        Booking booking = bookings.create(TestData.bookingFor(event, 2));

        myBookings.open();

        assertThat(myBookings.cardFor(booking.bookingRef())).isVisible();
        assertThat(myBookings.cardFor(booking.bookingRef())).containsText(event.title());
        assertThat(myBookings.cardFor(booking.bookingRef())).containsText("2");
    }

    @Test
    @DisplayName("AC4: a booking can be cancelled from its details page")
    void bookingCanBeCancelled(EventsClient events, BookingsClient bookings,
                               BookingDetailPage details, MyBookingsPage myBookings) {
        Booking booking = bookings.create(TestData.bookingFor(events.findBookable(1), 1));

        int status = details.open(booking.id()).cancel();

        org.assertj.core.api.Assertions.assertThat(status).as("cancel call made by the UI").isEqualTo(200);
        myBookings.open();
        assertThat(myBookings.cardFor(booking.bookingRef())).hasCount(0);
        org.assertj.core.api.Assertions.assertThat(bookings.listMine())
                .extracting(Booking::bookingRef).doesNotContain(booking.bookingRef());
    }

    @Test
    @Anonymous
    @DisplayName("Story gap: a logged-out visitor is sent to log in rather than seeing My Bookings")
    void loggedOutVisitorIsAskedToLogIn(Page page) {
        page.navigate("/bookings");

        assertThat(page).hasURL(Pattern.compile(".*/login.*"));
    }
}
