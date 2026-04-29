package com.simulation.producer.gateway.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@ConditionalOnProperty(name = "app.gateway.grpc.enabled", havingValue = "true")
public class GrpcGatewayServerLifecycle {
    private final GrpcIngestService grpcIngestService;
    private final int grpcPort;

    private Server server;

    public GrpcGatewayServerLifecycle(
            GrpcIngestService grpcIngestService,
            @Value("${app.gateway.grpc.port}") int grpcPort
    ) {
        this.grpcIngestService = grpcIngestService;
        this.grpcPort = grpcPort;
    }

    @PostConstruct
    public void start() throws IOException {
        server = ServerBuilder.forPort(grpcPort)
                .addService(grpcIngestService)
                .build()
                .start();
        System.out.println("gRPC gateway listening on port " + grpcPort);
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            server.shutdown();
            System.out.println("gRPC gateway stopped");
        }
    }
}