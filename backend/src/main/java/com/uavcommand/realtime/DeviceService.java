package com.uavcommand.realtime;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 设备管理业务服务。
 *
 * <p>负责设备注册、绑定状态管理、与拓扑注册表的联动。
 * 设备入网前需先注册待绑定资料，MQTT 握手成功后更新绑定状态。</p>
 */
@Service
public class DeviceService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceService.class);

    private final DeviceRepository deviceRepository;
    private final DjiDockTopologyRegistry topologyRegistry;

    public DeviceService(DeviceRepository deviceRepository, DjiDockTopologyRegistry topologyRegistry) {
        this.deviceRepository = deviceRepository;
        this.topologyRegistry = topologyRegistry;
    }

    /** 注册待绑定设备资料。 */
    @Transactional
    public DeviceView register(RegisterDeviceRequest request) {
        if (request.gatewaySn() == null || request.gatewaySn().isBlank()) {
            throw new IllegalArgumentException("网关序列号不能为空");
        }
        if (deviceRepository.existsByGatewaySerialNumber(request.gatewaySn())) {
            throw new IllegalStateException("该网关序列号已注册：" + request.gatewaySn());
        }

        DeviceEntity entity = new DeviceEntity(
                request.organizationId(),
                request.organizationName(),
                request.gatewaySn(),
                request.deviceType() != null ? request.deviceType() : "DJI Dock 3",
                request.displayName() != null ? request.displayName() : "Dock " + request.gatewaySn()
        );
        entity = deviceRepository.save(entity);

        // 同步注册到拓扑注册表，供 MQTT 握手使用
        topologyRegistry.registerProfile(request.gatewaySn(), request.organizationId(), request.organizationName());

        LOGGER.info("注册设备 gatewaySn={} org={}", request.gatewaySn(), request.organizationId());
        return toView(entity);
    }

    /** 查询全部设备。 */
    public List<DeviceView> list() {
        return deviceRepository.findAll().stream().map(this::toView).toList();
    }

    /** 查询单个设备。 */
    public DeviceView get(Long id) {
        return deviceRepository.findById(id)
                .map(this::toView)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在 id=" + id));
    }

    /** 按网关序列号查询设备（MQTT 握手时调用）。 */
    public DeviceView findByGatewaySn(String gatewaySn) {
        return deviceRepository.findByGatewaySerialNumber(gatewaySn)
                .map(this::toView)
                .orElse(null);
    }

    /** 更新设备绑定状态（MQTT 握手成功后调用）。 */
    @Transactional
    public void updateBindStatus(String gatewaySn, String nameplateSn, String nameplateSerialNumber) {
        deviceRepository.findByGatewaySerialNumber(gatewaySn).ifPresent(entity -> {
            entity.bindDevice(nameplateSn, nameplateSerialNumber);
            deviceRepository.save(entity);
            LOGGER.info("设备绑定成功 gatewaySn={} nameplateSn={}", gatewaySn, nameplateSn);
        });
    }

    /** 更新设备在线状态。 */
    @Transactional
    public void updateOnlineStatus(String gatewaySn, String status) {
        deviceRepository.findByGatewaySerialNumber(gatewaySn).ifPresent(entity -> {
            entity.updateStatus(status);
            deviceRepository.save(entity);
        });
    }

    /** 删除设备。 */
    @Transactional
    public void delete(Long id) {
        DeviceEntity entity = deviceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在 id=" + id));
        if ("在线".equals(entity.getStatus()) || "已绑定".equals(entity.getStatus())) {
            throw new IllegalStateException("设备已绑定或在线，无法删除");
        }
        deviceRepository.delete(entity);
        LOGGER.info("删除设备 id={} gatewaySn={}", id, entity.getGatewaySerialNumber());
    }

    private DeviceView toView(DeviceEntity entity) {
        return new DeviceView(
                entity.getId(),
                entity.getOrganizationId(),
                entity.getOrganizationName(),
                entity.getMasterSn(),
                entity.getGatewaySerialNumber(),
                entity.getDisplayName(),
                entity.getDeviceType(),
                entity.getStatus(),
                entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null
        );
    }

    /** 设备注册请求。 */
    public record RegisterDeviceRequest(
            String organizationId,
            String organizationName,
            String gatewaySn,
            String deviceType,
            String displayName
    ) {}

    /** 设备视图。 */
    public record DeviceView(
            Long id,
            String organizationId,
            String organizationName,
            String masterSn,
            String gatewaySerialNumber,
            String displayName,
            String deviceType,
            String status,
            String createdAt
    ) {}
}
