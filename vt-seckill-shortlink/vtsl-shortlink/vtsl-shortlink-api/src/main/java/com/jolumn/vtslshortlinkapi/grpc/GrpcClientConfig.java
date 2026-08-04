package com.jolumn.vtslshortlinkapi.grpc;

import com.jolumn.vtslcommon.grpc.key.KeyServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class GrpcClientConfig {

    private static final Logger log = LoggerFactory.getLogger(GrpcClientConfig.class);

    private final ManagedChannel channel;
    private final KeyServiceGrpc.KeyServiceBlockingStub stub;

    public GrpcClientConfig(@Value("${shortly.kgs.grpc-address:localhost:50051}") String grpcAddress) {
        String[] parts = grpcAddress.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);

        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.stub = KeyServiceGrpc.newBlockingStub(channel);
        log.info("KGS gRPC client connected to {}:{}", host, port);
    }

    public KeyServiceGrpc.KeyServiceBlockingStub getStub() {
        return stub;
    }

    @PreDestroy
    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
}
