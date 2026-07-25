package com.jolumn.livemallseckill.config;

import com.jolumn.livemallcommon.grpc
        .SeckillPushGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class GrpcClientConfig {

    @Value("${seckill.grpc.websocket-host:localhost}")
    private String wsHost;

    @Value("${seckill.grpc.websocket-port:9090}")
    private int wsPort;

    private ManagedChannel channel;

    @Bean
    SeckillPushGrpc.SeckillPushBlockingStub seckillPushBlockingStub() {
        channel = ManagedChannelBuilder
                .forAddress(wsHost, wsPort)
                .usePlaintext()
                .build();
        return SeckillPushGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) {
            channel.shutdown();
            try {
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    channel.shutdownNow();
                }
            } catch (InterruptedException e) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
