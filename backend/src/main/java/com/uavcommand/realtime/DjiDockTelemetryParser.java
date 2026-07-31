package com.uavcommand.realtime;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 修复问题2a：parseStatus 不再是空壳，处理 device_online / device_offline 事件，
 * 使网关上下线状态能在前端正确反映。<br>
 * 修复问题2c：activeAlerts 增加基于时间的过期清理，告警超过 ALERT_TTL_MINUTES 后自动移除。
 */
@Component
public class DjiDockTelemetryParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(DjiDockTelemetryParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_ALERTS = 20;
    /** 告警保留时长（分钟），超过此时间的告警在下次事件到达时清除。 */
    private static final long ALERT_TTL_MINUTES = 60;

    private final DjiDockTopologyRegistry topologyRegistry;
    private final RealtimeStatusPublisher publisher;
    private final Map<String, String> deviceStates = new ConcurrentHashMap<>();
    private final List<TimestampedAlert> activeAlerts = Collections.synchronizedList(new ArrayList<>());
    private volatile String lastOsdTopic;
    private volatile String lastOsdPayload;

    public DjiDockTelemetryParser(DjiDockTopologyRegistry topologyRegistry, RealtimeStatusPublisher publisher) {
        this.topologyRegistry = topologyRegistry;
        this.publisher = publisher;
    }

    @SuppressWarnings("unchecked")
    public void parseOsd(String topic, String payload) {
        try {
            Map<String, Object> root = MAPPER.readValue(payload, Map.class);
            Map<String, Object> data = (Map<String, Object>) root.get("data");
            if (data == null) { LOGGER.warn("OSD 报文缺少 data 层 topic={}", topic); return; }
            String gatewaySn = extractSn(topic);
            double lat = getDouble(data, "latitude", -91);
            double lng = getDouble(data, "longitude", -181);
            double alt = getDouble(data, "altitude", -1);
            int battery = (int) getDouble(data, "battery", -1);
            if (lat < -90 || lat > 90 || lng < -180 || lng > 180 || alt < 0 || battery < 0) {
                LOGGER.warn("OSD 网关遥测值超出范围 gatewaySn={}", gatewaySn); return;
            }

            String now = Instant.now().toString();
            List<RealtimeStatusSnapshot.DeviceState> devices = List.of(
                new RealtimeStatusSnapshot.DeviceState(gatewaySn, "Dock " + gatewaySn, "ONLINE",
                    deviceStates.getOrDefault(gatewaySn, "IDLE"), battery, (int) alt, now)
            );

            RealtimeStatusSnapshot snapshot = new RealtimeStatusSnapshot(
                RealtimeStatusSnapshot.SCHEMA_VERSION,
                new RealtimeStatusSnapshot.SourceMetadata("dji-dock", gatewaySn, "DJI Dock 3", now),
                now,
                60,
                new RealtimeStatusSnapshot.MissionStatus("", "", "EXECUTING", gatewaySn, 0, 0, 0, (int) alt, "--"),
                new RealtimeStatusSnapshot.DashboardSummary(1, 0, 0, 0),
                devices,
                currentAlerts(),
                new RealtimeStatusSnapshot.ControlCommand("", "", "IDLE", gatewaySn, "待命", "", 0, null)
            );

            this.lastOsdTopic = topic;
            this.lastOsdPayload = payload;
            publisher.publish(snapshot);
        } catch (Exception e) { LOGGER.warn("OSD 解析失败 topic={}", topic, e); }
    }

    @SuppressWarnings("unchecked")
    public void parseState(String topic, String payload) {
        try {
            Map<String, Object> root = MAPPER.readValue(payload, Map.class);
            Map<String, Object> data = (Map<String, Object>) root.get("data");
            if (data == null) return;
            String sn = extractSn(topic);
            if (data.containsKey("cover_state")) {
                String cover = String.valueOf(data.get("cover_state"));
                String emergency = String.valueOf(data.getOrDefault("emergency_stop_state", "0"));
                deviceStates.put(sn, "1".equals(emergency) ? "急停已触发" : "1".equals(cover) ? "舱盖已打开" : "就绪");
            } else if (data.containsKey("flight_mode")) {
                deviceStates.put(sn, mapFlightMode(String.valueOf(data.get("flight_mode"))));
            }
            refreshLatestSnapshot();
        } catch (Exception e) { LOGGER.warn("state 解析失败 topic={}", topic, e); }
    }

    @SuppressWarnings("unchecked")
    public void parseEvent(String topic, String payload) {
        try {
            Map<String, Object> root = MAPPER.readValue(payload, Map.class);
            String method = root != null ? (String) root.get("method") : null;
            if (method == null) return;
            String title = mapEventTitle(method);
            String level = "hms".equals(method) ? "HIGH" : "INFO";
            String sn = extractSn(topic);
            String now = Instant.now().toString();
            var alert = new RealtimeStatusSnapshot.AlertState(
                method, true, level, title, "", now, sn, sn, false, "待处理", "", "", ""
            );
            synchronized (activeAlerts) {
                // 修复问题2c：先清除超过 TTL 的旧告警，再添加新告警
                evictExpiredAlerts();
                activeAlerts.add(0, new TimestampedAlert(Instant.now(), alert));
                while (activeAlerts.size() > MAX_ALERTS) activeAlerts.remove(activeAlerts.size() - 1);
            }
            refreshLatestSnapshot();
        } catch (Exception e) { LOGGER.warn("event 解析失败 topic={}", topic, e); }
    }

    /**
     * 修复问题2a：parseStatus 处理握手协议之外的设备上下线通知。
     * DJI Dock 3 在设备上线/离线时通过 sys/product/{sn}/status 发送 device_online/device_offline，
     * 原实现只打了一行 debug 日志，导致离线状态无法更新到 deviceStates。
     */
    @SuppressWarnings("unchecked")
    public void parseStatus(String topic, String payload) {
        try {
            Map<String, Object> root = MAPPER.readValue(payload, Map.class);
            if (root == null) return;
            String method = (String) root.get("method");
            String sn = extractSn(topic);
            if ("device_online".equals(method)) {
                deviceStates.putIfAbsent(sn, "IDLE");
                LOGGER.info("设备上线 sn={}", sn);
                refreshLatestSnapshot();
            } else if ("device_offline".equals(method)) {
                deviceStates.put(sn, "OFFLINE");
                // 修复问题8b联动：设备离线时同步清除拓扑
                topologyRegistry.removeGateway(sn);
                LOGGER.info("设备离线 sn={}", sn);
                refreshLatestSnapshot();
            } else {
                LOGGER.debug("status 报文收到 method={} topic={}", method, topic);
            }
        } catch (Exception e) {
            LOGGER.warn("status 解析失败 topic={}", topic, e);
        }
    }

    public void refreshLatestSnapshot() {
        if (lastOsdTopic != null && lastOsdPayload != null) parseOsd(lastOsdTopic, lastOsdPayload);
    }

    /** 修复问题2c：返回当前有效（未过期）的告警快照。 */
    private List<RealtimeStatusSnapshot.AlertState> currentAlerts() {
        synchronized (activeAlerts) {
            evictExpiredAlerts();
            return activeAlerts.stream().map(TimestampedAlert::alert).toList();
        }
    }

    /** 修复问题2c：移除超过 ALERT_TTL_MINUTES 的过期告警。 */
    private void evictExpiredAlerts() {
        Instant cutoff = Instant.now().minus(ALERT_TTL_MINUTES, ChronoUnit.MINUTES);
        activeAlerts.removeIf(ta -> ta.createdAt().isBefore(cutoff));
    }

    private record TimestampedAlert(Instant createdAt, RealtimeStatusSnapshot.AlertState alert) {}

    private String extractSn(String topic) {
        String[] parts = topic.split("/");
        return parts.length >= 3 ? parts[2] : "unknown";
    }

    private double getDouble(Map<String, Object> map, String key, double fallback) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(val)); } catch (Exception e) { return fallback; }
    }

    private String mapFlightMode(String mode) {
        return switch (mode) {
            case "0" -> "手动操控";
            case "1" -> "起飞中";
            case "2" -> "降落中";
            case "3" -> "航线飞行";
            case "4" -> "悬停中";
            case "5" -> "返航中";
            default  -> "飞行模式:" + mode;
        };
    }

    private String mapEventTitle(String method) {
        return switch (method) {
            case "hms"               -> "健康告警";
            case "flight_task_ready" -> "任务就绪";
            case "flight_task_start" -> "任务开始";
            case "flight_task_finish"-> "任务完成";
            default                  -> method;
        };
    }
}
