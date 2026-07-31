package com.uavcommand.realtime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RealtimeStatusPublisher {
    private final DroneStatusService droneStatusService;
    private final DroneStatusWebSocketHandler webSocketHandler;
    private volatile boolean simulatorDisabled;

    public RealtimeStatusPublisher(DroneStatusService droneStatusService, DroneStatusWebSocketHandler webSocketHandler) {
        this.droneStatusService = droneStatusService;
        this.webSocketHandler = webSocketHandler;
    }

    @Scheduled(fixedRate = 3000)
    public void publishStatus() {
        if (simulatorDisabled) return;
        webSocketHandler.broadcast(droneStatusService.nextStatus());
    }

    public void publish(RealtimeStatusSnapshot snapshot) {
        simulatorDisabled = true;
        webSocketHandler.broadcast(snapshot);
    }
}
