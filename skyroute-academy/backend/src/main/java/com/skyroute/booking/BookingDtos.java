package com.skyroute.booking;

import com.skyroute.flight.CabinClass;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class BookingDtos {

    private BookingDtos() {
    }

    public record CreateBookingRequest(
            @NotBlank @Email String contactEmail,
            @NotEmpty @Size(max = 9) List<@Valid Passenger> passengers,
            @NotEmpty @Size(max = 4) List<@Valid SegmentRequest> segments) {
    }

    public record SegmentRequest(@NotBlank String flightId, @NotNull CabinClass cabin) {
    }

    public record BookingView(
            String recordLocator, BookingStatus status, String contactEmail,
            List<Passenger> passengers, List<BookedSegment> segments,
            BigDecimal totalAmount, String currency,
            String createdBy, Instant createdAt, Instant cancelledAt) {

        public static BookingView of(Booking b) {
            return new BookingView(b.getRecordLocator(), b.getStatus(), b.getContactEmail(),
                    b.getPassengers(), b.getSegments(), b.getTotalAmount(), b.getCurrency(),
                    b.getCreatedBy(), b.getCreatedAt(), b.getCancelledAt());
        }
    }

    /** Who is calling; built from the Spring Security Authentication in the controller. */
    public record Caller(String username, boolean admin) {
    }
}
