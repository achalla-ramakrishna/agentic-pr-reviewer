package com.skyroute.booking;

import com.skyroute.flight.CabinClass;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * A flight inside a booking. Flight number, route and time are COPIED from the
 * flight (extended reference pattern): the PNR must show what was sold even if the
 * schedule later changes, and reading a booking needs no join ($lookup).
 *
 * @param amount total fare for all passengers on this segment
 */
public record BookedSegment(
        String flightId,
        String flightNumber,
        String origin,
        String destination,
        Instant departureTime,
        CabinClass cabinClass,
        @Field(targetType = FieldType.DECIMAL128) BigDecimal amount,
        String currency) {
}
