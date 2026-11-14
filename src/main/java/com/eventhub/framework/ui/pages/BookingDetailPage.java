package com.eventhub.framework.ui.pages;

import com.eventhub.framework.ui.UiSession;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.TimeoutError;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** {@code /bookings/{id}}: booking details and cancellation. */
public final class BookingDetailPage extends BasePage {

    public BookingDetailPage(UiSession session) {
        super(session);
    }

    public BookingDetailPage open(long bookingId) {
        page.navigate("/bookings/" + bookingId);
        assertThat(cancelButton()).isVisible();
        return this;
    }

    public Locator cancelButton() {
        return byTestId("cancel-booking-btn");
    }

    /**
     * Cancels via the app's confirmation dialog and waits for the DELETE call.
     * Handles a native confirm() as well, in case the app uses one instead of its own dialog.
     * @return HTTP status of the cancel call
     */
    public int cancel() {
        page.onceDialog(dialog -> dialog.accept());
        Response response = page.waitForResponse(
                r -> "DELETE".equals(r.request().method()) && r.url().contains("/api/bookings/"),
                () -> {
                    cancelButton().click();
                    try {
                        byTestId("confirm-dialog-yes").click(new Locator.ClickOptions().setTimeout(5000));
                    } catch (TimeoutError noCustomDialog) {
                        // the app used a native confirm(), already accepted above
                    }
                });
        return response.status();
    }
}
