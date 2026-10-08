package com.skyroute.flight;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Bound from query parameters. A record => immutable, equals/hashCode for free,
 * which also makes it a correct cache key for {@code @Cacheable}.
 */
public record FlightSearchCriteria(
        @NotNull @Pattern(regexp = "[A-Za-z]{3}") String origin,
        @NotNull @Pattern(regexp = "[A-Za-z]{3}") String destination,
        @NotNull LocalDate date,
        CabinClass cabin,
        @Min(1) @Max(9) Integer passengers,
        @Positive BigDecimal maxFare,
        SortBy sort,
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size) {

    public enum SortBy { DEPARTURE, FARE, DURATION }

    /** Canonical constructor normalises input once, so the rest of the code can trust it. */
    public FlightSearchCriteria {
        origin = origin == null ? null : origin.toUpperCase();
        destination = destination == null ? null : destination.toUpperCase();
        cabin = cabin == null ? CabinClass.ECONOMY : cabin;
        passengers = passengers == null ? 1 : passengers;
        sort = sort == null ? SortBy.DEPARTURE : sort;
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }
}
