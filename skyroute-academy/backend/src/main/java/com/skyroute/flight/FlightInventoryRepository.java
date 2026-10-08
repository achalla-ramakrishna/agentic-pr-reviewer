package com.skyroute.flight;

import com.mongodb.client.result.UpdateResult;
import java.math.BigDecimal;
import org.bson.types.Decimal128;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/**
 * Atomic, single-document updates on embedded cabin inventory. These are the
 * heart of "never oversell a seat": the condition and the decrement happen in
 * ONE server-side operation, so two agents can't both take the last seat.
 */
@Repository
public class FlightInventoryRepository {

    private final MongoTemplate mongo;

    public FlightInventoryRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /**
     * <pre>
     * db.flights.updateOne(
     *   { _id: id, status: { $in: ["SCHEDULED","DELAYED"] },
     *     cabins: { $elemMatch: { cabinClass: "ECONOMY", seatsAvailable: { $gte: 2 } } } },
     *   { $inc: { "cabins.$.seatsAvailable": -2 } })
     * </pre>
     * The positional operator {@code $} targets the array element matched by $elemMatch.
     *
     * @return true if seats were held, false if not enough were left (or the flight isn't sellable)
     */
    public boolean holdSeats(String flightId, CabinClass cabin, int seats) {
        Query query = Query.query(Criteria.where("_id").is(flightId)
                .and("status").in(FlightStatus.SCHEDULED, FlightStatus.DELAYED)
                .and("cabins").elemMatch(Criteria.where("cabinClass").is(cabin).and("seatsAvailable").gte(seats)));
        Update update = new Update().inc("cabins.$.seatsAvailable", -seats);
        UpdateResult result = mongo.updateFirst(query, update, Flight.class);
        return result.getModifiedCount() == 1;
    }

    /**
     * Gives seats back, using an <b>arrayFilters</b> identifier instead of the positional {@code $}:
     * <pre>
     * db.flights.updateOne({ _id: id },
     *   { $inc: { "cabins.$[c].seatsAvailable": 2 } },
     *   { arrayFilters: [ { "c.cabinClass": "ECONOMY" } ] })
     * </pre>
     */
    public void releaseSeats(String flightId, CabinClass cabin, int seats) {
        Update update = new Update().inc("cabins.$[c].seatsAvailable", seats)
                .filterArray(Criteria.where("c.cabinClass").is(cabin));
        mongo.updateFirst(Query.query(Criteria.where("_id").is(flightId)), update, Flight.class);
    }

    /** $set on a top-level field; returns false if the flight doesn't exist. */
    public boolean updateStatus(String flightId, FlightStatus status) {
        UpdateResult result = mongo.updateFirst(Query.query(Criteria.where("_id").is(flightId)),
                new Update().set("status", status).currentDate("updatedAt"), Flight.class);
        return result.getMatchedCount() == 1;
    }

    /** $set on one array element via the positional operator. */
    public boolean updateFare(String flightId, CabinClass cabin, BigDecimal newFare) {
        Query query = Query.query(Criteria.where("_id").is(flightId).and("cabins.cabinClass").is(cabin));
        Update update = new Update()
                .set("cabins.$.baseFare", new Decimal128(newFare))
                .currentDate("updatedAt");
        return mongo.updateFirst(query, update, Flight.class).getMatchedCount() == 1;
    }
}
