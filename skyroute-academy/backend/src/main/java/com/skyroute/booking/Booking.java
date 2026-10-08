package com.skyroute.booking;

import com.skyroute.common.error.BusinessRuleException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * A reservation, identified to the traveller by its 6-character record locator (the "PNR").
 * Passengers and segments are embedded: they're owned by the booking, bounded in size,
 * and always read with it.
 */
@Document("bookings")
@CompoundIndex(name = "agent_recent", def = "{'createdBy': 1, 'createdAt': -1}")
@CompoundIndex(name = "by_flight", def = "{'segments.flightId': 1}")
public class Booking {

    @Id
    private String id;
    @Indexed(unique = true)
    private String recordLocator;
    private BookingStatus status;
    private String contactEmail;
    private List<Passenger> passengers;
    private List<BookedSegment> segments;
    /** Denormalised count of passengers, so reports can $sum it without $size gymnastics. */
    private int passengerCount;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal totalAmount;
    private String currency;
    private String createdBy;
    private Instant cancelledAt;

    @Version
    private Long version;
    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;

    protected Booking() {
        // for Spring Data
    }

    public Booking(String recordLocator, String contactEmail, List<Passenger> passengers,
                   List<BookedSegment> segments, BigDecimal totalAmount, String currency, String createdBy) {
        this.recordLocator = recordLocator;
        this.status = BookingStatus.CONFIRMED;
        this.contactEmail = contactEmail;
        this.passengers = List.copyOf(passengers);
        this.segments = List.copyOf(segments);
        this.passengerCount = passengers.size();
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.createdBy = createdBy;
    }

    /** State transition lives on the entity, so the rule can't be bypassed by another service. */
    public void cancel(Instant now) {
        if (status == BookingStatus.CANCELLED) {
            throw new BusinessRuleException("ALREADY_CANCELLED", "Booking " + recordLocator + " is already cancelled");
        }
        if (segments.stream().anyMatch(s -> !s.departureTime().isAfter(now))) {
            throw new BusinessRuleException("SEGMENT_FLOWN", "Bookings with a departed segment can't be cancelled");
        }
        this.status = BookingStatus.CANCELLED;
        this.cancelledAt = now;
    }

    /** Returns a copy with a different locator; used when a generated PNR collides. */
    Booking withRecordLocator(String newLocator) {
        return new Booking(newLocator, contactEmail, passengers, segments, totalAmount, currency, createdBy);
    }

    public String getId() { return id; }
    public String getRecordLocator() { return recordLocator; }
    public BookingStatus getStatus() { return status; }
    public String getContactEmail() { return contactEmail; }
    public List<Passenger> getPassengers() { return passengers; }
    public List<BookedSegment> getSegments() { return segments; }
    public int getPassengerCount() { return passengerCount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCancelledAt() { return cancelledAt; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
}
