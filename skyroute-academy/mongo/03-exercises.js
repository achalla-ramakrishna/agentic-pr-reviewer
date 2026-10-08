// MongoDB practice set for SkyRoute. Start the app once (it seeds data), then:
//   mongosh "mongodb://localhost:27017/skyroute"
// and paste each exercise. Try it yourself first; the answer is underneath.
// Use .explain("executionStats") on any find/aggregate to see which index was used.

// ---------- Level 1: CRUD & filters ----------
// E1. All DFW->JFK flights, departure ascending, only flightNumber + departureTime.
db.flights.find({ origin: "DFW", destination: "JFK" }, { _id: 0, flightNumber: 1, departureTime: 1 })
  .sort({ departureTime: 1 }).limit(5);

// E2. Flights with at least 10 BUSINESS seats left AND business fare under 1000.
//     (Why $elemMatch? Without it, the two conditions could match DIFFERENT cabins.)
db.flights.find({ cabins: { $elemMatch: { cabinClass: "BUSINESS", seatsAvailable: { $gte: 10 }, baseFare: { $lt: NumberDecimal("1000") } } } }).count();

// E3. Flights departing in the next 48 hours that are not CANCELLED.
db.flights.find({ departureTime: { $gte: new Date(), $lt: new Date(Date.now() + 48 * 3600e3) }, status: { $ne: "CANCELLED" } });

// E4. $in / $or / $exists / $regex
db.flights.find({ $or: [{ origin: { $in: ["LHR", "NRT"] } }, { flightNumber: /^SR5/ }] });
db.bookings.find({ cancelledAt: { $exists: true } });

// ---------- Level 2: updates ----------
// E5. Delay SR101 tomorrow: $set status and audit timestamp.
db.flights.updateOne({ flightNumber: "SR101", departureDate: new Date(Date.now() + 864e5).toISOString().slice(0, 10) },
  { $set: { status: "DELAYED" }, $currentDate: { updatedAt: true } });

// E6. Atomically sell 2 economy seats only if 2 are left (positional $).
db.flights.updateOne(
  { flightNumber: "SR101", cabins: { $elemMatch: { cabinClass: "ECONOMY", seatsAvailable: { $gte: 2 } } } },
  { $inc: { "cabins.$.seatsAvailable": -2 } });

// E7. Raise every BUSINESS fare on JFK-LHR by 5% (arrayFilters + $mul).
db.flights.updateMany({ origin: "JFK", destination: "LHR" },
  { $mul: { "cabins.$[c].baseFare": NumberDecimal("1.05") } },
  { arrayFilters: [{ "c.cabinClass": "BUSINESS" }] });

// E8. Upsert an airport; $setOnInsert only applies when the document is created.
db.airports.updateOne({ _id: "SIN" },
  { $set: { name: "Changi" }, $setOnInsert: { city: "Singapore", country: "SG", timeZone: "Asia/Singapore" } },
  { upsert: true });

// E9. Array operators: $push with $each/$slice, $addToSet, $pull.
db.users.updateOne({ username: "agent" }, { $addToSet: { roles: "AGENT" } });
db.users.updateOne({ username: "agent" }, { $push: { recentSearches: { $each: ["DFW-JFK"], $slice: -10 } } });
db.users.updateOne({ username: "agent" }, { $pull: { recentSearches: "DFW-JFK" } });

// E10. findOneAndUpdate returning the NEW document (what Spring's findAndModify does).
db.flights.findOneAndUpdate({ flightNumber: "SR202" }, { $set: { aircraftType: "B78X" } }, { returnDocument: "after" });

// ---------- Level 3: aggregation ----------
// E11. Seats offered per route per day.
db.flights.aggregate([
  { $unwind: "$cabins" },
  { $group: { _id: { route: { $concat: ["$origin", "-", "$destination"] }, day: "$departureDate" }, seats: { $sum: "$cabins.capacity" } } },
  { $sort: { "_id.day": 1, seats: -1 } },
  { $limit: 10 }
]);

// E12. Average economy fare per origin, rounded.
db.flights.aggregate([
  { $unwind: "$cabins" },
  { $match: { "cabins.cabinClass": "ECONOMY" } },
  { $group: { _id: "$origin", avgFare: { $avg: "$cabins.baseFare" }, flights: { $sum: 1 } } },
  { $project: { avgFare: { $round: ["$avgFare", 2] }, flights: 1 } }
]);

// E13. $lookup (join): bookings with the CURRENT status of their first flight.
db.bookings.aggregate([
  { $limit: 5 },
  { $lookup: { from: "flights", let: { fid: { $toObjectId: { $first: "$segments.flightId" } } },
               pipeline: [{ $match: { $expr: { $eq: ["$_id", "$$fid"] } } }, { $project: { status: 1, flightNumber: 1 } }],
               as: "flight" } },
  { $project: { recordLocator: 1, flight: { $first: "$flight" } } }
]);

// E14. $facet: one round-trip, several dashboards.
db.flights.aggregate([
  { $facet: {
      byStatus: [{ $group: { _id: "$status", n: { $sum: 1 } } }],
      byAircraft: [{ $sortByCount: "$aircraftType" }],
      longestRoutes: [{ $project: { flightNumber: 1, minutes: { $dateDiff: { startDate: "$departureTime", endDate: "$arrivalTime", unit: "minute" } } } },
                      { $sort: { minutes: -1 } }, { $limit: 3 }]
  } }
]);

// E15. $bucket fares into price bands.
db.flights.aggregate([
  { $unwind: "$cabins" },
  { $bucket: { groupBy: { $toDouble: "$cabins.baseFare" }, boundaries: [0, 200, 500, 1000, 5000], default: "5000+", output: { count: { $sum: 1 } } } }
]);

// ---------- Level 4: performance ----------
// E16. Compare plans: COLLSCAN vs IXSCAN. Look at totalDocsExamined vs nReturned.
db.flights.find({ origin: "DFW", destination: "JFK" }).sort({ departureTime: 1 }).explain("executionStats").executionStats;
db.flights.find({ aircraftType: "B738" }).explain("executionStats").executionStats; // no index -> COLLSCAN
// E17. Covered query: answered from the index alone (totalDocsExamined: 0).
db.bookings.find({ recordLocator: "ABC234" }, { _id: 0, recordLocator: 1 }).explain("executionStats").executionStats;
