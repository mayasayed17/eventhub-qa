package com.eventhub.framework.data;

import com.eventhub.framework.api.models.BookingRequest;
import com.eventhub.framework.api.models.Event;
import com.eventhub.framework.config.Config;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unique, recognisable test data. Every customer name starts with {@code data.namePrefix} and the run id,
 * so anything left behind in the shared environment can be traced back to a run and swept.
 */
public final class TestData {

    public static final String RUN_ID = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss"));
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private TestData() {
    }

    public record Customer(String name, String email, String phone) {
    }

    public static Customer customer() {
        Config cfg = Config.get();
        int n = SEQUENCE.incrementAndGet();
        // Letters only in the name, in case the app validates names; still unique and traceable to the run.
        String name = cfg.require("data.namePrefix") + " " + letters(Long.parseLong(RUN_ID)) + " " + letters(n);
        String email = "qa.auto." + RUN_ID + "." + n + "@" + cfg.require("data.emailDomain");
        return new Customer(name, email, phone());
    }

    /** Base-26 in letters behind a "B": 0 -> "Ba", 1 -> "Bb", 26 -> "Bba". Unique per number. */
    static String letters(long value) {
        StringBuilder sb = new StringBuilder();
        long v = value;
        do {
            sb.append((char) ('a' + (v % 26)));
            v /= 26;
        } while (v > 0);
        sb.append('b');
        sb.reverse();
        sb.setCharAt(0, Character.toUpperCase(sb.charAt(0)));
        return sb.toString();
    }

    /** 10-digit number starting with 9, accepted by the app's examples. */
    public static String phone() {
        return "9" + ThreadLocalRandom.current().nextLong(100_000_000L, 1_000_000_000L);
    }

    public static BookingRequest bookingFor(Event event, int quantity) {
        Customer c = customer();
        return new BookingRequest(event.id(), c.name(), c.email(), c.phone(), quantity);
    }
}
