package com.skyroute.flight;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** API-facing shapes for flights: request bodies and the flattened per-cabin search result. */
public final class FlightDtos {

    private FlightDtos() {
    }

    public record FlightSummary(
            String id, String flightNumber, String origin, String destination,
            Instant departureTime, Instant arrivalTime, long durationMinutes,
            String aircraftType, FlightStatus status,
            CabinClass cabin, int seatsAvailable, BigDecimal fare, String currency) {

        static FlightSummary of(Flight f, CabinInventory c) {
            return new FlightSummary(f.getId(), f.getFlightNumber(), f.getOrigin(), f.getDestination(),
                    f.getDepartureTime(), f.getArrivalTime(),
                    Duration.between(f.getDepartureTime(), f.getArrivalTime()).toMinutes(),
                    f.getAircraftType(), f.getStatus(),
                    c.cabinClass(), c.seatsAvailable(), c.baseFare(), c.currency());
        }
    }

    public record CreateFlightRequest(
            @NotBlank @Pattern(regexp = "[A-Z0-9]{2}\\d{1,4}") String flightNumber,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String origin,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String destination,
            @NotNull @Future Instant departureTime,
            @NotNull @Future Instant arrivalTime,
            @NotBlank String aircraftType,
            @NotEmpty List<@Valid CabinRequest> cabins) {
    }

    public record CabinRequest(
            @NotNull CabinClass cabinClass,
            @Min(1) int capacity,
            @NotNull @Positive BigDecimal baseFare,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) {
    }

    public record UpdateStatusRequest(@NotNull FlightStatus status) {
    }

    public record UpdateFareRequest(@NotNull @Positive BigDecimal baseFare) {
    }
}
