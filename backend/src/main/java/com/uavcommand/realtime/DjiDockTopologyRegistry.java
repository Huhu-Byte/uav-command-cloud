package com.uavcommand.realtime;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 内存维护 Dock 3 网关与子设备拓扑，不持久化 deviceSecret 或 nonce。 */
@Component
public class DjiDockTopologyRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(DjiDockTopologyRegistry.class);
    private final Map<String, GatewayNode> gateways = new ConcurrentHashMap<>();

    public record SubDevice(String sn, String domain, String type, String subType, int index) {}

    public record GatewayNode(String gatewaySn, List<SubDevice> subDevices) {}

    public record OrganizationProfile(String organizationId, String organizationName, String gatewaySn) {}

    public void upsertGateway(String gatewaySn, List<SubDevice> subDevices) {
        gateways.put(gatewaySn, new GatewayNode(gatewaySn, List.copyOf(subDevices)));
        LOGGER.info("注册网关拓扑 gatewaySn={} subDevices={}", gatewaySn, subDevices.size());
    }

    public Optional<GatewayNode> findGateway(String gatewaySn) {
        return Optional.ofNullable(gateways.get(gatewaySn));
    }

    public Optional<String> resolveDeviceType(String gatewaySn, String deviceSn) {
        var gw = gateways.get(gatewaySn);
        if (gw == null) return Optional.empty();
        return gw.subDevices().stream()
                .filter(d -> d.sn().equals(deviceSn))
                .map(d -> d.type() + "/" + d.subType())
                .findFirst();
    }

    public Optional<OrganizationProfile> findProfileByGatewaySn(String gatewaySn) {
        return Optional.empty();
    }
}
