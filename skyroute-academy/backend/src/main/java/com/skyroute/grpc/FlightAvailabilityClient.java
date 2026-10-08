package com.skyroute.grpc;

import com.skyroute.grpc.proto.AvailabilityRequest;
import com.skyroute.grpc.proto.AvailabilityResponse;
import com.skyroute.grpc.proto.FlightAvailabilityGrpc;
import com.skyroute.grpc.proto.FlightOption;
import io.grpc.Channel;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Example consumer showing the two client building blocks:
 * <ul>
 *   <li><b>Channel</b> — the (expensive, long-lived, shareable) connection to a server.
 *       In production: {@code ManagedChannelBuilder.forAddress(host, port).build()}.</li>
 *   <li><b>Stub</b> — the cheap, generated, typed proxy you call methods on. Blocking,
 *       async and future stubs exist; deadlines and headers are attached per stub.</li>
 * </ul>
 * Tests pass an in-process channel instead of a network one (see FlightAvailabilityGrpcServiceTest).
 */
public class FlightAvailabilityClient {

    private final FlightAvailabilityGrpc.FlightAvailabilityBlockingStub stub;

    public FlightAvailabilityClient(Channel channel, String bearerToken) {
        Metadata headers = new Metadata();
        headers.put(JwtServerInterceptor.AUTHORIZATION, "Bearer " + bearerToken);
        this.stub = FlightAvailabilityGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    public AvailabilityResponse search(AvailabilityRequest request) {
        // Always set a deadline: without one a hung server hangs the caller forever.
        return stub.withDeadlineAfter(2, TimeUnit.SECONDS).search(request);
    }

    public List<FlightOption> streamRoute(AvailabilityRequest request) {
        List<FlightOption> options = new ArrayList<>();
        Iterator<FlightOption> it = stub.withDeadlineAfter(5, TimeUnit.SECONDS).streamRoute(request);
        it.forEachRemaining(options::add);
        return options;
    }
}
