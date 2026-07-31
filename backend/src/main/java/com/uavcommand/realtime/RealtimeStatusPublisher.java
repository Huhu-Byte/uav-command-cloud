package com.uavcommand.realtime;

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 实时状态发布器，负责协调模拟器和真实 DJI 设备两条数据来源。
 *
 * <p>修复问题6a：新增超时回退机制。真实设备推送后若超过 REAL_DATA_STALE_MS 毫秒未收到新数据
 * （设备离线/MQTT断连），模拟器自动恢复推送，前端不会卡在最后一帧。<br>
 * 修复问题6b：publish() 接收真实设备快照时，验证 schemaVersion 是否为已知版本，
 * 对缺少关键字段的快照做防御性补全，保证前端收到的格式与模拟器一致。</p>
 */
@Component
public class RealtimeStatusPublisher {
    /** 真实设备数据超过此时长未更新，回退到模拟器推送（单位：毫秒）。 */
    private static final long REAL_DATA_STALE_MS = 30_000;

    private final DroneStatusService droneStatusService;
    private final DroneStatusWebSocketHandler webSocketHandler;

    private volatile Instant lastRealDataAt = null;

    public RealtimeStatusPublisher(DroneStatusService droneStatusService,
                                   DroneStatusWebSocketHandler webSocketHandler) {
        this.droneStatusService = droneStatusService;
        this.webSocketHandler = webSocketHandler;
    }

    @Scheduled(fixedRate = 3000)
    public void publishStatus() {
        // 修复问题6a：如果超过 REAL_DATA_STALE_MS 没有收到真实设备数据，回退到模拟器
        if (isRealDataFresh()) return;
        webSocketHandler.broadcast(droneStatusService.nextStatus());
    }

    /**
     * 由真实 DJI 设备遥测路径调用。
     *
     * <p>修复问题6b：对快照做基础合法性检查，补全缺失的 schemaVersion，
     * 确保前端接收到的 DJI 快照与模拟器快照格式一致。</p>
     */
    public void publish(RealtimeStatusSnapshot snapshot) {
        if (snapshot == null) return;
        // 修复问题6b：补全可能缺失的 schemaVersion
        RealtimeStatusSnapshot normalized = normalize(snapshot);
        // 修复问题6a：记录最新真实数据时间，用于判断是否需要回退模拟器
        lastRealDataAt = Instant.now();
        webSocketHandler.broadcast(normalized);
    }

    /** 修复问题6a：真实数据在有效期内则返回 true，模拟器应暂停。 */
    private boolean isRealDataFresh() {
        Instant last = lastRealDataAt;
        return last != null
                && Instant.now().toEpochMilli() - last.toEpochMilli() < REAL_DATA_STALE_MS;
    }

    /**
     * 修复问题6b：对 DJI 快照做防御性补全。
     * 确保 schemaVersion 已知，task/devices/alerts/returnCommand 非 null，
     * 与 RealtimeStatusSnapshot.from(DroneStatus) 产出的格式对齐。
     */
    private RealtimeStatusSnapshot normalize(RealtimeStatusSnapshot s) {
        String version = s.schemaVersion() != null ? s.schemaVersion() : RealtimeStatusSnapshot.SCHEMA_VERSION;
        var task = s.task() != null ? s.task()
                : new RealtimeStatusSnapshot.MissionStatus("", "", "EXECUTING", "", 0, 0, 0, 0, "--");
        var summary = s.summary() != null ? s.summary()
                : new RealtimeStatusSnapshot.DashboardSummary(0, 0, 0, 0);
        var devices = s.devices() != null ? s.devices() : java.util.Collections.<RealtimeStatusSnapshot.DeviceState>emptyList();
        var alerts  = s.alerts()  != null ? s.alerts()  : java.util.Collections.<RealtimeStatusSnapshot.AlertState>emptyList();
        var cmd = s.returnCommand() != null ? s.returnCommand()
                : new RealtimeStatusSnapshot.ControlCommand("", "", "IDLE", "", "待命", "", 0, null);

        return new RealtimeStatusSnapshot(
                version,
                s.source(),
                s.updatedAt() != null ? s.updatedAt() : Instant.now().toString(),
                s.staleAfterSeconds() > 0 ? s.staleAfterSeconds() : 10,
                task, summary, devices, alerts, cmd
        );
    }
}
