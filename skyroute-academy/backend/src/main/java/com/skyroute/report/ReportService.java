package com.skyroute.report;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.project;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.sort;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.unwind;

import com.skyroute.booking.Booking;
import com.skyroute.booking.BookingStatus;
import com.skyroute.flight.Flight;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.ArithmeticOperators;
import org.springframework.data.mongodb.core.aggregation.Fields;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

/** Analytics via the aggregation pipeline — computed in the database, not in Java loops. */
@Service
public class ReportService {

    private final MongoTemplate mongo;

    public ReportService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public record RouteRevenue(String origin, String destination, BigDecimal revenue,
                               long segmentsSold, long passengers) {
    }

    public record FlightLoad(String flightNumber, String origin, String destination,
                             int capacity, int seatsAvailable, double loadFactor) {
    }

    /**
     * Revenue per route for bookings created in [from, to].
     * <pre>
     * db.bookings.aggregate([
     *   { $match: { status: "CONFIRMED", createdAt: { $gte: from, $lt: to+1 } } },
     *   { $unwind: "$segments" },
     *   { $group: { _id: { origin: "$segments.origin", destination: "$segments.destination" },
     *               revenue: { $sum: "$segments.amount" },
     *               segmentsSold: { $sum: 1 },
     *               passengers: { $sum: "$passengerCount" } } },
     *   { $project: { _id: 0, origin: "$_id.origin", destination: "$_id.destination",
     *                 revenue: 1, segmentsSold: 1, passengers: 1 } },
     *   { $sort: { revenue: -1 } }
     * ])
     * </pre>
     */
    public List<RouteRevenue> routeRevenue(LocalDate from, LocalDate to) {
        return mongo.aggregate(routeRevenueAggregation(from, to), Booking.class, RouteRevenue.class)
                .getMappedResults();
    }

    Aggregation routeRevenueAggregation(LocalDate from, LocalDate to) {
        return newAggregation(
                match(Criteria.where("status").is(BookingStatus.CONFIRMED)
                        .and("createdAt").gte(from.atStartOfDay(ZoneOffset.UTC).toInstant())
                        .lt(to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant())),
                unwind("segments"),
                group(Fields.from(Fields.field("origin", "segments.origin"),
                        Fields.field("destination", "segments.destination")))
                        .sum("segments.amount").as("revenue")
                        .count().as("segmentsSold")
                        .sum("passengerCount").as("passengers"),
                project("revenue", "segmentsSold", "passengers", "origin", "destination").andExclude("_id"),
                sort(Sort.Direction.DESC, "revenue"));
    }

    /** Load factor = seats sold / seats offered, per flight departing on {@code date}. */
    public List<FlightLoad> loadFactor(LocalDate date) {
        return mongo.aggregate(loadFactorAggregation(date), Flight.class, FlightLoad.class).getMappedResults();
    }

    Aggregation loadFactorAggregation(LocalDate date) {
        return newAggregation(
                match(Criteria.where("departureDate").is(date.toString())),
                unwind("cabins"),
                group(Fields.from(Fields.field("flightNumber"), Fields.field("origin"), Fields.field("destination")))
                        .sum("cabins.capacity").as("capacity")
                        .sum("cabins.seatsAvailable").as("seatsAvailable"),
                project("flightNumber", "origin", "destination", "capacity", "seatsAvailable")
                        .and(ArithmeticOperators.Divide.valueOf(
                                        ArithmeticOperators.Subtract.valueOf("capacity").subtract("seatsAvailable"))
                                .divideBy("capacity")).as("loadFactor")
                        .andExclude("_id"),
                sort(Sort.Direction.DESC, "loadFactor"));
    }
}
