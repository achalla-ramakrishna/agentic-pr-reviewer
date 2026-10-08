package com.skyroute.booking;

import com.skyroute.booking.BookingDtos.BookingView;
import com.skyroute.booking.BookingDtos.Caller;
import com.skyroute.booking.BookingDtos.CreateBookingRequest;
import com.skyroute.booking.BookingDtos.SegmentRequest;
import com.skyroute.common.config.CacheConfig;
import com.skyroute.common.error.BusinessRuleException;
import com.skyroute.common.error.NotFoundException;
import com.skyroute.common.error.SeatsUnavailableException;
import com.skyroute.flight.CabinInventory;
import com.skyroute.flight.Flight;
import com.skyroute.flight.FlightInventoryRepository;
import com.skyroute.flight.FlightService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Books and cancels reservations.
 *
 * <p>Consistency model: each seat hold is an atomic single-document update
 * ({@link FlightInventoryRepository#holdSeats}). A booking touches several flights
 * plus the bookings collection, so if any later step fails we run a
 * <b>compensating action</b> (release what we held). The alternative — a multi-document
 * ACID transaction — needs a replica set; see docs/04-mongodb-mastery.md §Transactions.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    static final Duration MIN_CONNECTION_TIME = Duration.ofMinutes(45);
    private static final int MAX_LOCATOR_ATTEMPTS = 3;

    private final BookingRepository bookings;
    private final FlightService flightService;
    private final FlightInventoryRepository inventory;
    private final FarePricingService pricing;
    private final RecordLocatorGenerator locators;
    private final Clock clock;

    public BookingService(BookingRepository bookings, FlightService flightService,
                          FlightInventoryRepository inventory, FarePricingService pricing,
                          RecordLocatorGenerator locators, Clock clock) {
        this.bookings = bookings;
        this.flightService = flightService;
        this.inventory = inventory;
        this.pricing = pricing;
        this.locators = locators;
        this.clock = clock;
    }

    @CacheEvict(cacheNames = CacheConfig.FLIGHT_SEARCH, allEntries = true)
    public BookingView create(CreateBookingRequest request, Caller caller) {
        validatePassengerMix(request.passengers());
        Map<String, Flight> flightsById = request.segments().stream()
                .map(SegmentRequest::flightId)
                .distinct()
                .collect(Collectors.toMap(Function.identity(), flightService::get));
        List<Flight> itinerary = request.segments().stream().map(s -> flightsById.get(s.flightId())).toList();
        validateItinerary(itinerary);

        int seats = (int) request.passengers().stream().filter(p -> p.type().occupiesSeat()).count();
        Deque<SegmentRequest> held = new ArrayDeque<>();
        try {
            for (SegmentRequest segment : request.segments()) {
                Flight flight = flightsById.get(segment.flightId());
                if (!inventory.holdSeats(flight.getId(), segment.cabin(), seats)) {
                    throw new SeatsUnavailableException(flight.getFlightNumber(), segment.cabin().name(), seats);
                }
                held.push(segment);
            }
            Booking saved = saveWithUniqueLocator(buildBooking(request, flightsById, caller));
            log.info("Booking {} created by {}: {} pax, {} segment(s), total {} {}", saved.getRecordLocator(),
                    caller.username(), saved.getPassengerCount(), saved.getSegments().size(),
                    saved.getTotalAmount(), saved.getCurrency());
            return BookingView.of(saved);
        } catch (RuntimeException ex) {
            // Compensate in reverse order, then rethrow the ORIGINAL exception.
            held.forEach(s -> releaseQuietly(s, seats));
            throw ex;
        }
    }

    public BookingView get(String recordLocator, Caller caller) {
        return BookingView.of(loadOwned(recordLocator, caller));
    }

    public List<BookingView> listMine(Caller caller, int page, int size) {
        return bookings.findByCreatedByOrderByCreatedAtDesc(caller.username(), PageRequest.of(page, size))
                .stream().map(BookingView::of).toList();
    }

    @CacheEvict(cacheNames = CacheConfig.FLIGHT_SEARCH, allEntries = true)
    public BookingView cancel(String recordLocator, Caller caller) {
        Booking booking = loadOwned(recordLocator, caller);
        booking.cancel(clock.instant());
        // Save FIRST: @Version makes a concurrent second cancel fail here (409) before it
        // can release the seats a second time.
        Booking saved = bookings.save(booking);
        int seats = (int) saved.getPassengers().stream().filter(p -> p.type().occupiesSeat()).count();
        saved.getSegments().forEach(s -> inventory.releaseSeats(s.flightId(), s.cabinClass(), seats));
        log.info("Booking {} cancelled by {}", recordLocator, caller.username());
        return BookingView.of(saved);
    }

    private Booking loadOwned(String recordLocator, Caller caller) {
        Booking booking = bookings.findByRecordLocator(recordLocator.toUpperCase())
                .orElseThrow(() -> new NotFoundException("Booking", recordLocator));
        if (!caller.admin() && !booking.getCreatedBy().equals(caller.username())) {
            // 404, not 403: don't confirm to a stranger that the PNR exists.
            throw new NotFoundException("Booking", recordLocator);
        }
        return booking;
    }

    private Booking buildBooking(CreateBookingRequest request, Map<String, Flight> flightsById, Caller caller) {
        List<BookedSegment> segments = request.segments().stream().map(s -> {
            Flight f = flightsById.get(s.flightId());
            CabinInventory cabin = f.cabin(s.cabin()).orElseThrow(() ->
                    new BusinessRuleException("CABIN_NOT_OFFERED", f.getFlightNumber() + " has no " + s.cabin()));
            BigDecimal amount = pricing.priceSegment(cabin.baseFare(), request.passengers());
            return new BookedSegment(f.getId(), f.getFlightNumber(), f.getOrigin(), f.getDestination(),
                    f.getDepartureTime(), s.cabin(), amount, cabin.currency());
        }).toList();

        List<String> currencies = segments.stream().map(BookedSegment::currency).distinct().toList();
        if (currencies.size() != 1) {
            throw new BusinessRuleException("MIXED_CURRENCY", "All segments must be priced in one currency");
        }
        BigDecimal total = segments.stream().map(BookedSegment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Booking(locators.next(), request.contactEmail(), request.passengers(), segments,
                total, currencies.get(0), caller.username());
    }

    private Booking saveWithUniqueLocator(Booking booking) {
        Booking candidate = booking;
        for (int attempt = 1; ; attempt++) {
            try {
                return bookings.insert(candidate);
            } catch (DuplicateKeyException ex) {
                if (attempt >= MAX_LOCATOR_ATTEMPTS) {
                    throw ex;
                }
                log.warn("Record locator collision on {}, retrying (attempt {})", candidate.getRecordLocator(), attempt);
                candidate = candidate.withRecordLocator(locators.next());
            }
        }
    }

    private void releaseQuietly(SegmentRequest segment, int seats) {
        try {
            inventory.releaseSeats(segment.flightId(), segment.cabin(), seats);
        } catch (RuntimeException releaseFailure) {
            // Don't mask the original error, but make the leaked inventory visible to ops.
            log.error("Failed to release {} seat(s) on {} {} — inventory needs reconciliation",
                    seats, segment.flightId(), segment.cabin(), releaseFailure);
        }
    }

    static void validatePassengerMix(List<Passenger> passengers) {
        Map<PassengerType, Long> byType = passengers.stream()
                .collect(Collectors.groupingBy(Passenger::type, Collectors.counting()));
        long adults = byType.getOrDefault(PassengerType.ADT, 0L);
        if (adults == 0) {
            throw new BusinessRuleException("ADULT_REQUIRED", "At least one adult must travel");
        }
        if (byType.getOrDefault(PassengerType.INF, 0L) > adults) {
            throw new BusinessRuleException("TOO_MANY_INFANTS", "Each lap infant needs an accompanying adult");
        }
    }

    void validateItinerary(List<Flight> itinerary) {
        for (int i = 0; i < itinerary.size(); i++) {
            Flight current = itinerary.get(i);
            if (!current.getStatus().isSellable() || !current.getDepartureTime().isAfter(clock.instant())) {
                throw new BusinessRuleException("FLIGHT_NOT_SELLABLE", current.getFlightNumber() + " can't be sold");
            }
            if (i == 0) {
                continue;
            }
            Flight previous = itinerary.get(i - 1);
            if (!previous.getDestination().equals(current.getOrigin())) {
                throw new BusinessRuleException("BROKEN_ITINERARY",
                        previous.getFlightNumber() + " arrives at " + previous.getDestination()
                                + " but " + current.getFlightNumber() + " departs from " + current.getOrigin());
            }
            if (previous.getArrivalTime().plus(MIN_CONNECTION_TIME).isAfter(current.getDepartureTime())) {
                throw new BusinessRuleException("MIN_CONNECTION_TIME",
                        "Connection at " + current.getOrigin() + " is shorter than "
                                + MIN_CONNECTION_TIME.toMinutes() + " minutes");
            }
        }
    }
}
