package com.skyroute.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.skyroute.auth.JwtService;
import com.skyroute.flight.CabinClass;
import com.skyroute.flight.FlightDtos.FlightSummary;
import com.skyroute.flight.FlightService;
import com.skyroute.flight.FlightStatus;
import com.skyroute.grpc.proto.AvailabilityRequest;
import com.skyroute.grpc.proto.Cabin;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Real gRPC server and client stubs over an <b>in-process channel</b>: same
 * serialization, interceptors and status codes as production, no network ports.
 */
class FlightAvailabilityGrpcServiceTest {

    private final FlightService flightService = mock(FlightService.class);
    private final JwtService jwtService = mock(JwtService.class);
    private Server server;
    private ManagedChannel channel;

    @BeforeEach
    void start() throws Exception {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(ServerInterceptors.intercept(new FlightAvailabilityGrpcService(flightService),
                        new JwtServerInterceptor(jwtService)))
                .build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        when(jwtService.verify("good")).thenReturn(Optional.of(new JwtService.TokenPrincipal("agent", List.of("AGENT"))));
        when(jwtService.verify("bad")).thenReturn(Optional.empty());
    }

    @AfterEach
    void stop() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void unarySearchMapsToProto() {
        when(flightService.search(any())).thenReturn(List.of(summary("SR101"), summary("SR103")));

        var response = new FlightAvailabilityClient(channel, "good").search(request());

        assertThat(response.getOptionsList()).extracting(o -> o.getFlightNumber()).containsExactly("SR101", "SR103");
        assertThat(response.getOptions(0).getFare().getAmount()).isEqualTo("189.00");
    }

    @Test
    void serverStreamingDeliversEachFlight() {
        when(flightService.search(any())).thenReturn(List.of(summary("SR101"), summary("SR103"), summary("SR105")));

        assertThat(new FlightAvailabilityClient(channel, "good").streamRoute(request())).hasSize(3);
    }

    @Test
    void rejectsMissingOrInvalidToken() {
        var client = new FlightAvailabilityClient(channel, "bad");
        assertThatThrownBy(() -> client.search(request()))
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                .isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    @Test
    void invalidArgumentForBadIataCode() {
        var bad = request().toBuilder().setOrigin("DALLAS").build();
        assertThatThrownBy(() -> new FlightAvailabilityClient(channel, "good").search(bad))
                .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    private static AvailabilityRequest request() {
        return AvailabilityRequest.newBuilder()
                .setOrigin("DFW").setDestination("JFK").setDate("2026-11-01")
                .setCabin(Cabin.ECONOMY).setPassengers(1).build();
    }

    private static FlightSummary summary(String number) {
        return new FlightSummary("id-" + number, number, "DFW", "JFK", Instant.parse("2026-11-01T07:00:00Z"),
                Instant.parse("2026-11-01T10:35:00Z"), 215, "B738", FlightStatus.SCHEDULED,
                CabinClass.ECONOMY, 42, new BigDecimal("189.00"), "USD");
    }
}
