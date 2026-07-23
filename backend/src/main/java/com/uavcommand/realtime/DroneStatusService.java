package com.uavcommand.realtime;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;

/**
 * 当前阶段没有接入真实无人机，因此这里每三秒产生一份变化的模拟状态。
 * 将来接入 DJI Cloud API 时，只需把这个类的数据来源替换为真实接口即可。
 */
@Service
public class DroneStatusService {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter HANDLED_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MISSION_COMPLETION_STEP = 35;
    private final AtomicInteger sequence = new AtomicInteger();
    private final AtomicReference<String> alertOccurredAt = new AtomicReference<>();
    private final AtomicReference<AlertHandling> alertHandling = new AtomicReference<>();

    public DroneStatus currentStatus() {
        int step = Math.min(sequence.get(), MISSION_COMPLETION_STEP);
        int progress = Math.min(100, 65 + step);
        int battery = Math.max(20, 76 - step);
        int inspectedPoints = Math.min(50, 32 + progress / 4);
        double distance = Math.round((18.2 + step * 0.03) * 10.0) / 10.0;
        boolean missionCompleted = progress >= 100;
        int altitude = missionCompleted ? 0 : 80;
        String estimatedCompletion = missionCompleted
                ? "已完成"
                : LocalTime.now().plusMinutes(Math.max(3, (100 - progress) / 2)).format(TIME_FORMAT);
        DroneStatus.AlertStatus alert = createAlert(step);

        return new DroneStatus(
                progress,
                altitude,
                battery,
                estimatedCompletion,
                2,
                distance,
                inspectedPoints,
                50,
                List.of(
                        new DroneStatus.DeviceStatus("巡检无人机 01", "在线待命", 86),
                        new DroneStatus.DeviceStatus("巡检无人机 02", missionCompleted ? "在线待命" : "正在飞行", battery),
                        new DroneStatus.DeviceStatus("巡检无人机 03", "离线", 0)
                ),
                alert
        );
    }

    private DroneStatus.AlertStatus createAlert(int step) {
        if (step < 1) {
            return new DroneStatus.AlertStatus(
                    false, "无", "当前无告警", "设备和气象状态正常", "", "园区东侧气象站",
                    false, "无需处理", "", "", ""
            );
        }

        String occurredAt = alertOccurredAt.updateAndGet(value -> value == null
                ? LocalTime.now().format(TIME_FORMAT)
                : value);
        AlertHandling handling = alertHandling.get();
        return new DroneStatus.AlertStatus(
                true,
                "一般",
                "风速接近预警阈值",
                "当前风速 10 m/s，已接近预警阈值，建议持续观察。",
                occurredAt,
                "园区东侧气象站",
                handling != null,
                handling == null ? "待确认" : "已确认",
                handling == null ? "" : handling.handler(),
                handling == null ? "" : handling.handledAt(),
                handling == null ? "" : handling.result()
        );
    }

    public DroneStatus acknowledgeCurrentAlert(String handler, String result) {
        if (sequence.get() < 1) {
            throw new IllegalStateException("当前没有需要确认的告警");
        }
        if (handler == null || handler.isBlank()) {
            throw new IllegalArgumentException("处理人不能为空");
        }
        if (result == null || result.isBlank()) {
            throw new IllegalArgumentException("处理结果不能为空");
        }

        AlertHandling handling = new AlertHandling(
                handler.trim(),
                result.trim(),
                java.time.LocalDateTime.now().format(HANDLED_TIME_FORMAT)
        );
        if (!alertHandling.compareAndSet(null, handling)) {
            throw new IllegalStateException("当前告警已经确认，不能重复处理");
        }
        return currentStatus();
    }

    public DroneStatus nextStatus() {
        sequence.incrementAndGet();
        return currentStatus();
    }

    private record AlertHandling(String handler, String result, String handledAt) { }
}
