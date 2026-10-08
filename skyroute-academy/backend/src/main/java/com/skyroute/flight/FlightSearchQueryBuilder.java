package com.skyroute.flight;

import java.time.ZoneOffset;
import org.bson.types.Decimal128;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * Turns search criteria into a MongoDB query. Kept separate from the service
 * (Single Responsibility) so the generated filter document can be unit-tested
 * without a database — see FlightSearchQueryBuilderTest.
 *
 * <p>Equivalent shell query:
 * <pre>
 * db.flights.find({
 *   origin: "DFW", destination: "JFK",
 *   departureTime: { $gte: ISODate("2026-11-01T00:00Z"), $lt: ISODate("2026-11-02T00:00Z") },
 *   status: { $in: ["SCHEDULED", "DELAYED"] },
 *   cabins: { $elemMatch: { cabinClass: "ECONOMY", seatsAvailable: { $gte: 2 }, baseFare: { $lte: 400 } } }
 * }).sort({ departureTime: 1 }).skip(0).limit(20)
 * </pre>
 */
@Component
public class FlightSearchQueryBuilder {

    public Query build(FlightSearchCriteria c) {
        var dayStart = c.date().atStartOfDay(ZoneOffset.UTC).toInstant();
        var dayEnd = c.date().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        // $elemMatch: all cabin conditions must hold for the SAME array element.
        List<Criteria> cabinConditions = new ArrayList<>();
        cabinConditions.add(Criteria.where("cabinClass").is(c.cabin().name()));
        cabinConditions.add(Criteria.where("seatsAvailable").gte(c.passengers()));
        if (c.maxFare() != null) {
            // Compare as Decimal128, the type baseFare is stored as (see CabinInventory).
            cabinConditions.add(Criteria.where("baseFare").lte(new Decimal128(c.maxFare())));
        }

        Criteria criteria = Criteria.where("origin").is(c.origin())
                .and("destination").is(c.destination())
                .and("departureTime").gte(dayStart).lt(dayEnd)
                .and("status").in(FlightStatus.SCHEDULED.name(), FlightStatus.DELAYED.name())
                .and("cabins").elemMatch(new Criteria().andOperator(cabinConditions));

        // Fare/duration sorting depends on the chosen cabin, so FlightService does it in memory;
        // the database always sorts by departure, which the route_departure index serves.
        Query query = Query.query(criteria)
                .with(PageRequest.of(c.page(), c.size(), Sort.by(Sort.Direction.ASC, "departureTime")));
        // Projection: don't ship audit fields we never display.
        query.fields().exclude("createdAt", "updatedAt");
        return query;
    }
}
