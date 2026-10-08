package com.skyroute.flight;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One dated flight (a "flight leg" on a specific day), e.g. SR101 DFW->JFK on 2026-11-01.
 *
 * <p>Indexes follow the ESR rule (Equality, Sort, Range): searches filter by
 * origin + destination (equality) and a departure window (range).
 */
@Document("flights")
@CompoundIndex(name = "route_departure", def = "{'origin': 1, 'destination': 1, 'departureTime': 1}")
@CompoundIndex(name = "uniq_flight_per_day", def = "{'flightNumber': 1, 'departureDate': 1}", unique = true)
public class Flight {

    @Id
    private String id;
    private String flightNumber;
    private String carrierCode;
    private String origin;
    private String destination;
    private Instant departureTime;
    private Instant arrivalTime;
    /**
     * Denormalised from departureTime as an ISO string ("2026-11-01", UTC) so "one SR101 per day"
     * can be a unique index. A string, not LocalDate: the driver would store LocalDate as a
     * timestamp at midnight in the JVM's default time zone, which differs between servers.
     */
    private String departureDate;
    private String aircraftType;
    private FlightStatus status = FlightStatus.SCHEDULED;
    private List<CabinInventory> cabins = new ArrayList<>();

    @Version
    private Long version;
    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;

    protected Flight() {
        // for Spring Data
    }

    public Flight(String flightNumber, String origin, String destination, Instant departureTime,
                  Instant arrivalTime, String aircraftType, List<CabinInventory> cabins) {
        this.flightNumber = flightNumber;
        this.carrierCode = flightNumber.substring(0, 2);
        this.origin = origin;
        this.destination = destination;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.departureDate = LocalDate.ofInstant(departureTime, ZoneOffset.UTC).toString();
        this.aircraftType = aircraftType;
        this.cabins = new ArrayList<>(cabins);
    }

    public Optional<CabinInventory> cabin(CabinClass cabinClass) {
        return cabins.stream().filter(c -> c.cabinClass() == cabinClass).findFirst();
    }

    public String getId() { return id; }
    public String getFlightNumber() { return flightNumber; }
    public String getCarrierCode() { return carrierCode; }
    public String getOrigin() { return origin; }
    public String getDestination() { return destination; }
    public Instant getDepartureTime() { return departureTime; }
    public Instant getArrivalTime() { return arrivalTime; }
    public String getDepartureDate() { return departureDate; }
    public String getAircraftType() { return aircraftType; }
    public FlightStatus getStatus() { return status; }
    public List<CabinInventory> getCabins() { return List.copyOf(cabins); }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    void setId(String id) { this.id = id; }
}
