package com.uavcommand.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

public class DroneStatusWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    public DroneStatusWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    public void broadcast(DroneStatus status) {
        try {
            TextMessage message = new TextMessage(objectMapper.writeValueAsString(status));
            sessions.removeIf(session -> {
                if (!session.isOpen()) return true;
                try {
                    session.sendMessage(message);
                    return false;
                } catch (IOException error) {
                    return true;
                }
            });
        } catch (IOException error) {
            throw new IllegalStateException("无法生成实时状态数据", error);
        }
    }
}
