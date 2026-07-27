package com.jolumn.vtslwebsocket.config;

import jakarta.websocket.server.ServerEndpointConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import java.util.concurrent.Executors;

@Configuration
public class WebSocketConfig {

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }

    @Bean
    public jakarta.websocket.server.ServerEndpointConfig.Configurator vtConfigurator() {
        return new ServerEndpointConfig.Configurator() {
            @Override
            public void modifyHandshake(ServerEndpointConfig sec,
                                        jakarta.websocket.server.HandshakeRequest request,
                                        jakarta.websocket.HandshakeResponse response) {
                super.modifyHandshake(sec, request, response);
            }
        };
    }
}