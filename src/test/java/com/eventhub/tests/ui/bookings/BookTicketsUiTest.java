package com.eventhub.tests.ui.bookings;

import com.eventhub.framework.api.clients.BookingsClient;
import com.eventhub.framework.api.clients.EventsClient;
import com.eventhub.framework.api.models.Booking;
import com.eventhub.framework.api.models.Event;
import com.eventhub.framework.data.TestData;
import com.eventhub.framework.junit.Smoke;
import com.eventhub.framework.junit.UiTest;
import com.eventhub.framework.ui.UiSession;
import com.eventhub.framework.ui.pages.BookingConfirmation;
import com.eventhub.framework.ui.pages.EventDetailPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** US-114 AC1 and AC2 through the browser, end to end. */
@UiTest
@DisplayName("UI: book tickets")
class BookTicketsUiTest {

    @Test
    @Smoke
    @DisplayName("AC1/AC2: booking shows a confirmation with a reference and the right total")
    void bookingShowsConfirmationWithReferenceAndTotal(EventsClient events, BookingsClient bookings,
                                                       EventDetailPage eventPage) {
        int quantity = 3; // > 1 so that the unit price alone can't pass the total check
        Event event = events.findBookable(quantity);
        BigDecimal expectedTotal = event.price().multiply(BigDecimal.valueOf(quantity));

        BookingConfirmation confirmation = eventPage.open(event.id())
                .setQuantity(quantity)
                .fillCustomer(TestData.customer())
                .confirm();

        assertThat(confirmation.apiStatus()).as("create-booking call made by the UI").isEqualTo(201);
        String ref = confirmation.bookingRef();
        assertThat(ref).matches("^EVT-[A-Z0-9]{6}$");
        assertThat(confirmation.amountsShown())
                .as("amounts on the confirmation should include %s", expectedTotal)
                .anySatisfy(amount -> assertThat(amount).isEqualByComparingTo(expectedTotal));

        // The UI is a view over the API: what was stored must match what was shown.
        Booking stored = bookings.getByRef(ref);
        assertThat(stored.quantity()).isEqualTo(quantity);
        assertThat(stored.totalPrice()).isEqualByComparingTo(expectedTotal);
    }

    @Test
    @DisplayName("AC1: Confirm Booking without customer details does not create a booking")
    void confirmWithoutCustomerDetailsCreatesNothing(EventsClient events, EventDetailPage eventPage, UiSession session) {
        eventPage.open(events.findBookable(1).id()).confirmExpectingValidationError();

        assertThat(session.requests()).noneMatch(r -> r.startsWith("POST ") && r.matches(".*/api/bookings/?(\\?.*)?$"));
        assertThat(eventPage.page().getByTestId("booking-ref").isVisible()).isFalse();
    }
}
