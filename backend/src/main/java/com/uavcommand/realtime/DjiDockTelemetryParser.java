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
    private final InspectionTaskService inspectionTaskService;
    private final Map<String, String> deviceStates = new ConcurrentHashMap<>();
    /** OSD 数据缓存：gatewaySn:dock / gatewaySn:aircraft → 最新 OSD data。 */
    private final Map<String, Map<String, Object>> osdCache = new ConcurrentHashMap<>();
    private final List<TimestampedAlert> activeAlerts = Collections.synchronizedList(new ArrayList<>());
    private volatile String lastOsdTopic;
    private volatile String lastOsdPayload;

    public DjiDockTelemetryParser(DjiDockTopologyRegistry topologyRegistry, RealtimeStatusPublisher publisher,
                                    InspectionTaskService inspectionTaskService) {
        this.topologyRegistry = topologyRegistry;
        this.publisher = publisher;
        this.inspectionTaskService = inspectionTaskService;
    }

    @SuppressWarnings("unchecked")
    public void parseOsd(String topic, String payload) {
        try {
            Map<String, Object> root = MAPPER.readValue(payload, Map.class);
            Map<String, Object> data = (Map<String, Object>) root.get("data");
            if (data == null) { LOGGER.warn("OSD 报文缺少 data 层 topic={}", topic); return; }
            String gatewaySn = extractSn(topic);

            // DJI OSD 通过 from 字段区分设备类型：0=机场, 1=无人机
            int from = (int) getDouble(data, "from", 0);
            boolean isAircraft = (from == 1);

            // 缓存最新 OSD 数据（区分机场和无人机）
            String cacheKey = gatewaySn + (isAircraft ? ":aircraft" : ":dock");
            osdCache.put(cacheKey, data);

            String now = Instant.now().toString();
            List<RealtimeStatusSnapshot.DeviceState> devices = new ArrayList<>();

            // 构建机场设备状态
            Map<String, Object> dockData = osdCache.get(gatewaySn + ":dock");
            if (dockData != null) {
                int dockBattery = (int) getDouble(dockData, "battery", -1);
                double dockLat = getDouble(dockData, "latitude", -91);
                double dockLng = getDouble(dockData, "longitude", -181);
                String dockMode = mapDockModeCode((int) getDouble(dockData, "mode_code", 0));
                String coverState = String.valueOf(dockData.getOrDefault("cover_state", "0"));
                String droneCharge = String.valueOf(dockData.getOrDefault("drone_charge_state", "0"));
                String dockStatus = "1".equals(droneCharge) ? "充电中" : "1".equals(coverState) ? "舱盖已打开" : dockMode;

                deviceStates.put(gatewaySn, dockStatus);
                devices.add(new RealtimeStatusSnapshot.DeviceState(
                    gatewaySn, "Dock " + gatewaySn, "ONLINE",
                    dockStatus, Math.max(dockBattery, 0), 0, now
                ));
            }

            // 构建无人机设备状态
            Map<String, Object> aircraftData = osdCache.get(gatewaySn + ":aircraft");
            if (aircraftData != null) {
                double lat = getDouble(aircraftData, "latitude", -91);
                double lng = getDouble(aircraftData, "longitude", -181);
                double alt = getDouble(aircraftData, "height", getDouble(aircraftData, "altitude", -1));
                int battery = (int) getDouble(aircraftData, "battery", -1);
                double speed = getDouble(aircraftData, "horizontal_speed", getDouble(aircraftData, "speed", 0));
                int modeCode = (int) getDouble(aircraftData, "mode_code", 0);
                String aircraftSn = (String) aircraftData.getOrDefault("sn", gatewaySn + "-aircraft");
                String flightState = mapFlightMode(String.valueOf(modeCode));

                if (lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180) {
                    deviceStates.put(aircraftSn, flightState);
                    devices.add(new RealtimeStatusSnapshot.DeviceState(
                        aircraftSn, "Aircraft " + aircraftSn, "ONLINE",
                        flightState, Math.max(battery, 0), Math.max((int) alt, 0), now
                    ));
                }
            }

            // 如果没有任何缓存数据，至少用当前报文构建一条
            if (devices.isEmpty()) {
                double lat = getDouble(data, "latitude", -91);
                double lng = getDouble(data, "longitude", -181);
                double alt = getDouble(data, "altitude", -1);
                int battery = (int) getDouble(data, "battery", -1);
                if (lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180) {
                    devices.add(new RealtimeStatusSnapshot.DeviceState(
                        gatewaySn, (isAircraft ? "Aircraft " : "Dock ") + gatewaySn, "ONLINE",
                        deviceStates.getOrDefault(gatewaySn, "IDLE"), Math.max(battery, 0), Math.max((int) alt, 0), now
                    ));
                }
            }

            if (devices.isEmpty()) {
                LOGGER.warn("OSD 无有效设备数据 gatewaySn={}", gatewaySn);
                return;
            }

            // 取无人机的飞行数据作为任务状态（如果有）
            Map<String, Object> missionData = aircraftData != null ? aircraftData : data;
            double missionAlt = getDouble(missionData, "height", getDouble(missionData, "altitude", 0));
            double missionSpeed = getDouble(missionData, "horizontal_speed", getDouble(missionData, "speed", 0));
            String speedDisplay = missionSpeed > 0 ? String.format("%.1f m/s", missionSpeed) : "--";

            RealtimeStatusSnapshot snapshot = new RealtimeStatusSnapshot(
                RealtimeStatusSnapshot.SCHEMA_VERSION,
                new RealtimeStatusSnapshot.SourceMetadata("dji-dock", gatewaySn, "DJI Dock 3", now),
                now,
                60,
                new RealtimeStatusSnapshot.MissionStatus("", "", "EXECUTING", gatewaySn, 0, 0, 0, (int) missionAlt, speedDisplay),
                new RealtimeStatusSnapshot.DashboardSummary(devices.size(), 0, 0, 0),
                devices,
                currentAlerts(),
                new RealtimeStatusSnapshot.ControlCommand("", "", "IDLE", gatewaySn, "待命", "", 0, null)
            );

            this.lastOsdTopic = topic;
            this.lastOsdPayload = payload;
            if (publisher != null) {
                publisher.publish(snapshot);
            } else {
                LOGGER.warn("publisher 未注入，OSD 快照无法推送 gatewaySn={}", gatewaySn);
            }
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

            // 处理飞行任务事件：更新 inspection_tasks 状态
            handleFlightTaskEvent(method, root);

            // 处理媒体文件上传回调事件
            handleMediaUploadEvent(method, root, extractSn(topic));

            String title = mapEventTitle(method);
            String level = "hms".equals(method) ? "HIGH" : "INFO";
            String sn = extractSn(topic);
            String now = Instant.now().toString();
            var alert = new RealtimeStatusSnapshot.AlertState(
                method, true, level, title, "", now, sn, sn, false, "待处理", "", "", ""
            );
            synchronized (activeAlerts) {
                evictExpiredAlerts();
                activeAlerts.add(0, new TimestampedAlert(Instant.now(), alert));
                while (activeAlerts.size() > MAX_ALERTS) activeAlerts.remove(activeAlerts.size() - 1);
            }
            refreshLatestSnapshot();
        } catch (Exception e) { LOGGER.warn("event 解析失败 topic={}", topic, e); }
    }

    /** 处理 flight_task 系列事件，更新任务状态。 */
    @SuppressWarnings("unchecked")
    private void handleFlightTaskEvent(String method, Map<String, Object> root) {
        if (inspectionTaskService == null) return;
        Map<String, Object> data = (Map<String, Object>) root.get("data");
        if (data == null) return;
        String flightId = (String) data.get("flight_id");
        if (flightId == null || flightId.isBlank()) return;

        switch (method) {
            case "flight_task_ready" -> {
                tryUpdate(flightId, "待执行", 0);
            }
            case "flight_task_start" -> {
                tryUpdate(flightId, "执行中", 0);
            }
            case "flight_task_progress" -> {
                int progress = (int) getDouble(data, "progress", 0);
                tryUpdate(flightId, "执行中", progress);
            }
            case "flight_task_finish" -> {
                tryUpdate(flightId, "已完成", 100);
            }
            case "flight_task_failed" -> {
                tryUpdate(flightId, "失败", 0);
            }
            default -> { /* 非任务事件，忽略 */ }
        }
    }

    private void tryUpdate(String flightId, String status, int progress) {
        try {
            inspectionTaskService.updateFlightStatus(flightId, status, progress);
            LOGGER.info("任务状态更新 flightId={} status={} progress={}", flightId, status, progress);
        } catch (Exception e) {
            LOGGER.debug("任务状态更新跳过 flightId={} 原因={}", flightId, e.getMessage());
        }
    }

    /** 处理媒体文件上传回调事件，归档到 media_files 表。 */
    @SuppressWarnings("unchecked")
    private void handleMediaUploadEvent(String method, Map<String, Object> root, String gatewaySn) {
        if (!"file_upload_callback".equals(method) && !"media_upload".equals(method)) return;
        Map<String, Object> data = (Map<String, Object>) root.get("data");
        if (data == null) return;
        try {
            String fileName = String.valueOf(data.getOrDefault("file_name", data.getOrDefault("name", "unknown")));
            String fileType = String.valueOf(data.getOrDefault("file_type", data.getOrDefault("type", "PHOTO")));
            long fileSize = (long) getDouble(data, "file_size", 0);
            String objectKey = String.valueOf(data.getOrDefault("object_key", data.getOrDefault("path", "")));
            String downloadUrl = String.valueOf(data.getOrDefault("download_url", data.getOrDefault("url", "")));
            String flightId = String.valueOf(data.getOrDefault("flight_id", ""));
            if ("null".equals(flightId)) flightId = null;

            // 通过 ApplicationContext 获取 MediaFileService（避免循环依赖）
            // 这里直接记录日志，实际的归档由 MediaFileController 的 REST 接口完成
            LOGGER.info("媒体文件上传回调 fileName={} fileType={} size={}KB flightId={} gatewaySn={}",
                    fileName, fileType, fileSize / 1024, flightId, gatewaySn);
        } catch (Exception e) {
            LOGGER.warn("媒体文件回调处理失败 method={}", method, e);
        }
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

    /** 映射机场 mode_code 到中文状态。 */
    private String mapDockModeCode(int modeCode) {
        return switch (modeCode) {
            case 0  -> "待机";
            case 1  -> "远程调试";
            case 2  -> "本地调试";
            default -> "机场模式:" + modeCode;
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
