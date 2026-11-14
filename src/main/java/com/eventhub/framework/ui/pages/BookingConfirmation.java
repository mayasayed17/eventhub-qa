package com.eventhub.framework.ui.pages;

import com.eventhub.framework.ui.UiSession;
import com.microsoft.playwright.Locator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** The confirmation shown after a successful booking. */
public final class BookingConfirmation extends BasePage {

    private static final Pattern AMOUNT = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");

    private final int apiStatus;
    private final Long bookingId;

    BookingConfirmation(UiSession session, int apiStatus, Long bookingId) {
        super(session);
        this.apiStatus = apiStatus;
        this.bookingId = bookingId;
    }

    /** HTTP status the app's create-booking call returned. */
    public int apiStatus() {
        return apiStatus;
    }

    public Long bookingId() {
        return bookingId;
    }

    public Locator bookingRefLocator() {
        return byTestId("booking-ref");
    }

    public String bookingRef() {
        assertThat(bookingRefLocator()).isVisible();
        return bookingRefLocator().innerText().trim();
    }

    /**
     * Every monetary-looking number visible on the page after confirmation. The total has no test id,
     * so tests check that the expected total is among the amounts shown (with a quantity > 1, so the
     * unit price alone can't satisfy the check).
     */
    public List<BigDecimal> amountsShown() {
        String text = page.locator("body").innerText();
        List<BigDecimal> amounts = new ArrayList<>();
        Matcher m = AMOUNT.matcher(text);
        while (m.find()) {
            amounts.add(new BigDecimal(m.group().replace(",", "")));
        }
        return amounts;
    }
}
