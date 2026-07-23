package com.uavcommand.realtime;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/dashboard")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"})
public class DroneStatusController {
    private final DroneStatusService droneStatusService;
    private final DroneStatusWebSocketHandler webSocketHandler;

    public DroneStatusController(
            DroneStatusService droneStatusService,
            DroneStatusWebSocketHandler webSocketHandler
    ) {
        this.droneStatusService = droneStatusService;
        this.webSocketHandler = webSocketHandler;
    }

    @GetMapping("/status")
    public DroneStatus status() {
        return droneStatusService.currentStatus();
    }

    @PostMapping("/alerts/current/acknowledge")
    public DroneStatus acknowledgeAlert(@RequestBody AlertAcknowledgementRequest request) {
        try {
            DroneStatus status = droneStatusService.acknowledgeCurrentAlert(request.handler(), request.result());
            webSocketHandler.broadcast(status);
            return status;
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        } catch (IllegalStateException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, error.getMessage(), error);
        }
    }

    public record AlertAcknowledgementRequest(String handler, String result) { }
}
