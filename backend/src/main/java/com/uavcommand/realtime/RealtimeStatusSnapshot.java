package com.uavcommand.realtime;

import java.time.Instant;
import java.util.List;

/**
 * 前端实时状态的统一数据合同。
 *
 * <p>模拟器和后续 DJI Cloud API 都必须转换为这一格式后，才能提供给网页和 WebSocket。
 * 这样替换数据来源时不会把 DJI 的原始字段泄漏到页面代码中。</p>
 */
public record RealtimeStatusSnapshot(
        String schemaVersion,
        SourceMetadata source,
        String updatedAt,
        int staleAfterSeconds,
        MissionStatus task,
        DashboardSummary summary,
        List<DeviceState> devices,
        List<AlertState> alerts,
        ControlCommand returnCommand
) {
    public static final String SCHEMA_VERSION = "1.0";
    private static final String SIMULATOR_SOURCE_ID = "local-flight-simulator";
    private static final int STALE_AFTER_SECONDS = 10;

    public static RealtimeStatusSnapshot from(DroneStatus status) {
        String updatedAt = Instant.now().toString();
        DeviceState activeDevice = new DeviceState(
                "drone-02",
                "巡检无人机 02",
                deviceConnectionStatus(status.devices().get(1).status()),
                deviceOperationalStatus(status.devices().get(1).status()),
                status.battery(),
                status.altitude(),
                updatedAt
        );
        List<DeviceState> devices = List.of(
                new DeviceState("drone-01", "巡检无人机 01", "ONLINE", "IDLE", 86, 0, updatedAt),
                activeDevice,
                new DeviceState("drone-03", "巡检无人机 03", "OFFLINE", "OFFLINE", 0, 0, updatedAt)
        );
        return new RealtimeStatusSnapshot(
                SCHEMA_VERSION,
                new SourceMetadata("SIMULATOR", SIMULATOR_SOURCE_ID, "模拟飞行状态", updatedAt),
                updatedAt,
                STALE_AFTER_SECONDS,
                new MissionStatus(
                        "mission-east-fence-001",
                        "园区东侧例行巡检",
                        missionStatus(status),
                        "drone-02",
                        status.progress(),
                        status.inspectedPoints(),
                        status.totalPoints(),
                        status.altitude(),
                        status.estimatedCompletion()
                ),
                new DashboardSummary(
                        status.onlineDeviceCount(),
                        status.todayDistance(),
                        status.inspectedPoints(),
                        status.totalPoints()
                ),
                devices,
                List.of(new AlertState(
                        "alert-wind-speed-current",
                        status.alert().active(),
                        status.alert().level(),
                        status.alert().title(),
                        status.alert().detail(),
                        status.alert().occurredAt(),
                        "weather-station-east",
                        status.alert().device(),
                        status.alert().acknowledged(),
                        status.alert().handlingStatus(),
                        status.alert().handledBy(),
                        status.alert().handledAt(),
                        status.alert().handlingResult()
                )),
                new ControlCommand(
                        "return-current",
                        "RETURN_TO_HOME",
                        controlStatus(status.returnStatus()),
                        "drone-02",
                        status.returnStatus().phase(),
                        status.returnStatus().message(),
                        status.returnStatus().returnProgress(),
                        status.returnStatus().lastOperation()
                )
        );
    }

    private static String deviceConnectionStatus(String legacyStatus) {
        return "离线".equals(legacyStatus) ? "OFFLINE" : "ONLINE";
    }

    private static String deviceOperationalStatus(String legacyStatus) {
        if ("正在飞行".equals(legacyStatus)) return "FLYING";
        if ("正在返航".equals(legacyStatus)) return "RETURNING";
        if ("已返航".equals(legacyStatus)) return "RETURNED";
        if ("离线".equals(legacyStatus)) return "OFFLINE";
        return "IDLE";
    }

    private static String missionStatus(DroneStatus status) {
        if (status.returnStatus().inProgress()) return "RETURNING";
        if ("已完成".equals(status.returnStatus().phase())) return "ABORTED";
        return status.progress() >= 100 ? "COMPLETED" : "EXECUTING";
    }

    private static String controlStatus(DroneStatus.ReturnStatus returnStatus) {
        if (returnStatus.inProgress()) return "IN_PROGRESS";
        if ("已完成".equals(returnStatus.phase())) return "SUCCEEDED";
        return "IDLE";
    }

    public record SourceMetadata(String type, String id, String label, String receivedAt) { }

    public record MissionStatus(
            String id,
            String name,
            String status,
            String deviceId,
            int progress,
            int inspectedPoints,
            int totalPoints,
            int altitude,
            String estimatedCompletion
    ) { }

    public record DashboardSummary(int onlineDeviceCount, double todayDistance, int inspectedPoints, int totalPoints) { }

    public record DeviceState(
            String id,
            String name,
            String connectionStatus,
            String operationalStatus,
            int battery,
            int altitude,
            String updatedAt
    ) { }

    public record AlertState(
            String id,
            boolean active,
            String level,
            String title,
            String detail,
            String occurredAt,
            String deviceId,
            String deviceName,
            boolean acknowledged,
            String handlingStatus,
            String handledBy,
            String handledAt,
            String handlingResult
    ) { }

    public record ControlCommand(
            String id,
            String type,
            String status,
            String deviceId,
            String phase,
            String message,
            int progress,
            DroneStatus.OperationRecord lastOperation
    ) { }
}
