package com.eventhub.framework.ui.pages;

import com.eventhub.framework.ui.UiSession;
import com.microsoft.playwright.Locator;

/** {@code /bookings}: the signed-in user's bookings. */
public final class MyBookingsPage extends BasePage {

    public MyBookingsPage(UiSession session) {
        super(session);
    }

    public MyBookingsPage open() {
        page.navigate("/bookings");
        page.waitForLoadState();
        return this;
    }

    public MyBookingsPage openFromNavbar() {
        byTestId("nav-bookings").click();
        page.waitForURL("**/bookings");
        return this;
    }

    public Locator cards() {
        return byTestId("booking-card");
    }

    public Locator cardFor(String bookingRef) {
        return cards().filter(new Locator.FilterOptions().setHasText(bookingRef));
    }
}
