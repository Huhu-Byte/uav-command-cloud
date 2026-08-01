package com.uavcommand.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@EnableConfigurationProperties({ DjiCloudApiProperties.class, DjiMqttProperties.class, OssProperties.class })
public class RealtimeConfiguration implements WebSocketConfigurer {
    private final ObjectMapper objectMapper;
    private final String[] allowedOriginPatterns;

    public RealtimeConfiguration(
            ObjectMapper objectMapper,
            @Value("${app.cors.allowed-origin-patterns}") String allowedOriginPatterns
    ) {
        this.objectMapper = objectMapper;
        this.allowedOriginPatterns = allowedOriginPatterns.split(",");
    }

    @Bean
    public DroneStatusWebSocketHandler droneStatusWebSocketHandler() {
        return new DroneStatusWebSocketHandler(objectMapper);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(droneStatusWebSocketHandler(), "/ws/drone-status")
                .setAllowedOriginPatterns(allowedOriginPatterns);
    }
}
