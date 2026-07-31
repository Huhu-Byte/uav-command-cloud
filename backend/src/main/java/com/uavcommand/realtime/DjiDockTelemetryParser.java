package com.uavcommand.realtime;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class DjiDockTelemetryParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(DjiDockTelemetryParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_ALERTS = 20;

    private final DjiDockTopologyRegistry topologyRegistry;
    private final RealtimeStatusPublisher publisher;
    private final Map<String, String> deviceStates = new ConcurrentHashMap<>();
    private final List<RealtimeStatusSnapshot.AlertState> activeAlerts = Collections.synchronizedList(new ArrayList<>());
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
                List.copyOf(activeAlerts),
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
                activeAlerts.add(0, alert);
                while (activeAlerts.size() > MAX_ALERTS) activeAlerts.remove(activeAlerts.size() - 1);
            }
            refreshLatestSnapshot();
        } catch (Exception e) { LOGGER.warn("event 解析失败 topic={}", topic, e); }
    }

    @SuppressWarnings("unchecked")
    public void parseStatus(String topic, String payload) {
        LOGGER.debug("status 报文收到 topic={}", topic);
    }

    public void refreshLatestSnapshot() {
        if (lastOsdTopic != null && lastOsdPayload != null) parseOsd(lastOsdTopic, lastOsdPayload);
    }

    private String extractSn(String topic) { String[] parts = topic.split("/"); return parts.length >= 3 ? parts[2] : "unknown"; }
    private double getDouble(Map<String, Object> map, String key, double fallback) {
        Object val = map.get(key); if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(val)); } catch (Exception e) { return fallback; }
    }
    private String mapFlightMode(String mode) { return switch (mode) { case "0"->"手动操控"; case "1"->"起飞中"; case "2"->"降落中"; case "3"->"航线飞行"; case "4"->"悬停中"; case "5"->"返航中"; default->"飞行模式:"+mode; }; }
    private String mapEventTitle(String method) { return switch (method) { case "hms"->"健康告警"; case "flight_task_ready"->"任务就绪"; case "flight_task_start"->"任务开始"; case "flight_task_finish"->"任务完成"; default->method; }; }
}
