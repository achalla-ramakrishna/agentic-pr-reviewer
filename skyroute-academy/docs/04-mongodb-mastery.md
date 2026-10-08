# MongoDB Mastery: From Zero to Expert (with SkyRoute)

This guide assumes you know SQL and Java and are new to MongoDB. Each level
has concepts, the shell syntax, the Spring Data equivalent, and exercises
against the SkyRoute data (`mongo/03-exercises.js`). Work through it in order.

**Contents**
0. [Mental model: SQL → MongoDB](#0-mental-model-sql--mongodb)
1. [Setup and tools](#1-setup-and-tools)
2. [Documents and BSON types](#2-documents-and-bson-types)
3. [Reading: queries, filters, projections](#3-reading-queries-filters-projections)
4. [Writing: inserts and update operators](#4-writing-inserts-and-update-operators)
5. [Data modelling: the skill that matters most](#5-data-modelling-the-skill-that-matters-most)
6. [Atomicity, concurrency and transactions](#6-atomicity-concurrency-and-transactions)
7. [Indexes and query performance](#7-indexes-and-query-performance)
8. [Aggregation pipeline](#8-aggregation-pipeline)
9. [Spring Data MongoDB](#9-spring-data-mongodb)
10. [Production operations](#10-production-operations)
11. [Common mistakes](#11-common-mistakes)
12. [Expert interview questions (with answers)](#12-expert-interview-questions-with-answers)
13. [30-day practice plan](#13-30-day-practice-plan)

---

## 0. Mental model: SQL → MongoDB

| SQL | MongoDB | Note |
|---|---|---|
| database | database | |
| table | collection | No fixed schema (but see `$jsonSchema`, §5.6) |
| row | document (BSON) | Nested objects and arrays are first-class |
| column | field | Can differ per document |
| primary key | `_id` | Always present, unique, immutable; ObjectId by default |
| JOIN | embedding, or `$lookup` | Design so most reads need **no** join |
| foreign key | reference (store the other `_id`) | Not enforced by the DB |
| index | index | B-tree, very similar rules (left-prefix) |
| `GROUP BY` | `$group` in an aggregation pipeline | |
| transaction | single-document ops are atomic; multi-doc transactions exist (replica set) | |
| `ALTER TABLE` | nothing, or a migration script | Schema evolves per document (§5.5 versioning) |

**The big shift:** in SQL you normalise first and then write queries. In MongoDB
you **start from the queries** (access patterns) and shape documents so each hot
query reads one document or one index range.

---

## 1. Setup and tools

```bash
docker compose up -d mongo mongo-express      # from skyroute-academy/
mongosh "mongodb://localhost:27017/skyroute"   # shell
```

- **mongosh**: JavaScript shell. `show dbs`, `use skyroute`, `show collections`,
  `db.flights.findOne()`, `it` (next batch of a cursor).
- **MongoDB Compass**: GUI with schema analysis, an explain-plan visualiser and an aggregation builder.
- **mongo-express** (http://localhost:8081): lightweight web UI from `docker-compose.yml`.
- **IntelliJ database tool window**: add a MongoDB data source and run queries next to your code.
- **Atlas free tier (M0)**: a cloud cluster with Performance Advisor, worth trying.

Why the compose file starts Mongo as a **replica set** (`--replSet rs0`) even with
one node: transactions and change streams need one.

---

## 2. Documents and BSON types

BSON is binary JSON with more types. Choosing the right type is the first
expert habit.

| Type | Shell | Java (Spring Data) | Use for |
|---|---|---|---|
| ObjectId | `ObjectId("…")` | `String`/`ObjectId` `@Id` | Default `_id`: 12 bytes, roughly time-ordered |
| String | `"DFW"` | `String` | Text, natural keys |
| Int32 / Int64 | `NumberInt(5)` / `NumberLong(5)` | `int` / `long` | Counts. JS numbers are doubles by default! |
| Double | `5.5` | `double` | Measurements, **never money** |
| **Decimal128** | `NumberDecimal("189.99")` | `BigDecimal` + `@Field(targetType = DECIMAL128)` | **Money** |
| Date | `ISODate("2026-11-01T07:00:00Z")` | `Instant` / `Date` | Timestamps (stored as UTC milliseconds) |
| Boolean, Null, Array, Object | | | |

SkyRoute lessons:
- Spring Data maps `BigDecimal` to **String** by default, so `$sum` and `$lt` on
  money would break. That's why `CabinInventory.baseFare` has
  `@Field(targetType = FieldType.DECIMAL128)`.
- `LocalDate` is stored as a Date at midnight **in the JVM's time zone**, so two
  servers in different zones would disagree. `Flight.departureDate` is an ISO string instead.
- Max document size: **16 MB**. Design so documents can't grow without bound.
- Field names are stored in every document, so very long names cost disk and RAM at scale.

---

## 3. Reading: queries, filters, projections

```js
db.flights.find(<filter>, <projection>).sort(<spec>).skip(n).limit(n)
db.flights.findOne(<filter>)
db.flights.countDocuments(<filter>)
db.flights.distinct("origin")
```

### 3.1 Comparison and logical operators

```js
{ origin: "DFW" }                                   // equality ($eq implied)
{ departureTime: { $gte: ISODate("2026-11-01"), $lt: ISODate("2026-11-02") } }   // range
{ status: { $in: ["SCHEDULED", "DELAYED"] } }       // IN
{ status: { $nin: ["CANCELLED"] } }                 // NOT IN
{ status: { $ne: "CANCELLED" } }
{ $or: [ { origin: "LHR" }, { destination: "LHR" } ] }
{ $and: [ {...}, {...} ] }                          // usually implicit: {a: 1, b: 2}
{ cancelledAt: { $exists: true } }
{ flightNumber: { $regex: "^SR1" } }                // anchored prefix regex can use an index
{ $expr: { $gt: ["$arrivalTime", "$departureTime"] } }   // compare two fields
```

SQL ↔ Mongo:
```sql
SELECT flightNumber, departureTime FROM flights
WHERE origin='DFW' AND destination='JFK' AND status IN ('SCHEDULED','DELAYED')
ORDER BY departureTime LIMIT 20;
```
```js
db.flights.find({ origin: "DFW", destination: "JFK", status: { $in: ["SCHEDULED","DELAYED"] } },
                { flightNumber: 1, departureTime: 1, _id: 0 })
          .sort({ departureTime: 1 }).limit(20)
```

### 3.2 Querying arrays (critical for embedded data)

```js
{ "cabins.cabinClass": "BUSINESS" }   // ANY element has cabinClass BUSINESS (dot notation)

// TRAP: these conditions may match DIFFERENT elements!
{ "cabins.cabinClass": "BUSINESS", "cabins.seatsAvailable": { $gte: 2 } }

// Correct: ALL conditions on the SAME element
{ cabins: { $elemMatch: { cabinClass: "BUSINESS", seatsAvailable: { $gte: 2 } } } }

{ tags: "wifi" }                       // array contains value
{ tags: { $all: ["wifi", "power"] } }  // contains all
{ cabins: { $size: 3 } }               // exact length (can't use an index; store a count if you query it)
```

This is why `FlightSearchQueryBuilder` uses `elemMatch`. Without it, a flight
with Business at full price *and* a cheap Economy cabin would wrongly match
"Business under $500".

### 3.3 Projection, sort and paging
- Projection `{ field: 1 }` includes and `{ field: 0 }` excludes. Don't mix the two, except for `_id: 0`.
- `$slice` / `$elemMatch` in projections return part of an array.
- `skip` gets slower the larger it gets, because the server walks and discards documents. For deep paging use **range
  (keyset) pagination**: `{ departureTime: { $gt: lastSeen } }` + sort + limit.

**Spring Data:** `Criteria.where("origin").is(o).and("cabins").elemMatch(...)`,
`Query.with(PageRequest.of(page, size, Sort.by("departureTime")))`,
`query.fields().include(...)`. See `FlightSearchQueryBuilder`.

---

## 4. Writing: inserts and update operators

### 4.1 Insert / replace / delete
```js
db.airports.insertOne({ _id: "SIN", name: "Changi", city: "Singapore" })
db.airports.insertMany([...], { ordered: false })    // keep going past duplicate-key errors
db.airports.replaceOne({ _id: "SIN" }, { ...whole new doc... })
db.bookings.deleteOne({ recordLocator: "ABC234" })
db.bookings.deleteMany({ status: "CANCELLED", cancelledAt: { $lt: ISODate("2025-01-01") } })
```

### 4.2 Update operators: change fields, don't replace the document

| Operator | Example | Effect |
|---|---|---|
| `$set` | `{ $set: { status: "DELAYED" } }` | Set fields (creates them if missing) |
| `$unset` | `{ $unset: { legacyField: "" } }` | Remove field |
| `$inc` | `{ $inc: { "cabins.$.seatsAvailable": -2 } }` | Atomic add/subtract |
| `$mul` | `{ $mul: { price: NumberDecimal("1.05") } }` | Multiply |
| `$min` / `$max` | `{ $min: { lowestFare: 99 } }` | Only if new value is lower/higher |
| `$currentDate` | `{ $currentDate: { updatedAt: true } }` | Server timestamp |
| `$rename` | `{ $rename: { "pax": "passengers" } }` | Rename field |
| `$setOnInsert` | with `upsert: true` | Only on insert |
| `$push` (+`$each`, `$slice`, `$sort`) | `{ $push: { history: { $each: [e], $slice: -50 } } }` | Append, cap array |
| `$addToSet` | `{ $addToSet: { roles: "AGENT" } }` | Append if absent |
| `$pull` / `$pop` | `{ $pull: { tags: "wifi" } }` | Remove matching / first/last |

### 4.3 Updating inside arrays: the three positional operators

```js
// $  — the FIRST element matched by the query filter
db.flights.updateOne(
  { _id: id, cabins: { $elemMatch: { cabinClass: "ECONOMY", seatsAvailable: { $gte: 2 } } } },
  { $inc: { "cabins.$.seatsAvailable": -2 } })

// $[] — ALL elements
db.flights.updateOne({ _id: id }, { $set: { "cabins.$[].currency": "EUR" } })

// $[ident] + arrayFilters — all elements matching a separate condition
db.flights.updateMany({ origin: "JFK" },
  { $mul: { "cabins.$[c].baseFare": NumberDecimal("1.05") } },
  { arrayFilters: [ { "c.cabinClass": "BUSINESS" } ] })
```
In SkyRoute: `holdSeats` uses `$`, `releaseSeats` uses `$[c]` + `arrayFilters`
(`Update.filterArray(...)` in Spring).

### 4.4 Upserts, findOneAndUpdate, results
- `updateOne(filter, update, { upsert: true })` inserts if nothing matched.
- `findOneAndUpdate(filter, update, { returnDocument: "after" })` updates and
  returns the document atomically. Spring: `mongoTemplate.findAndModify(q, u, FindAndModifyOptions.options().returnNew(true), X.class)`.
- **Always check the result:** `matchedCount` (filter hit) vs `modifiedCount`
  (something changed). `holdSeats` returns `modifiedCount == 1`; that one check
  is the whole oversell protection.

### 4.5 Pipeline updates (4.2+)
An update can be an aggregation pipeline, which lets you compute from other fields:
```js
db.flights.updateMany({}, [ { $set: { durationMin: { $dateDiff: { startDate: "$departureTime", endDate: "$arrivalTime", unit: "minute" } } } } ])
```

### 4.6 Bulk writes
`db.flights.bulkWrite([{ updateOne: {...} }, { insertOne: {...} }], { ordered: false })`,
or `BulkOperations` in Spring (`mongoTemplate.bulkOps(...)`). Use these for
schedule loads: one round-trip for thousands of operations.

---

## 5. Data modelling: the skill that matters most

### 5.1 Embed vs reference

**Embed when** the data is *owned* by the parent, *bounded*, and *read together*.
**Reference when** it's unbounded, shared by many parents, or updated independently and often.

| Relationship | SkyRoute choice | Why |
|---|---|---|
| Flight → cabins | **Embed** | ≤ 4, always read with the flight, atomic seat updates need the same document |
| Booking → passengers, segments | **Embed** | ≤ 9 / ≤ 4, owned by the PNR, read together |
| Booking → flight | **Reference** (`flightId`) + copied fields | A flight has unbounded bookings; never embed bookings in flights |
| Flight → airport | **Reference** by natural key (`"DFW"`) | Shared reference data, rarely changes |
| User → bookings | **Reference** (`createdBy`) | Unbounded |

**Anti-pattern:** an ever-growing array, like `flight.bookings: [...]`. It
eventually hits 16 MB, every update rewrites a large document, and the indexes on the
array explode.

### 5.2 Core patterns (learn these by name)

| Pattern | Idea | Airline example |
|---|---|---|
| **Extended reference** | Copy the few fields you always need from the referenced doc | `BookedSegment` copies flightNumber/route/time |
| **Subset** | Embed only the most-used N items, keep the rest elsewhere | Last 10 searches on the user, full history in another collection |
| **Computed** | Store precomputed values updated on write | `passengerCount`, `totalAmount`, a daily availability summary |
| **Bucket** | Group many small time-series docs into one per period | Seat-inventory snapshots per flight per hour |
| **Outlier** | Handle rare huge cases separately (`hasOverflow: true`) | A charter PNR with 300 passengers |
| **Polymorphic** | One collection, multiple shapes, with a `type` field | Ancillaries: bag, seat, meal |
| **Schema versioning** | `schemaVersion` field, migrate lazily on read/write | Changing the passenger structure |
| **Attribute** | Turn many sparse fields into `[{k, v}]` to index them once | Aircraft amenities |
| **Archive** | Move cold documents to another collection/cluster | Flown PNRs older than 2 years |

### 5.3 Modelling workflow
1. List the **access patterns** with frequency and latency targets
   ("search route+date: 5k/s, < 50 ms"; "get PNR: 1k/s"; "revenue report: daily").
2. Sketch documents so the top patterns hit **one document or one index range**.
3. Pick indexes for each pattern (§7).
4. Check growth and the 16 MB limit, and work out write contention on hot documents.
5. Decide where duplicated data comes from and how it stays in sync (or deliberately doesn't, like a PNR snapshot).

### 5.4 Many-to-many
Store an array of ids on the side with fewer, bounded links (e.g. `airport.alliances: ["oneworld"]`),
or a junction collection when both sides are large.

### 5.5 Schema evolution without downtime
Add the new field with a default in code → write both → backfill with a
`updateMany` (in batches) → read the new field only → remove the old one. Use a
`schemaVersion` field when the shape changes significantly.

### 5.6 Schema validation
`mongo/02-schema-validation.js` installs a `$jsonSchema` validator on `flights`
(e.g. `seatsAvailable: { minimum: 0 }`, so the database itself refuses negative
inventory). Use `validationAction: "warn"` while rolling out and `"error"` once clean.

---

## 6. Atomicity, concurrency and transactions

### 6.1 Single-document atomicity
**Every write to one document is atomic**, including all embedded arrays and
sub-documents. This is the main reason to embed data that changes together.

### 6.2 Conditional updates (compare-and-set)
Put the precondition in the filter, the change in the update, and check
`modifiedCount`. No locks, no read-modify-write race:
```js
db.flights.updateOne({ _id: id, cabins: { $elemMatch: { cabinClass: "ECONOMY", seatsAvailable: { $gte: n } } } },
                     { $inc: { "cabins.$.seatsAvailable": -n } })
```

### 6.3 Optimistic locking (`@Version`)
For read → change in Java → save flows (`Booking.cancel`), Spring Data adds
`version` to the filter and increments it. If someone else saved first, the filter
matches nothing → `OptimisticLockingFailureException` → 409 in our API.

### 6.4 Multi-document transactions
Needs a **replica set** (or sharded cluster). Use them when several documents must
change all-or-nothing, and keep them short (< 1 s, few documents).

```java
// 1. Register a transaction manager
@Bean
MongoTransactionManager transactionManager(MongoDatabaseFactory factory) {
    return new MongoTransactionManager(factory);
}

// 2. Annotate — every repository/template call inside joins the transaction
@Transactional
public BookingView createTransactional(CreateBookingRequest req, Caller caller) {
    for (SegmentRequest s : req.segments()) {
        if (!inventory.holdSeats(s.flightId(), s.cabin(), seats)) {
            throw new SeatsUnavailableException(...);   // → whole transaction rolls back
        }
    }
    return BookingView.of(bookings.insert(buildBooking(...)));
}
```
Compared with SkyRoute's compensation approach: transactions are simpler to
reason about, but they hold locks, can abort with `WriteConflict` (the driver
retries `TransientTransactionError` in `withTransaction`), and cost more latency.
Many high-volume systems prefer a single-document design plus sagas. Know both
approaches and be able to argue either one.

### 6.5 Write concern, read concern, read preference
- **Write concern** `w: "majority"` (the default since 5.0) means the write survives
  primary failover. `w: 1` is faster but can roll back.
- **Read concern** `"majority"` reads only data that can't be rolled back. `"snapshot"` is for transactions.
- **Read preference** `primary` (default, read-your-writes) vs `secondaryPreferred`
  (offload reports, accept lag). Run the revenue report on a secondary or an analytics node.
- **Retryable writes** (on by default) let the driver retry once on network errors safely.

### 6.6 Idempotency
Clients retry. For `POST /bookings` store an `idempotencyKey` with a unique
index: a retry hits E11000, and you return the existing booking.

---

## 7. Indexes and query performance

### 7.1 Types
| Type | Example | Notes |
|---|---|---|
| Single field | `{ status: 1 }` | |
| **Compound** | `{ origin: 1, destination: 1, departureTime: 1 }` | Order matters (left prefix) |
| Multikey | `{ "segments.flightId": 1 }` | Automatic on arrays; one entry per element |
| Unique | `{ recordLocator: 1 }, { unique: true }` | Enforces business uniqueness |
| **Partial** | `{ createdAt: 1 }, { partialFilterExpression: { status: "CONFIRMED" } }` | Smaller; the query must include the filter |
| Sparse | `{ cancelledAt: 1 }, { sparse: true }` | Only docs that have the field (prefer partial) |
| **TTL** | `{ createdAt: 1 }, { expireAfterSeconds: 900 }` | Auto-delete (seat holds, sessions, refresh tokens) |
| Text / Atlas Search | `{ name: "text" }` | Full-text search |
| Wildcard | `{ "attributes.$**": 1 }` | Unpredictable field names |
| Hashed | `{ _id: "hashed" }` | For hashed sharding |
| 2dsphere | `{ location: "2dsphere" }` | "Airports within 200 km" |

### 7.2 The ESR rule for compound indexes
Order fields as **E**quality → **S**ort → **R**ange.
- Search: `origin = ? AND destination = ? AND departureTime in [day]`, sorted by
  `departureTime`. Index `{ origin: 1, destination: 1, departureTime: 1 }`:
  equality, equality, then sort and range on the same field. That's
  `route_departure` in `Flight`.
- "My bookings": `createdBy = ?` sorted by `createdAt desc` → `{ createdBy: 1, createdAt: -1 }`.

### 7.3 Reading `explain()`
```js
db.flights.find({ origin: "DFW", destination: "JFK" }).sort({ departureTime: 1 }).explain("executionStats")
```
Look at:
- `winningPlan.stage`: **IXSCAN** good; **COLLSCAN** = full scan; **SORT** = in-memory sort (an index doesn't support the sort).
- `executionStats.nReturned` vs `totalKeysExamined` vs `totalDocsExamined`.
  The ideal ratio is about 1:1:1. `docsExamined: 0` means a **covered query**.
- `executionTimeMillis`.

### 7.4 Index costs and hygiene
- Every index slows writes and uses RAM (the working set should fit in memory).
- Use `db.collection.aggregate([{ $indexStats: {} }])` to find unused indexes.
- Build big indexes off-peak (4.2+ builds no longer block for long, but they still use I/O).
- Turn on the **profiler** (`db.setProfilingLevel(1, { slowms: 50 })`) or use Atlas
  Performance Advisor to find slow queries.

### 7.5 Spring Data
`@Indexed(unique = true)`, `@CompoundIndex(def = "{...}")` on documents
(`spring.data.mongodb.auto-index-creation: true` in dev). For production, run
reviewed scripts like `mongo/01-indexes.js`.

---

## 8. Aggregation pipeline

A pipeline is an array of stages. Documents flow through each stage in order.

| Stage | Purpose |
|---|---|
| `$match` | Filter (put it first so it can use indexes) |
| `$project` / `$set` (`$addFields`) / `$unset` | Reshape, compute fields |
| `$group` | Aggregate by key: `$sum $avg $min $max $first $last $push $addToSet $count` |
| `$unwind` | One document per array element |
| `$sort` / `$limit` / `$skip` | |
| `$lookup` | Left outer join to another collection (with optional sub-pipeline) |
| `$facet` | Several sub-pipelines in one pass (dashboards) |
| `$bucket` / `$bucketAuto` | Histogram |
| `$setWindowFields` | Running totals, moving averages, rank (5.0+) |
| `$sortByCount` | group + count + sort shortcut |
| `$out` / `$merge` | Write results to a collection (materialised views) |

**SkyRoute route revenue** (`ReportService.routeRevenue`):
```js
db.bookings.aggregate([
  { $match: { status: "CONFIRMED", createdAt: { $gte: from, $lt: to } } },
  { $unwind: "$segments" },
  { $group: { _id: { origin: "$segments.origin", destination: "$segments.destination" },
              revenue: { $sum: "$segments.amount" }, segmentsSold: { $sum: 1 },
              passengers: { $sum: "$passengerCount" } } },
  { $project: { _id: 0, origin: "$_id.origin", destination: "$_id.destination", revenue: 1, segmentsSold: 1, passengers: 1 } },
  { $sort: { revenue: -1 } }
])
```

**Window function: running revenue per day**
```js
db.bookings.aggregate([
  { $match: { status: "CONFIRMED" } },
  { $group: { _id: { $dateTrunc: { date: "$createdAt", unit: "day" } }, revenue: { $sum: "$totalAmount" } } },
  { $sort: { _id: 1 } },
  { $setWindowFields: { sortBy: { _id: 1 }, output: { runningTotal: { $sum: "$revenue", window: { documents: ["unbounded", "current"] } } } } }
])
```

Performance rules: `$match` and `$sort` early (they use indexes only at the
start of the pipeline), `$project` away unneeded fields early, watch the 100 MB
per-stage memory limit (`allowDiskUse: true` for big reports), and run analytics
on secondaries.

**Spring Data:** `Aggregation.newAggregation(match(...), unwind(...), group(...).sum(...).as(...), project(...), sort(...))`
and `mongoTemplate.aggregate(agg, Input.class, Output.class)`. Results can map
straight into records (`RouteRevenue`). Unit-test the shape with
`agg.toPipeline(Aggregation.DEFAULT_CONTEXT)` (`ReportServiceTest`).

---

## 9. Spring Data MongoDB

### 9.1 Three levels of API
| API | When | Example |
|---|---|---|
| `MongoRepository` derived queries | Simple, fixed lookups | `findByRecordLocator`, `findByCreatedByOrderByCreatedAtDesc(user, Pageable)` |
| `@Query("{json}")` on repository | Fixed but non-trivial query | `AirportRepository.searchByPrefix` |
| `MongoTemplate` + `Criteria/Query/Update/Aggregation` | Dynamic filters, atomic updates, aggregations, bulk | `FlightSearchQueryBuilder`, `FlightInventoryRepository`, `ReportService` |

### 9.2 Mapping annotations
`@Document("flights")`, `@Id`, `@Field("name")` / `@Field(targetType = ...)`,
`@Indexed`, `@CompoundIndex`, `@Version`, `@CreatedDate` / `@LastModifiedDate`
(with `@EnableMongoAuditing`), `@Transient`, `@DocumentReference` (lazy
references, use sparingly). Spring adds a `_class` field for polymorphism; you
can turn it off if you don't need it.

### 9.3 Useful snippets
```java
// Dynamic filter with optional parts
Criteria c = Criteria.where("origin").is(o);
if (maxFare != null) c = c.and("cabins").elemMatch(Criteria.where("baseFare").lte(new Decimal128(maxFare)));

// Atomic update returning the new doc
Flight updated = mongo.findAndModify(Query.query(where("_id").is(id)),
        new Update().set("status", DELAYED).currentDate("updatedAt"),
        FindAndModifyOptions.options().returnNew(true), Flight.class);

// Upsert
mongo.upsert(Query.query(where("_id").is("SIN")), new Update().set("name", "Changi").setOnInsert("country", "SG"), Airport.class);

// Bulk
BulkOperations bulk = mongo.bulkOps(BulkOperations.BulkMode.UNORDERED, Flight.class);
schedule.forEach(f -> bulk.insert(f));
bulk.execute();

// Keyset pagination
Query.query(where("createdBy").is(user).and("createdAt").lt(lastSeen)).with(Sort.by(DESC, "createdAt")).limit(20);

// Count without loading
long n = mongo.count(Query.query(where("status").is("CONFIRMED")), Booking.class);

// See every query Spring sends
logging.level.org.springframework.data.mongodb.core.MongoTemplate: DEBUG
```

### 9.4 Testing
- Unit-test query shapes: `query.getQueryObject()` (`FlightSearchQueryBuilderTest`).
- `@DataMongoTest` + Testcontainers for repository tests.
- `@SpringBootTest` + Testcontainers for flows (`BookingFlowIT`).
- `MongoDBContainer#getReplicaSetUrl()` gives a single-node replica set, so transactions work in tests.

---

## 10. Production operations

- **Replica set:** 1 primary + 2 secondaries (3 data-bearing nodes, or 2 + an
  arbiter only if you must). Automatic failover in seconds. Drivers handle it if
  you use the replica-set connection string.
- **Sharding:** horizontal scale by **shard key**. A good key has high cardinality,
  even distribution, and matches the main query (targeted, not scatter-gather).
  For flights, `{ origin: 1, departureDate: 1 }` or hashed `_id`. Avoid
  monotonically increasing keys (all inserts land on one shard). You can reshard (5.0+), but it's expensive,
  so choose carefully.
- **Change streams:** subscribe to inserts and updates (needs a replica set).
  Example: on a flight status change → notify affected PNRs. Resume tokens give exactly-once processing on your side.
- **Backup:** `mongodump`/`mongorestore` for small data; filesystem snapshots
  or Atlas/Ops Manager continuous backup with point-in-time restore. **Test restores.**
- **Security:** enable auth (SCRAM or x.509), TLS everywhere, least-privilege roles
  (app user: `readWrite` on its own database only), network allow-lists, encryption
  at rest, and field-level / Queryable Encryption for passport data.
- **Monitoring:** `db.serverStatus()`, `mongostat`, `mongotop`, slow-query
  profiler, connection-pool metrics (Micrometer exports driver metrics),
  replication lag, cache (WiredTiger) usage, disk.
- **Connection pool:** one `MongoClient` per application (Spring does this), with
  `maxPoolSize` sized to concurrent requests. Never create a client per request.

---

## 11. Common mistakes

| Mistake | Fix |
|---|---|
| Modelling like SQL (one collection per table, `$lookup` everywhere) | Model from access patterns, embed owned data |
| Unbounded arrays | Reference, or the bucket/subset patterns |
| Multiple conditions on array fields without `$elemMatch` | `$elemMatch` |
| Read → modify in Java → save for counters/inventory | Atomic `$inc` with a conditional filter |
| Money as double or string | Decimal128 |
| Missing indexes / COLLSCAN in production | `explain()`, ESR, profiler |
| Too many indexes | `$indexStats`, drop unused |
| Large `skip` for paging | Keyset pagination |
| Unanchored or user-supplied regex | Anchor `^`, `Pattern.quote` (see `AirportService.search`) |
| Ignoring `matchedCount` / `modifiedCount` | Check them; they tell you whether the precondition held |
| Long transactions | Keep them short, or redesign for single-document atomicity |
| Building queries with string concatenation from user input | Use `Criteria`/driver builders. Operator injection like `{ "$ne": null }` is real |

---

## 12. Expert interview questions (with answers)

1. **How do you guarantee no overselling without transactions?**
   A conditional atomic update on the single document holding inventory: the filter
   checks availability (`$elemMatch … $gte n`), the update decrements (`$inc` with `$`),
   and `modifiedCount` tells you whether you won.

2. **Why `$elemMatch` here?** Without it, the conditions on array fields can be
   satisfied by different elements, and the positional `$` needs the matched element.

3. **When would you use a multi-document transaction?** When several documents
   must change all-or-nothing and the documents can't be redesigned into one: transfers,
   double-entry ledgers, or a booking plus inventory in a strictly consistent system. Keep
   it short, retry on `TransientTransactionError`, and use a replica set.

4. **Design an index for "PNRs by agent, newest first, filtered by status".**
   `{ createdBy: 1, status: 1, createdAt: -1 }`: equality, equality, then sort (ESR).

5. **A query uses an index but is still slow. Why?** Low selectivity (keys examined ≫
   returned), an in-memory SORT stage, fetching large documents (add a projection or covered
   index), the working set not fitting in RAM, or lock/IO contention. Check `explain` and `serverStatus`.

6. **How do you change a field's type across 200M documents?** Make the code read both
   types → batched backfill with a pipeline update (`$convert`) in small ranges by `_id`
   → monitor replication lag → switch reads → remove the old path. Use `schemaVersion`
   if the shape is complex.

7. **How do you pick a shard key for bookings?** Most reads are by `recordLocator`.
   A hashed `recordLocator` (or `_id`) spreads writes and targets point reads. Reports
   go to a secondary or analytics node, or are precomputed.

8. **What's the difference between `w:1` and `w:"majority"`?** With `w:1` the primary
   acknowledges alone and the write can be rolled back after failover. `majority`
   survives failover. Pair it with `readConcern: "majority"` for durability on reads.

9. **Embedding vs `$lookup` performance?** An embedded read is one document fetch. `$lookup` is
   a per-document query into another collection (needs an index on the foreign field)
   and gets more expensive on sharded clusters, where it may have to fan out across shards. Use it for
   occasional reports, not hot paths.

10. **How do TTL indexes work, and their caveats?** A background task runs every
    ~60 s and deletes documents whose date field + `expireAfterSeconds` < now. Deletion
    isn't instant (so queries must still filter out expired items), the field must be a Date
    (or an array of dates), and TTL has traditionally required a single-field index, so check
    what your server version allows before relying on compound TTL.

---

## 13. 30-day practice plan

| Days | Focus | Do |
|---|---|---|
| 1–3 | Shell, BSON, CRUD | §1–3; exercises E1–E4; recreate them in Spring with `Criteria` |
| 4–6 | Update operators | §4; E5–E10; write `FlightInventoryRepository` from scratch without looking |
| 7–9 | Modelling | §5; model a seat map and ancillaries (bags, meals). Write the access patterns first |
| 10–12 | Atomicity & transactions | §6; implement §6.4 with a replica set; run `BookingFlowIT` |
| 13–16 | Indexes | §7; E16–E17; profiler; break and fix the search index |
| 17–20 | Aggregation | §8; E11–E15; add `daily-bookings` and `top-routes` reports |
| 21–23 | Spring Data deep dive | §9; bulk schedule import endpoint with `BulkOperations` |
| 24–26 | Ops | Replica-set failover drill in Docker (stop the primary, watch the app), `mongodump`/`mongorestore`, change stream listener |
| 27–28 | Review | §11 and §12; explain each answer out loud |
| 29–30 | Certify | MongoDB University's free courses + the **MongoDB Associate Developer (Java)** exam |

When you can do the Module 8 checkpoint in `02-course-outline.md` from a blank
whiteboard, you're at a senior level.
