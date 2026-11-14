package com.eventhub.framework.api.models;

/**
 * Body of {@code POST /bookings}. Null fields are omitted from the JSON, which is how negative tests
 * send "missing field" payloads. Use {@link com.eventhub.framework.data.TestData#bookingFor} for valid defaults.
 */
public record BookingRequest(
        Long eventId,
        String customerName,
        String customerEmail,
        String customerPhone,
        Integer quantity) {

    public BookingRequest withEventId(Long value) {
        return new BookingRequest(value, customerName, customerEmail, customerPhone, quantity);
    }

    public BookingRequest withCustomerName(String value) {
        return new BookingRequest(eventId, value, customerEmail, customerPhone, quantity);
    }

    public BookingRequest withCustomerEmail(String value) {
        return new BookingRequest(eventId, customerName, value, customerPhone, quantity);
    }

    public BookingRequest withCustomerPhone(String value) {
        return new BookingRequest(eventId, customerName, customerEmail, value, quantity);
    }

    public BookingRequest withQuantity(Integer value) {
        return new BookingRequest(eventId, customerName, customerEmail, customerPhone, value);
    }
}
