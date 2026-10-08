package com.skyroute.grpc;

import com.skyroute.flight.CabinClass;
import com.skyroute.flight.FlightDtos.FlightSummary;
import com.skyroute.flight.FlightSearchCriteria;
import com.skyroute.flight.FlightService;
import com.skyroute.grpc.proto.AvailabilityRequest;
import com.skyroute.grpc.proto.AvailabilityResponse;
import com.skyroute.grpc.proto.Cabin;
import com.skyroute.grpc.proto.FlightAvailabilityGrpc;
import com.skyroute.grpc.proto.FlightOption;
import com.skyroute.grpc.proto.Money;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * gRPC adapter over the same FlightService the REST controller uses: transport is a
 * detail, business logic lives in one place. Errors map to gRPC status codes
 * (INVALID_ARGUMENT, INTERNAL) the way REST maps them to HTTP codes.
 */
@Component
public class FlightAvailabilityGrpcService extends FlightAvailabilityGrpc.FlightAvailabilityImplBase {

    private static final Logger log = LoggerFactory.getLogger(FlightAvailabilityGrpcService.class);

    private final FlightService flightService;

    public FlightAvailabilityGrpcService(FlightService flightService) {
        this.flightService = flightService;
    }

    @Override
    public void search(AvailabilityRequest request, StreamObserver<AvailabilityResponse> responseObserver) {
        try {
            List<FlightOption> options = find(request).stream().map(FlightAvailabilityGrpcService::toProto).toList();
            responseObserver.onNext(AvailabilityResponse.newBuilder().addAllOptions(options).build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(ex.getMessage()).asRuntimeException());
        } catch (RuntimeException ex) {
            log.error("gRPC Search failed", ex);
            responseObserver.onError(Status.INTERNAL.withDescription("internal error").asRuntimeException());
        }
    }

    @Override
    public void streamRoute(AvailabilityRequest request, StreamObserver<FlightOption> responseObserver) {
        try {
            find(request).stream().map(FlightAvailabilityGrpcService::toProto).forEach(responseObserver::onNext);
            responseObserver.onCompleted();
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(ex.getMessage()).asRuntimeException());
        } catch (RuntimeException ex) {
            log.error("gRPC StreamRoute failed", ex);
            responseObserver.onError(Status.INTERNAL.withDescription("internal error").asRuntimeException());
        }
    }

    private List<FlightSummary> find(AvailabilityRequest request) {
        if (request.getOrigin().length() != 3 || request.getDestination().length() != 3) {
            throw new IllegalArgumentException("origin and destination must be 3-letter IATA codes");
        }
        var criteria = new FlightSearchCriteria(request.getOrigin(), request.getDestination(),
                LocalDate.parse(request.getDate()), toCabin(request.getCabin()),
                Math.max(1, request.getPassengers()), null, null, 0, 50);
        return flightService.search(criteria);
    }

    static CabinClass toCabin(Cabin cabin) {
        return switch (cabin) {
            case CABIN_UNSPECIFIED, ECONOMY, UNRECOGNIZED -> CabinClass.ECONOMY;
            case PREMIUM_ECONOMY -> CabinClass.PREMIUM_ECONOMY;
            case BUSINESS -> CabinClass.BUSINESS;
            case FIRST -> CabinClass.FIRST;
        };
    }

    static FlightOption toProto(FlightSummary f) {
        return FlightOption.newBuilder()
                .setFlightId(f.id())
                .setFlightNumber(f.flightNumber())
                .setOrigin(f.origin())
                .setDestination(f.destination())
                .setDepartureEpochSeconds(f.departureTime().getEpochSecond())
                .setArrivalEpochSeconds(f.arrivalTime().getEpochSecond())
                .setSeatsAvailable(f.seatsAvailable())
                .setFare(Money.newBuilder().setAmount(f.fare().toPlainString()).setCurrency(f.currency()))
                .setStatus(f.status().name())
                .build();
    }
}
