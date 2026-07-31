package com.uavcommand.realtime;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** DJI Dock 3 上云握手所需四类应答的离线准备，不含任何真实设备连接或凭证保存。 */
@Service
public class DjiDockHandshakeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DjiDockHandshakeService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DjiCloudApiProperties cloudProperties;
    private final DjiMqttProperties mqttProperties;
    private final DjiDockTopologyRegistry topologyRegistry;

    public DjiDockHandshakeService(DjiCloudApiProperties cloudProperties,
                                   DjiMqttProperties mqttProperties,
                                   DjiDockTopologyRegistry topologyRegistry) {
        this.cloudProperties = cloudProperties;
        this.mqttProperties = mqttProperties;
        this.topologyRegistry = topologyRegistry;
    }

    public String buildConfigReply(String reqTid, String reqBid) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("app_id", cloudProperties.getClientId());
        data.put("app_key", "");
        data.put("app_license", cloudProperties.getClientSecret());
        data.put("ntp_server_host", "ntp.aliyun.com");
        data.put("ntp_server_port", 123);
        return wrapReply(reqTid, reqBid, "config", 0, data);
    }

    public String buildAirportBindStatusReply(String reqTid, String reqBid, int bindStatus) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("bind_status", bindStatus);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("output", output);
        return wrapReply(reqTid, reqBid, "airport_bind_status", 0, data);
    }

    public String buildUpdateTopoReply(String reqTid, String reqBid) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("result", 0);
        return wrapReply(reqTid, reqBid, "update_topo", 0, data);
    }

    public String buildOrganizationGetReply(String reqTid, String reqBid, String gatewaySn) {
        var profile = topologyRegistry.findProfileByGatewaySn(gatewaySn);
        if (profile.isEmpty()) {
            LOGGER.info("org_get: 无匹配待绑定资料 gatewaySn={}", gatewaySn);
            return "";
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("organization_id", profile.get().organizationId());
        data.put("organization_name", profile.get().organizationName());
        return wrapReply(reqTid, reqBid, "airport_organization_get", 0, data);
    }

    public String buildOrganizationBindReply(String reqTid, String reqBid, String gatewaySn, String reqBindingCode) {
        String serverCode = mqttProperties.getDeviceBindingCode();
        // 修复问题3b：服务端未配置绑定码时，不应返回 result=1（设备会认为绑定失败并重试）
        // 与 buildOrganizationGetReply 保持一致，返回空字符串表示"服务端未就绪，不应答"
        if (serverCode.isBlank()) {
            LOGGER.info("org_bind: 服务端绑定码未配置，不应答 gatewaySn={}", gatewaySn);
            return "";
        }
        if (!serverCode.equals(reqBindingCode)) {
            LOGGER.info("org_bind: binding code 不匹配 gatewaySn={}", gatewaySn);
            return wrapReply(reqTid, reqBid, "airport_organization_bind", 1, Map.of());
        }
        var profile = topologyRegistry.findProfileByGatewaySn(gatewaySn);
        if (profile.isEmpty()) {
            LOGGER.info("org_bind: 无匹配待绑定资料 gatewaySn={}", gatewaySn);
            return wrapReply(reqTid, reqBid, "airport_organization_bind", 1, Map.of());
        }
        return wrapReply(reqTid, reqBid, "airport_organization_bind", 0, Map.of());
    }

    private String wrapReply(String tid, String bid, String method, int result, Map<String, Object> data) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("tid", tid == null ? "" : tid);
        root.put("bid", bid == null ? "" : bid);
        root.put("method", method);
        root.put("timestamp", Instant.now().toEpochMilli());
        // 修复问题3a：DJI 官方报文规范要求 result 在根层，不在 data 子层
        root.put("result", result);
        root.put("data", data != null ? data : Map.of());
        try {
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            LOGGER.error("握手应答序列化失败 method={}", method, e);
            return "{}";
        }
    }
}
