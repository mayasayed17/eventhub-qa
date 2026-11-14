package com.eventhub.framework.api.models;

import java.math.BigDecimal;

/** A booking as returned by the API. Unknown fields are ignored (see {@code Json.MAPPER}). */
public record Booking(
        long id,
        String bookingRef,
        Long eventId,
        String customerName,
        String customerEmail,
        String customerPhone,
        Integer quantity,
        BigDecimal totalPrice,
        String status,
        Event event) {
}
