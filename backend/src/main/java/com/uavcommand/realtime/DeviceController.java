package com.uavcommand.realtime;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 设备管理 REST 接口。
 *
 * <p>提供设备注册、查询、删除等接口，供前端设备管理页面和 DJI 设备入网流程使用。
 * 路径前缀 /api/v1/devices，独立于 /api/v1/dashboard。</p>
 */
@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {
    private final DeviceService deviceService;
    private final DemoAuthorizationService demoAuthorizationService;

    public DeviceController(DeviceService deviceService, DemoAuthorizationService demoAuthorizationService) {
        this.deviceService = deviceService;
        this.demoAuthorizationService = demoAuthorizationService;
    }

    @GetMapping
    public List<DeviceService.DeviceView> list() {
        return deviceService.list();
    }

    @GetMapping("/{id}")
    public DeviceService.DeviceView get(@PathVariable Long id) {
        try {
            return deviceService.get(id);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, error.getMessage(), error);
        }
    }

    @PostMapping
    public DeviceService.DeviceView register(
            @RequestHeader(name = "X-Demo-User", required = false) String userName,
            @RequestHeader(name = "X-Demo-Role", required = false) String role,
            @RequestBody DeviceService.RegisterDeviceRequest request
    ) {
        try {
            demoAuthorizationService.requireControlOperator(userName, role);
            return deviceService.register(request);
        } catch (SecurityException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        } catch (IllegalStateException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, error.getMessage(), error);
        }
    }

    @DeleteMapping("/{id}")
    public void delete(
            @PathVariable Long id,
            @RequestHeader(name = "X-Demo-User", required = false) String userName,
            @RequestHeader(name = "X-Demo-Role", required = false) String role
    ) {
        try {
            demoAuthorizationService.requireControlOperator(userName, role);
            deviceService.delete(id);
        } catch (SecurityException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, error.getMessage(), error);
        } catch (IllegalStateException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, error.getMessage(), error);
        }
    }
}
