// mongosh "mongodb://localhost:27017/skyroute" 01-indexes.js
// Explicit, reviewable index definitions — the production alternative to auto-index-creation.
// Index builds on big collections are expensive: run them in a maintenance window and watch db.currentOp().

db.flights.createIndex({ origin: 1, destination: 1, departureTime: 1 }, { name: "route_departure" });
db.flights.createIndex({ flightNumber: 1, departureDate: 1 }, { name: "uniq_flight_per_day", unique: true });

db.bookings.createIndex({ recordLocator: 1 }, { name: "recordLocator", unique: true });
db.bookings.createIndex({ createdBy: 1, createdAt: -1 }, { name: "agent_recent" });
db.bookings.createIndex({ "segments.flightId": 1 }, { name: "by_flight" }); // multikey (array field)
// Partial index: only CONFIRMED bookings are queried by the revenue report, so index only those.
db.bookings.createIndex({ createdAt: 1 }, { name: "confirmed_by_date", partialFilterExpression: { status: "CONFIRMED" } });

db.users.createIndex({ username: 1 }, { name: "username", unique: true });

// TTL index: an example "seat hold" collection whose documents expire 15 minutes after creation.
db.seatHolds.createIndex({ createdAt: 1 }, { name: "hold_ttl", expireAfterSeconds: 900 });

printjson(db.flights.getIndexes().map(i => i.name));
printjson(db.bookings.getIndexes().map(i => i.name));
