// mongosh "mongodb://localhost:27017/skyroute" 02-schema-validation.js
// MongoDB is "schema-flexible", not "schema-less": $jsonSchema validation rejects bad writes
// from ANY client (scripts, other services), not just from our Java code.

db.runCommand({
  collMod: "flights",
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["flightNumber", "origin", "destination", "departureTime", "arrivalTime", "status", "cabins"],
      properties: {
        flightNumber: { bsonType: "string", pattern: "^[A-Z0-9]{2}[0-9]{1,4}$" },
        origin: { bsonType: "string", minLength: 3, maxLength: 3 },
        destination: { bsonType: "string", minLength: 3, maxLength: 3 },
        departureTime: { bsonType: "date" },
        arrivalTime: { bsonType: "date" },
        status: { enum: ["SCHEDULED", "DELAYED", "BOARDING", "DEPARTED", "ARRIVED", "CANCELLED"] },
        cabins: {
          bsonType: "array", minItems: 1, maxItems: 4,
          items: {
            bsonType: "object",
            required: ["cabinClass", "capacity", "seatsAvailable", "baseFare", "currency"],
            properties: {
              cabinClass: { enum: ["ECONOMY", "PREMIUM_ECONOMY", "BUSINESS", "FIRST"] },
              capacity: { bsonType: "int", minimum: 1 },
              seatsAvailable: { bsonType: "int", minimum: 0 },   // <- the database itself refuses overselling
              baseFare: { bsonType: "decimal" },
              currency: { bsonType: "string", pattern: "^[A-Z]{3}$" }
            }
          }
        }
      }
    }
  },
  validationLevel: "moderate",   // existing invalid docs can still be updated; new writes must be valid
  validationAction: "error"      // "warn" logs instead of rejecting — useful while rolling out
});
print("flights validator installed");
