package com.skyroute.flight;

import com.skyroute.common.config.CacheConfig;
import com.skyroute.common.error.BusinessRuleException;
import com.skyroute.common.error.NotFoundException;
import com.skyroute.flight.FlightDtos.CreateFlightRequest;
import com.skyroute.flight.FlightDtos.FlightSummary;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
public class FlightService {

    private static final Logger log = LoggerFactory.getLogger(FlightService.class);

    private final FlightRepository flights;
    private final FlightInventoryRepository inventory;
    private final FlightSearchQueryBuilder queryBuilder;
    private final MongoTemplate mongo;

    public FlightService(FlightRepository flights, FlightInventoryRepository inventory,
                         FlightSearchQueryBuilder queryBuilder, MongoTemplate mongo) {
        this.flights = flights;
        this.inventory = inventory;
        this.queryBuilder = queryBuilder;
        this.mongo = mongo;
    }

    /** Cached for 30s (see CacheConfig): popular routes are searched far more than they're booked. */
    @Cacheable(cacheNames = CacheConfig.FLIGHT_SEARCH, key = "#criteria")
    public List<FlightSummary> search(FlightSearchCriteria criteria) {
        List<Flight> found = mongo.find(queryBuilder.build(criteria), Flight.class);
        log.debug("Search {} matched {} flight(s)", criteria, found.size());
        return found.stream()
                .flatMap(f -> f.cabin(criteria.cabin()).map(c -> FlightSummary.of(f, c)).stream())
                .sorted(comparatorFor(criteria.sort()))
                .toList();
    }

    public Flight get(String id) {
        return flights.findById(id).orElseThrow(() -> new NotFoundException("Flight", id));
    }

    @CacheEvict(cacheNames = CacheConfig.FLIGHT_SEARCH, allEntries = true)
    public Flight create(CreateFlightRequest request) {
        if (request.origin().equals(request.destination())) {
            throw new BusinessRuleException("SAME_ORIGIN_DESTINATION", "Origin and destination must differ");
        }
        if (!request.arrivalTime().isAfter(request.departureTime())) {
            throw new BusinessRuleException("INVALID_SCHEDULE", "Arrival must be after departure");
        }
        long distinctCabins = request.cabins().stream().map(FlightDtos.CabinRequest::cabinClass).distinct().count();
        if (distinctCabins != request.cabins().size()) {
            throw new BusinessRuleException("DUPLICATE_CABIN", "Each cabin class may appear only once");
        }
        List<CabinInventory> cabins = request.cabins().stream()
                .map(c -> new CabinInventory(c.cabinClass(), c.capacity(), c.capacity(), c.baseFare(), c.currency()))
                .toList();
        Flight saved = flights.insert(new Flight(request.flightNumber(), request.origin(), request.destination(),
                request.departureTime(), request.arrivalTime(), request.aircraftType(), cabins));
        log.info("Created flight {} {}->{} departing {}", saved.getFlightNumber(), saved.getOrigin(),
                saved.getDestination(), saved.getDepartureTime());
        return saved;
    }

    @CacheEvict(cacheNames = CacheConfig.FLIGHT_SEARCH, allEntries = true)
    public Flight updateStatus(String id, FlightStatus status) {
        if (!inventory.updateStatus(id, status)) {
            throw new NotFoundException("Flight", id);
        }
        log.info("Flight {} status -> {}", id, status);
        return get(id);
    }

    @CacheEvict(cacheNames = CacheConfig.FLIGHT_SEARCH, allEntries = true)
    public Flight updateFare(String id, CabinClass cabin, BigDecimal fare) {
        if (!inventory.updateFare(id, cabin, fare)) {
            throw new NotFoundException("Flight cabin", id + "/" + cabin);
        }
        return get(id);
    }

    static Comparator<FlightSummary> comparatorFor(FlightSearchCriteria.SortBy sortBy) {
        // Switch expression (Java 14+): exhaustive over the enum, no fall-through, no default needed.
        return switch (sortBy) {
            case DEPARTURE -> Comparator.comparing(FlightSummary::departureTime);
            case FARE -> Comparator.comparing(FlightSummary::fare)
                    .thenComparing(FlightSummary::departureTime);
            case DURATION -> Comparator.comparingLong(FlightSummary::durationMinutes)
                    .thenComparing(FlightSummary::departureTime);
        };
    }
}
