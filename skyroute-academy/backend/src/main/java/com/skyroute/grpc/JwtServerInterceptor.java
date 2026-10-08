package com.skyroute.grpc;

import com.skyroute.auth.JwtService;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.springframework.stereotype.Component;

/**
 * gRPC's equivalent of a servlet filter: rejects calls without a valid
 * "authorization: Bearer &lt;jwt&gt;" metadata entry and exposes the caller via {@link #USERNAME}.
 */
@Component
public class JwtServerInterceptor implements ServerInterceptor {

    public static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    public static final Context.Key<String> USERNAME = Context.key("username");

    private final JwtService jwtService;

    public JwtServerInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> call, Metadata headers,
                                                       ServerCallHandler<Q, R> next) {
        String header = headers.get(AUTHORIZATION);
        var principal = header != null && header.startsWith("Bearer ")
                ? jwtService.verify(header.substring(7))
                : java.util.Optional.<JwtService.TokenPrincipal>empty();
        if (principal.isEmpty()) {
            call.close(Status.UNAUTHENTICATED.withDescription("valid bearer token required"), new Metadata());
            return new ServerCall.Listener<>() { };
        }
        Context ctx = Context.current().withValue(USERNAME, principal.get().username());
        return Contexts.interceptCall(ctx, call, headers, next);
    }
}
