package com.skyroute.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skyroute.booking.BookingDtos.Caller;
import com.skyroute.booking.BookingDtos.CreateBookingRequest;
import com.skyroute.booking.BookingDtos.SegmentRequest;
import com.skyroute.common.error.BusinessRuleException;
import com.skyroute.common.error.NotFoundException;
import com.skyroute.common.error.SeatsUnavailableException;
import com.skyroute.flight.CabinClass;
import com.skyroute.flight.CabinInventory;
import com.skyroute.flight.Flight;
import com.skyroute.flight.FlightInventoryRepository;
import com.skyroute.flight.FlightService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
    private static final Caller AGENT = new Caller("agent", false);

    @Mock BookingRepository bookings;
    @Mock FlightService flightService;
    @Mock FlightInventoryRepository inventory;
    @Mock RecordLocatorGenerator locators;

    private BookingService service;
    private Flight dfwJfk;
    private Flight jfkLhr;

    @BeforeEach
    void setUp() {
        FareRules rules = new FareRules();
        var pricing = new FarePricingService(List.of(rules.adultFare(), rules.childFare(), rules.infantFare()));
        service = new BookingService(bookings, flightService, inventory, pricing, locators,
                Clock.fixed(NOW, ZoneOffset.UTC));
        Instant dep = Instant.parse("2026-11-01T07:00:00Z");
        dfwJfk = flight("f1", "SR101", "DFW", "JFK", dep, dep.plus(Duration.ofHours(4)));
        jfkLhr = flight("f2", "SR202", "JFK", "LHR", dep.plus(Duration.ofHours(6)), dep.plus(Duration.ofHours(13)));
    }

    @Test
    void createsBookingAndHoldsSeatsOnEverySegment() {
        when(flightService.get("f1")).thenReturn(dfwJfk);
        when(flightService.get("f2")).thenReturn(jfkLhr);
        when(inventory.holdSeats(any(), any(), any(Integer.class))).thenReturn(true);
        when(locators.next()).thenReturn("ABC234");
        when(bookings.insert(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        var view = service.create(request(adult(), infant()), AGENT);

        assertThat(view.recordLocator()).isEqualTo("ABC234");
        // adult 100% + infant 10% on both legs: (200 + 20) + (500 + 50)
        assertThat(view.totalAmount()).isEqualByComparingTo("770.00");
        // the lap infant does not take a seat
        verify(inventory).holdSeats("f1", CabinClass.ECONOMY, 1);
        verify(inventory).holdSeats("f2", CabinClass.ECONOMY, 1);
    }

    @Test
    void releasesAlreadyHeldSeatsWhenALaterSegmentIsSoldOut() {
        when(flightService.get("f1")).thenReturn(dfwJfk);
        when(flightService.get("f2")).thenReturn(jfkLhr);
        when(inventory.holdSeats("f1", CabinClass.ECONOMY, 1)).thenReturn(true);
        when(inventory.holdSeats("f2", CabinClass.ECONOMY, 1)).thenReturn(false);

        assertThatThrownBy(() -> service.create(request(adult()), AGENT))
                .isInstanceOf(SeatsUnavailableException.class);

        verify(inventory).releaseSeats("f1", CabinClass.ECONOMY, 1); // compensation
        verify(bookings, never()).insert(any(Booking.class));
    }

    @Test
    void retriesWithANewLocatorOnCollision() {
        when(flightService.get("f1")).thenReturn(dfwJfk);
        when(flightService.get("f2")).thenReturn(jfkLhr);
        when(inventory.holdSeats(any(), any(), any(Integer.class))).thenReturn(true);
        when(locators.next()).thenReturn("DUP222", "NEW333");
        when(bookings.insert(any(Booking.class)))
                .thenThrow(new DuplicateKeyException("E11000"))
                .thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.create(request(adult()), AGENT).recordLocator()).isEqualTo("NEW333");
    }

    @Test
    void rejectsConnectionsShorterThanMinimum() {
        Flight tooTight = flight("f2", "SR202", "JFK", "LHR",
                dfwJfk.getArrivalTime().plus(Duration.ofMinutes(30)),
                dfwJfk.getArrivalTime().plus(Duration.ofHours(8)));
        when(flightService.get("f1")).thenReturn(dfwJfk);
        when(flightService.get("f2")).thenReturn(tooTight);

        assertThatThrownBy(() -> service.create(request(adult()), AGENT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("45 minutes");
    }

    @Test
    void requiresAnAdultPerInfant() {
        assertThatThrownBy(() -> BookingService.validatePassengerMix(List.of(adult(), infant(), infant())))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode").isEqualTo("TOO_MANY_INFANTS");
    }

    @Test
    void hidesOtherAgentsBookings() {
        Booking someoneElses = new Booking("XYZ789", "a@b.com", List.of(adult()), List.of(), BigDecimal.TEN,
                "USD", "other-agent");
        when(bookings.findByRecordLocator("XYZ789")).thenReturn(Optional.of(someoneElses));

        assertThatThrownBy(() -> service.get("xyz789", AGENT)).isInstanceOf(NotFoundException.class);
        assertThat(service.get("XYZ789", new Caller("boss", true)).recordLocator()).isEqualTo("XYZ789");
    }

    private static CreateBookingRequest request(Passenger... pax) {
        return new CreateBookingRequest("traveller@example.com", List.of(pax),
                List.of(new SegmentRequest("f1", CabinClass.ECONOMY), new SegmentRequest("f2", CabinClass.ECONOMY)));
    }

    private static Passenger adult() {
        return new Passenger("Ana", "Diaz", PassengerType.ADT, null);
    }

    private static Passenger infant() {
        return new Passenger("Mia", "Diaz", PassengerType.INF, null);
    }

    private static Flight flight(String id, String number, String from, String to, Instant dep, Instant arr) {
        BigDecimal fare = number.equals("SR101") ? new BigDecimal("200.00") : new BigDecimal("500.00");
        Flight f = new Flight(number, from, to, dep, arr, "B738",
                List.of(new CabinInventory(CabinClass.ECONOMY, 150, 150, fare, "USD")));
        ReflectionTestUtils.setField(f, "id", id);
        return f;
    }
}
