package com.skyroute.flight;

import java.math.BigDecimal;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * Embedded in {@link Flight}: one entry per cabin. Embedding (not a separate
 * collection) because inventory is always read and updated together with its
 * flight and is bounded (max 4 cabins) — the classic "embed" case.
 *
 * baseFare is stored as Decimal128: Spring Data would otherwise store BigDecimal
 * as a String, which breaks $sum / range queries on money.
 */
public record CabinInventory(
        CabinClass cabinClass,
        int capacity,
        int seatsAvailable,
        @Field(targetType = FieldType.DECIMAL128) BigDecimal baseFare,
        String currency) {
}
