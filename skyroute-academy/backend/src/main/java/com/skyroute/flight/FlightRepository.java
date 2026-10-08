package com.skyroute.flight;

import java.time.Instant;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Simple lookups via derived queries. Anything dynamic (optional filters,
 * atomic updates, aggregations) goes through MongoTemplate in {@link FlightInventoryRepository}
 * and {@link FlightSearchQueryBuilder}.
 */
public interface FlightRepository extends MongoRepository<Flight, String> {

    List<Flight> findByOriginAndDestinationAndDepartureTimeBetweenOrderByDepartureTime(
            String origin, String destination, Instant from, Instant to);
}
