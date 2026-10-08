# Calling the gRPC API

The server listens on `localhost:9090` and needs a JWT, sent as `authorization` metadata.
Get a token from `POST /api/v1/auth/login` first.

## grpcurl (CLI)

```bash
TOKEN=$(curl -s localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"agent","password":"agent123"}' | jq -r .accessToken)

# Server reflection is not enabled, so point grpcurl at the .proto file.
grpcurl -plaintext -import-path backend/src/main/proto -proto flight_availability.proto \
  -H "authorization: Bearer $TOKEN" \
  -d '{"origin":"DFW","destination":"JFK","date":"2026-11-01","cabin":"ECONOMY","passengers":1}' \
  localhost:9090 skyroute.v1.FlightAvailability/Search

# Server streaming: one JSON message per flight
grpcurl -plaintext -import-path backend/src/main/proto -proto flight_availability.proto \
  -H "authorization: Bearer $TOKEN" \
  -d '{"origin":"DFW","destination":"JFK","date":"2026-11-01"}' \
  localhost:9090 skyroute.v1.FlightAvailability/StreamRoute
```

## Postman / Bruno

Both clients support gRPC. In Postman: **New → gRPC**, URL `localhost:9090`,
import `flight_availability.proto`, and add metadata `authorization: Bearer <token>`.
