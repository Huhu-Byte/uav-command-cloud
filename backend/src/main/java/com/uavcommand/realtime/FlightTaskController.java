package com.uavcommand.realtime;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 航线任务调度 REST 接口。
 *
 * <p>提供航线任务下发、取消、暂停、恢复、返航、设备属性控制等接口，
 * 前端航线规划页面和任务调度页面调用。路径前缀 /api/v1/flight-tasks。</p>
 */
@RestController
@RequestMapping("/api/v1/flight-tasks")
public class FlightTaskController {
    private final FlightTaskDispatchService dispatchService;
    private final DjiMqttCommandPublisher mqttPublisher;
    private final DemoAuthorizationService demoAuthorizationService;

    public FlightTaskController(
            FlightTaskDispatchService dispatchService,
            DjiMqttCommandPublisher mqttPublisher,
            DemoAuthorizationService demoAuthorizationService
    ) {
        this.dispatchService = dispatchService;
        this.mqttPublisher = mqttPublisher;
        this.demoAuthorizationService = demoAuthorizationService;
    }

    /**
     * 下发航线任务：生成 KMZ → 上传对象存储 → MQTT 三步下发（create/prepare/execute）。
     *
     * @param userName 演示模式用户名（X-Demo-User Header）
     * @param role     演示模式角色（X-Demo-Role Header）
     * @param request  下发请求，包含航线 ID、机场 SN、任务名称
     */
    @PostMapping("/dispatch")
    public FlightTaskDispatchService.DispatchResult dispatch(
            @RequestHeader(name = "X-Demo-User", required = false) String userName,
            @RequestHeader(name = "X-Demo-Role", required = false) String role,
            @RequestBody DispatchRequest request
    ) {
        try {
            String operator = demoAuthorizationService.requireControlOperator(userName, role);
            if (request.routeId() == null) {
                throw new IllegalArgumentException("缺少参数：routeId（航线 ID）");
            }
            if (request.gatewaySn() == null || request.gatewaySn().isBlank()) {
                throw new IllegalArgumentException("缺少参数：gatewaySn（机场网关 SN）");
            }
            return dispatchService.dispatch(
                    request.routeId(),
                    request.gatewaySn(),
                    operator,
                    request.taskName()
            );
        } catch (SecurityException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        } catch (RuntimeException error) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, error.getMessage(), error);
        }
    }

    /** 取消任务（flighttask_undo）。 */
    @PostMapping("/{flightId}/cancel")
    public Map<String, Object> cancel(
            @PathVariable String flightId,
            @RequestHeader(name = "X-Demo-User", required = false) String userName,
            @RequestHeader(name = "X-Demo-Role", required = false) String role,
            @RequestBody GatewaySnRequest request
    ) {
        try {
            demoAuthorizationService.requireControlOperator(userName, role);
            return dispatchService.cancel(flightId, request.gatewaySn());
        } catch (SecurityException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    /** 暂停任务执行（flighttask_pause）。 */
    @PostMapping("/{flightId}/pause")
    public Map<String, Object> pause(
            @PathVariable String flightId,
            @RequestHeader(name = "X-Demo-User", required = false) String userName,
            @RequestHeader(name = "X-Demo-Role", required = false) String role,
            @RequestBody GatewaySnRequest request
    ) {
        try {
            demoAuthorizationService.requireControlOperator(userName, role);
            return mqttPublisher.publishFlighttaskPause(request.gatewaySn(), flightId);
        } catch (SecurityException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    /** 恢复任务执行（flighttask_recovery）。 */
    @PostMapping("/{flightId}/resume")
    public Map<String, Object> resume(
            @PathVariable String flightId,
            @RequestHeader(name = "X-Demo-User", required = false) String userName,
            @RequestHeader(name = "X-Demo-Role", required = false) String role,
            @RequestBody GatewaySnRequest request
    ) {
        try {
            demoAuthorizationService.requireControlOperator(userName, role);
            return mqttPublisher.publishFlighttaskRecovery(request.gatewaySn(), flightId);
        } catch (SecurityException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    /** 返航（return_home）。 */
    @PostMapping("/return-home")
    public Map<String, Object> returnHome(
            @RequestHeader(name = "X-Demo-User", required = false) String userName,
            @RequestHeader(name = "X-Demo-Role", required = false) String role,
            @RequestBody GatewaySnRequest request
    ) {
        try {
            demoAuthorizationService.requireControlOperator(userName, role);
            return mqttPublisher.publishReturnHome(request.gatewaySn());
        } catch (SecurityException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    /** 设置机场属性（开舱盖、急停等）。 */
    @PostMapping("/property/{property}")
    public Map<String, Object> setProperty(
            @PathVariable String property,
            @RequestHeader(name = "X-Demo-User", required = false) String userName,
            @RequestHeader(name = "X-Demo-Role", required = false) String role,
            @RequestBody PropertySetRequest request
    ) {
        try {
            demoAuthorizationService.requireControlOperator(userName, role);
            return mqttPublisher.publishPropertySet(request.gatewaySn(), property, request.value());
        } catch (SecurityException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    /** 下发请求。 */
    public record DispatchRequest(
            Long routeId,
            String gatewaySn,
            String taskName
    ) {}

    /** 机场 SN 请求体。 */
    public record GatewaySnRequest(String gatewaySn) {}

    /** 属性设置请求。 */
    public record PropertySetRequest(String gatewaySn, String value) {}
}
