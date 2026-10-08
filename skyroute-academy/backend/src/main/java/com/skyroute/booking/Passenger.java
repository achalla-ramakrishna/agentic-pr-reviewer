package com.skyroute.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Embedded in Booking (also used as the request shape — it's a pure value). */
public record Passenger(
        @NotBlank @Size(max = 50) String firstName,
        @NotBlank @Size(max = 50) String lastName,
        @NotNull PassengerType type,
        @Past LocalDate dateOfBirth) {
}
