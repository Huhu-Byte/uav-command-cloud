package com.uavcommand.realtime;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DroneStatusController {
    private final DroneStatusService droneStatusService;
    private final DroneStatusWebSocketHandler webSocketHandler;
    private final DemoAuthorizationService demoAuthorizationService;
    private final ActivityHistoryService activityHistoryService;
    private final DjiCloudApiClient djiCloudApiClient;
    private final InspectionTaskService inspectionTaskService;
    private final InspectionResultService inspectionResultService;

    public DroneStatusController(
            DroneStatusService droneStatusService,
            DroneStatusWebSocketHandler webSocketHandler,
            DemoAuthorizationService demoAuthorizationService,
            ActivityHistoryService activityHistoryService,
            DjiCloudApiClient djiCloudApiClient,
            InspectionTaskService inspectionTaskService,
            InspectionResultService inspectionResultService
    ) {
        this.droneStatusService = droneStatusService;
        this.webSocketHandler = webSocketHandler;
        this.demoAuthorizationService = demoAuthorizationService;
        this.activityHistoryService = activityHistoryService;
        this.djiCloudApiClient = djiCloudApiClient;
        this.inspectionTaskService = inspectionTaskService;
        this.inspectionResultService = inspectionResultService;
    }

    @GetMapping("/status")
    public RealtimeStatusSnapshot status() {
        return droneStatusService.currentStatus();
    }

    @PostMapping("/alerts/current/acknowledge")
    public RealtimeStatusSnapshot acknowledgeAlert(
            @RequestHeader(name = "X-Demo-User", required = false) String userName,
            @RequestHeader(name = "X-Demo-Role", required = false) String role,
            @RequestBody AlertAcknowledgementRequest request
    ) {
        try {
            String handler = demoAuthorizationService.requireControlOperator(userName, role);
            RealtimeStatusSnapshot status = droneStatusService.acknowledgeCurrentAlert(handler, request.result());
            webSocketHandler.broadcast(status);
            return status;
        } catch (SecurityException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        } catch (IllegalStateException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, error.getMessage(), error);
        }
    }

    @PostMapping("/return")
    public DroneStatusService.ReturnRequestResult requestReturn(
            @RequestHeader(name = "X-Demo-User", required = false) String userName,
            @RequestHeader(name = "X-Demo-Role", required = false) String role,
            @RequestBody ReturnRequest request
    ) {
        try {
            String operator = demoAuthorizationService.requireControlOperator(userName, role);
            DroneStatusService.ReturnRequestResult result = droneStatusService.requestReturn(operator, request.scenario());
            webSocketHandler.broadcast(result.status());
            return result;
        } catch (SecurityException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        }
    }

    @GetMapping("/tasks")
    public java.util.List<InspectionTaskService.TaskView> tasks() {
        return inspectionTaskService.list();
    }

    @GetMapping("/results")
    public java.util.List<InspectionResultService.ResultView> results() {
        return inspectionResultService.list();
    }

    @PostMapping("/tasks")
    public InspectionTaskService.TaskView createTask(
            @RequestHeader(name = "X-Demo-User", required = false) String userName,
            @RequestHeader(name = "X-Demo-Role", required = false) String role,
            @RequestBody InspectionTaskService.CreateTaskRequest request
    ) {
        try {
            String operator = demoAuthorizationService.requireControlOperator(userName, role);
            return inspectionTaskService.create(operator, request);
        } catch (SecurityException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    @PutMapping("/tasks/{id}")
    public InspectionTaskService.TaskView updateTask(
            @org.springframework.web.bind.annotation.PathVariable Long id,
            @RequestHeader(name = "X-Demo-User", required = false) String userName,
            @RequestHeader(name = "X-Demo-Role", required = false) String role,
            @RequestBody InspectionTaskService.CreateTaskRequest request
    ) {
        try {
            demoAuthorizationService.requireControlOperator(userName, role);
            return inspectionTaskService.update(id, request);
        } catch (SecurityException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        } catch (IllegalStateException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, error.getMessage(), error);
        }
    }

    @GetMapping("/history")
    public java.util.List<DroneStatus.ActivityRecord> activityHistory(
            @RequestParam(defaultValue = "ALL") String type,
            @RequestParam(defaultValue = "ALL") String result
    ) {
        try {
            return activityHistoryService.history(type, result);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    @GetMapping("/integration/dji/readiness")
    public DjiCloudApiClient.Readiness djiReadiness() {
        return djiCloudApiClient.readiness();
    }

    public record AlertAcknowledgementRequest(String result) { }
    public record ReturnRequest(String scenario) { }
}
