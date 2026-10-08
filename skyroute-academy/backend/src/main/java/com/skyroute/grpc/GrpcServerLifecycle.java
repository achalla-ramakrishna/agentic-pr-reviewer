package com.skyroute.grpc;

import com.skyroute.common.config.SkyRouteProperties;
import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Runs a Netty gRPC server next to embedded Tomcat, started/stopped with the Spring
 * context. Written by hand (rather than via a starter) so you can see every moving part.
 */
@Component
@ConditionalOnProperty(prefix = "skyroute.grpc", name = "enabled", havingValue = "true")
public class GrpcServerLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(GrpcServerLifecycle.class);

    private final List<BindableService> services;
    private final JwtServerInterceptor jwtInterceptor;
    private final int port;
    private Server server;

    public GrpcServerLifecycle(List<BindableService> services, JwtServerInterceptor jwtInterceptor,
                               SkyRouteProperties properties) {
        this.services = services;
        this.jwtInterceptor = jwtInterceptor;
        this.port = properties.grpc().port();
    }

    @Override
    public void start() {
        NettyServerBuilder builder = NettyServerBuilder.forPort(port);
        services.forEach(s -> builder.addService(ServerInterceptors.intercept(s, jwtInterceptor)));
        try {
            server = builder.build().start();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not start gRPC server on port " + port, ex);
        }
        log.info("gRPC server listening on port {} with {} service(s)", port, services.size());
    }

    @Override
    public void stop() {
        if (server == null) {
            return;
        }
        server.shutdown();
        try {
            if (!server.awaitTermination(10, TimeUnit.SECONDS)) {
                server.shutdownNow();
            }
        } catch (InterruptedException ex) {
            server.shutdownNow();
            Thread.currentThread().interrupt(); // restore the flag — never swallow interrupts
        }
        log.info("gRPC server stopped");
    }

    @Override
    public boolean isRunning() {
        return server != null && !server.isShutdown();
    }
}
