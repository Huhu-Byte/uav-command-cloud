package com.uavcommand.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class RealtimeConfiguration implements WebSocketConfigurer {
    private final ObjectMapper objectMapper;

    public RealtimeConfiguration(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public DroneStatusWebSocketHandler droneStatusWebSocketHandler() {
        return new DroneStatusWebSocketHandler(objectMapper);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(droneStatusWebSocketHandler(), "/ws/drone-status")
                .setAllowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*");
    }
}
