package com.eventhub.framework.ui.pages;

import com.eventhub.framework.api.Envelope;
import com.eventhub.framework.api.Json;
import com.eventhub.framework.data.TestData.Customer;
import com.eventhub.framework.ui.UiSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;

import java.util.List;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** {@code /events/{id}}: event details and the booking form. */
public final class EventDetailPage extends BasePage {

    /** Matches the create-booking call, not GET /bookings/... */
    private static final Pattern CREATE_BOOKING_URL = Pattern.compile("/api/bookings/?(\\?.*)?$");
    private static final Pattern INCREMENT = Pattern.compile("^\\+$|increase|increment", Pattern.CASE_INSENSITIVE);

    public EventDetailPage(UiSession session) {
        super(session);
    }

    public EventDetailPage open(long eventId) {
        page.navigate("/events/" + eventId);
        assertThat(confirmButton()).isVisible();
        return this;
    }

    public int quantity() {
        Locator q = byTestId("quantity-input");
        String raw = "INPUT".equals(q.evaluate("el => el.tagName")) ? q.inputValue() : q.innerText();
        return Integer.parseInt(raw.replaceAll("\\D", ""));
    }

    /** The quantity is a stepper (a display plus +/- buttons), so we click until we reach the target. */
    public EventDetailPage setQuantity(int target) {
        Locator q = byTestId("quantity-input");
        if ("INPUT".equals(q.evaluate("el => el.tagName")) && q.isEditable()) {
            q.fill(String.valueOf(target));
        } else {
            Locator increment = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(INCREMENT)).first();
            for (int guard = 0; quantity() < target && guard < 50; guard++) {
                increment.click();
            }
        }
        if (quantity() != target) {
            throw new IllegalStateException("Could not set quantity to " + target + "; it shows " + quantity());
        }
        return this;
    }

    public EventDetailPage fillCustomer(Customer customer) {
        byTestId("customer-name").fill(customer.name());
        byTestId("customer-email").fill(customer.email());
        byTestId("customer-phone").fill(customer.phone());
        return this;
    }

    /**
     * Clicks Confirm Booking and waits for the API response, so the test proceeds on a real outcome rather
     * than on a timeout. The created booking id is reported to the session for cleanup.
     */
    public BookingConfirmation confirm() {
        Response response = page.waitForResponse(
                r -> "POST".equals(r.request().method()) && CREATE_BOOKING_URL.matcher(r.url()).find(),
                () -> confirmButton().click());
        Long bookingId = null;
        if (response.status() / 100 == 2) {
            JsonNode root = Json.read(response.text());
            for (String path : List.of("data.booking.id", "data.id", "id")) {
                JsonNode id = Envelope.at(root, path);
                if (id.canConvertToLong()) {
                    bookingId = id.asLong();
                    session.bookingCreated(bookingId);
                    break;
                }
            }
        }
        return new BookingConfirmation(session, response.status(), bookingId);
    }

    /**
     * Tries to confirm a form that should be rejected. A disabled button and a validation message are both
     * valid rejections, so we only click when the button is enabled, then give a request time to appear.
     * (A short fixed wait is the honest way to assert that something does NOT happen.)
     */
    public EventDetailPage confirmExpectingValidationError() {
        if (confirmButton().isEnabled()) {
            confirmButton().click();
        }
        page.waitForTimeout(1500);
        return this;
    }

    public Locator confirmButton() {
        return byTestId("confirm-booking-btn");
    }
}
