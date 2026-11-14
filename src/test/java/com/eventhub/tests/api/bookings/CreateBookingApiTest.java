package com.eventhub.tests.api.bookings;

import com.eventhub.framework.api.ApiAssert;
import com.eventhub.framework.api.Envelope;
import com.eventhub.framework.api.clients.BookingsClient;
import com.eventhub.framework.api.clients.EventsClient;
import com.eventhub.framework.api.models.Booking;
import com.eventhub.framework.api.models.BookingRequest;
import com.eventhub.framework.api.models.Event;
import com.eventhub.framework.data.TestData;
import com.eventhub.framework.junit.ApiTest;
import com.eventhub.framework.junit.Smoke;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** US-114 AC5 (create via POST /api/bookings), AC1 (reference returned) and AC2 (total price). */
@ApiTest
@DisplayName("API: create booking")
class CreateBookingApiTest {

    private static final String BOOKING_REF_FORMAT = "^EVT-[A-Z0-9]{6}$";

    @Test
    @Smoke
    @DisplayName("AC5/AC1: a valid booking is created, confirmed, and has a booking reference")
    void createsConfirmedBookingWithReference(EventsClient events, BookingsClient bookings) {
        Event event = events.findBookable(2);
        BookingRequest request = TestData.bookingFor(event, 2);

        Response response = bookings.createRaw(request);

        ApiAssert.status(response, 201, "create booking");
        Booking booking = Envelope.object(response, Booking.class, "data.booking", "data", "booking");
        assertThat(booking.bookingRef()).matches(BOOKING_REF_FORMAT);
        assertThat(booking.status()).isEqualToIgnoringCase("confirmed");
        assertThat(booking.quantity()).isEqualTo(2);
        assertThat(booking.customerName()).isEqualTo(request.customerName());
        assertThat(booking.customerEmail()).isEqualTo(request.customerEmail());
        assertThat(booking.customerPhone()).isEqualTo(request.customerPhone());
    }

    @ParameterizedTest(name = "AC2: {0} ticket(s) cost price x {0}")
    @ValueSource(ints = {1, 2, 3})
    void totalPriceIsTicketPriceTimesQuantity(int quantity, EventsClient events, BookingsClient bookings) {
        Event event = events.findBookable(quantity);

        Booking booking = bookings.create(TestData.bookingFor(event, quantity));

        assertThat(booking.totalPrice())
                .as("total for %d x %s", quantity, event.price())
                .isEqualByComparingTo(event.price().multiply(BigDecimal.valueOf(quantity)));
    }

    @Test
    @DisplayName("AC2: the server computes the total; a client-supplied total is ignored")
    void clientSuppliedTotalIsIgnored(EventsClient events, BookingsClient bookings) {
        Event event = events.findBookable(2);
        BookingRequest valid = TestData.bookingFor(event, 2);
        Map<String, Object> tampered = new LinkedHashMap<>();
        tampered.put("eventId", valid.eventId());
        tampered.put("customerName", valid.customerName());
        tampered.put("customerEmail", valid.customerEmail());
        tampered.put("customerPhone", valid.customerPhone());
        tampered.put("quantity", 2);
        tampered.put("totalPrice", 1);

        Response response = bookings.createRaw(tampered);

        ApiAssert.status(response, 201, "create booking with tampered total");
        Booking booking = Envelope.object(response, Booking.class, "data.booking", "data", "booking");
        assertThat(booking.totalPrice()).isEqualByComparingTo(event.price().multiply(BigDecimal.valueOf(2)));
    }

    @Test
    @DisplayName("Booking reduces the event's available seats by the quantity booked")
    void bookingReducesAvailableSeats(EventsClient events, BookingsClient bookings) {
        Event event = events.findBookable(3);
        int before = events.get(event.id()).availableSeats();

        bookings.create(TestData.bookingFor(event, 3));

        // Shared environment: someone else may book the same event concurrently, so allow "at least".
        assertThat(events.get(event.id()).availableSeats()).isLessThanOrEqualTo(before - 3);
    }
}
