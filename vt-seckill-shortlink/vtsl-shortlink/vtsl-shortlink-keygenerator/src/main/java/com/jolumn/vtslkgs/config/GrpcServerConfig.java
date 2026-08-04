package com.jolumn.vtslkgs.config;

import com.jolumn.vtslkgs.grpc.KeyGrpcService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.io.IOException;

@Configuration
public class GrpcServerConfig implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GrpcServerConfig.class);

    private final KeyGrpcService keyGrpcService;
    private final int grpcPort;
    private Server server;

    public GrpcServerConfig(KeyGrpcService keyGrpcService,
                            @Value("${shortly.kgs.grpc-port:50051}") int grpcPort) {
        this.keyGrpcService = keyGrpcService;
        this.grpcPort = grpcPort;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        server = ServerBuilder.forPort(grpcPort)
                .addService(keyGrpcService)
                .build()
                .start();
        log.info("KGS gRPC server started on port {}", grpcPort);
    }

    @PreDestroy
    public void shutdown() {
        if (server != null) {
            server.shutdown();
            log.info("KGS gRPC server shut down");
        }
    }
}
