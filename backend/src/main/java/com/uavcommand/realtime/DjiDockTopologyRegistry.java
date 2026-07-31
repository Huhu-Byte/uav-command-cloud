package com.uavcommand.realtime;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 内存维护 Dock 3 网关与子设备拓扑，不持久化 deviceSecret 或 nonce。
 *
 * <p>修复问题8a：新增 organizationProfiles 内存表，通过 registerProfile() 预注册
 * 网关SN与组织资料的关联，findProfileByGatewaySn() 不再永远返回 empty。<br>
 * 修复问题8b：新增 removeGateway() 方法，设备离线后可清除残留拓扑。</p>
 */
@Component
public class DjiDockTopologyRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(DjiDockTopologyRegistry.class);

    private final Map<String, GatewayNode> gateways = new ConcurrentHashMap<>();
    /** 网关SN → 组织资料，由外部（API接口或配置）调用 registerProfile 预注册。 */
    private final Map<String, OrganizationProfile> organizationProfiles = new ConcurrentHashMap<>();

    public record SubDevice(String sn, String domain, String type, String subType, int index) {}
    public record GatewayNode(String gatewaySn, List<SubDevice> subDevices) {}
    public record OrganizationProfile(String organizationId, String organizationName, String gatewaySn) {}

    public void upsertGateway(String gatewaySn, List<SubDevice> subDevices) {
        gateways.put(gatewaySn, new GatewayNode(gatewaySn, List.copyOf(subDevices)));
        LOGGER.info("注册网关拓扑 gatewaySn={} subDevices={}", gatewaySn, subDevices.size());
    }

    /**
     * 修复问题8b：设备离线时调用此方法清除拓扑，防止长期残留。
     */
    public void removeGateway(String gatewaySn) {
        if (gateways.remove(gatewaySn) != null) {
            LOGGER.info("移除网关拓扑 gatewaySn={}", gatewaySn);
        }
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

    /**
     * 修复问题8a：预注册待绑定设备的组织资料，将网关SN与组织ID/名称关联。
     * 可由 REST 接口（管理员操作）或启动配置调用。
     */
    public void registerProfile(String gatewaySn, String organizationId, String organizationName) {
        if (gatewaySn == null || gatewaySn.isBlank()) return;
        organizationProfiles.put(gatewaySn,
                new OrganizationProfile(organizationId, organizationName, gatewaySn));
        LOGGER.info("注册组织绑定资料 gatewaySn={} org={}", gatewaySn, organizationId);
    }

    /**
     * 修复问题8a：查询该网关SN对应的待绑定组织资料。
     * 原实现永远返回 empty，握手中的 org_get / org_bind 因此永远失败。
     */
    public Optional<OrganizationProfile> findProfileByGatewaySn(String gatewaySn) {
        return Optional.ofNullable(organizationProfiles.get(gatewaySn));
    }
}
