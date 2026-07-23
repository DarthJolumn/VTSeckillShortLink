package com.jolumn.livemallseckill.config;

import com.jolumn.livemallcommon.grpc
        .SeckillPushGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcClientConfig {

    @Value("${seckill.grpc.websocket-host:localhost}")
    private String wsHost;

    @Value("${seckill.grpc.websocket-port:9090}")
    private int wsPort;

    @Bean
    SeckillPushGrpc.SeckillPushBlockingStub seckillPushBlockingStub() {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(wsHost, wsPort)
                .usePlaintext()
                .build();
        return SeckillPushGrpc.newBlockingStub(channel);
    }
}
