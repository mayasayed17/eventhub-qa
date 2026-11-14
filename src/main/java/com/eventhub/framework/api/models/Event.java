package com.eventhub.framework.api.models;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * An event as returned by the API. {@code price} is a BigDecimal because the API has been seen returning it
 * as a string ("1500.00"); Jackson coerces both forms, and money is never a double.
 */
public record Event(
        long id,
        String title,
        String category,
        String venue,
        String city,
        String eventDate,
        BigDecimal price,
        Integer totalSeats,
        Integer availableSeats) {

    public boolean isUpcoming() {
        if (eventDate == null || eventDate.isBlank()) {
            return true;
        }
        try {
            return Instant.parse(eventDate).isAfter(Instant.now());
        } catch (DateTimeParseException notInstant) {
            try {
                return OffsetDateTime.parse(eventDate).toInstant().isAfter(Instant.now());
            } catch (DateTimeParseException notOffset) {
                try {
                    return LocalDate.parse(eventDate.substring(0, Math.min(10, eventDate.length())))
                            .atStartOfDay().toInstant(ZoneOffset.UTC).isAfter(Instant.now());
                } catch (DateTimeParseException unknownFormat) {
                    return true;
                }
            }
        }
    }

    public boolean hasSeatsFor(int quantity) {
        return availableSeats != null && availableSeats >= quantity;
    }
}
