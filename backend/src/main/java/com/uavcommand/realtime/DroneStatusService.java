package com.uavcommand.realtime;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;

@Service
public class DroneStatusService {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter HANDLED_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MISSION_COMPLETION_STEP = 35;
    private static final int BATTERY_THRESHOLD = 20;
    private static final int RETURN_STEPS = 10;

    private final AtomicInteger sequence = new AtomicInteger();
    private final AtomicReference<String> alertOccurredAt = new AtomicReference<>();
    private final AtomicReference<AlertHandling> alertHandling = new AtomicReference<>();
    private final AtomicBoolean returnInProgress = new AtomicBoolean(false);
    private final AtomicBoolean returnCompleted = new AtomicBoolean(false);
    private final AtomicInteger returnStep = new AtomicInteger();
    private final AtomicInteger missionStepAtReturn = new AtomicInteger(-1);
    private final AtomicReference<DroneStatus.OperationRecord> lastOperation = new AtomicReference<>();
    private final OperationRecordService operationRecordService;
    private final AlertRecordService alertRecordService;

    public DroneStatusService(
            OperationRecordService operationRecordService,
            AlertRecordService alertRecordService
    ) {
        this.operationRecordService = operationRecordService;
        this.alertRecordService = alertRecordService;
        operationRecordService.latest().ifPresent(lastOperation::set);
        alertRecordService.latest().ifPresent(record -> {
            alertOccurredAt.set(record.occurredAt());
            alertHandling.set(new AlertHandling(record.handler(), record.result(), record.timestamp()));
        });
    }

    public RealtimeStatusSnapshot currentStatus() {
        return RealtimeStatusSnapshot.from(currentLegacyStatus());
    }

    private DroneStatus currentLegacyStatus() {
        int step = currentMissionStep();
        int progress = Math.min(100, 65 + step);
        int battery = Math.max(20, 76 - step - returnBatteryUsage());
        int inspectedPoints = Math.min(50, 32 + progress / 4);
        double distance = roundDistance(18.2 + step * 0.03 + returnDistance());
        boolean missionCompleted = progress >= 100;
        DroneStatus.AlertStatus alert = createAlert(step);
        DroneStatus.ReturnStatus returnStatus = createReturnStatus(missionCompleted);

        return new DroneStatus(
                progress,
                currentAltitude(missionCompleted),
                battery,
                estimatedCompletion(missionCompleted),
                2,
                distance,
                inspectedPoints,
                50,
                List.of(
                        new DroneStatus.DeviceStatus("巡检无人机 01", "在线待命", 86),
                        new DroneStatus.DeviceStatus("巡检无人机 02", drone02Status(missionCompleted), battery),
                        new DroneStatus.DeviceStatus("巡检无人机 03", "离线", 0)
                ),
                alert,
                returnStatus
        );
    }

    private int currentMissionStep() {
        int frozenStep = missionStepAtReturn.get();
        return frozenStep >= 0 ? frozenStep : Math.min(sequence.get(), MISSION_COMPLETION_STEP);
    }

    private int currentAltitude(boolean missionCompleted) {
        if (returnInProgress.get() || returnCompleted.get()) {
            return Math.max(0, 80 - returnStep.get() * 80 / RETURN_STEPS);
        }
        return missionCompleted ? 0 : 80;
    }

    private int returnBatteryUsage() {
        return returnInProgress.get() || returnCompleted.get() ? returnStep.get() : 0;
    }

    private double returnDistance() {
        return returnInProgress.get() || returnCompleted.get() ? returnStep.get() * 0.02 : 0;
    }

    private double roundDistance(double distance) {
        return Math.round(distance * 10.0) / 10.0;
    }

    private String estimatedCompletion(boolean missionCompleted) {
        if (returnInProgress.get()) {
            return "返航中";
        }
        if (returnCompleted.get()) {
            return "已返航";
        }
        return missionCompleted
                ? "已完成"
                : LocalTime.now().plusMinutes(Math.max(3, (100 - Math.min(100, 65 + currentMissionStep())) / 2))
                        .format(TIME_FORMAT);
    }

    private String drone02Status(boolean missionCompleted) {
        if (returnInProgress.get()) {
            return "正在返航";
        }
        if (returnCompleted.get()) {
            return "已返航";
        }
        return missionCompleted ? "在线待命" : "正在飞行";
    }

    private DroneStatus.ReturnStatus createReturnStatus(boolean missionCompleted) {
        if (returnCompleted.get()) {
            return new DroneStatus.ReturnStatus(false, "已完成", "无人机已安全返航至起飞点", 100, lastOperation.get());
        }
        if (!returnInProgress.get()) {
            String message = missionCompleted ? "当前任务已完成，无需返航" : "无人机正常执行任务，未触发返航";
            return new DroneStatus.ReturnStatus(false, "待命", message, 0, lastOperation.get());
        }

        int currentStep = returnStep.get();
        String phase;
        String message;
        if (currentStep == 0) {
            phase = "返航启动";
            message = "无人机已接收到返航指令，正在调整飞行姿态";
        } else if (currentStep <= 3) {
            phase = "下降阶段";
            message = String.format("无人机正在下降，当前高度约 %d m", currentAltitude(false));
        } else if (currentStep <= 7) {
            phase = "返航途中";
            message = "无人机正在沿预设航线返回起飞点";
        } else {
            phase = "降落准备";
            message = "无人机已到达起飞点上空，准备降落";
        }
        return new DroneStatus.ReturnStatus(
                true,
                phase,
                message,
                currentStep * 100 / RETURN_STEPS,
                lastOperation.get()
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

    public synchronized RealtimeStatusSnapshot acknowledgeCurrentAlert(String handler, String result) {
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
                LocalDateTime.now().format(HANDLED_TIME_FORMAT)
        );
        if (alertHandling.get() != null) {
            throw new IllegalStateException("当前告警已经确认，不能重复处理");
        }
        alertRecordService.recordAcknowledgement(
                handling.handler(),
                handling.result(),
                "一般",
                "风速接近预警阈值",
                "当前风速 10 m/s，已接近预警阈值，建议持续观察。",
                alertOccurredAt.updateAndGet(value -> value == null
                        ? LocalTime.now().format(TIME_FORMAT)
                : value),
                "园区东侧气象站"
        );
        alertHandling.set(handling);
        return currentStatus();
    }

    public RealtimeStatusSnapshot nextStatus() {
        if (returnInProgress.get()) {
            int nextStep = returnStep.incrementAndGet();
            if (nextStep >= RETURN_STEPS) {
                returnInProgress.set(false);
                returnCompleted.set(true);
            }
        } else if (!returnCompleted.get()) {
            sequence.incrementAndGet();
        }
        return currentStatus();
    }

    public synchronized ReturnRequestResult requestReturn(String operator, String scenario) {
        String safeOperator = operator == null || operator.isBlank() ? "未知操作人" : operator.trim();
        int step = currentMissionStep();
        int progress = Math.min(100, 65 + step);
        int battery = Math.max(20, 76 - step);

        if (returnInProgress.get()) {
            return failedReturn(safeOperator, "无人机正在返航中，无需重复请求");
        }
        if (returnCompleted.get()) {
            return failedReturn(safeOperator, "无人机已经返航完成，无需重复请求");
        }
        if (progress >= 100) {
            return failedReturn(safeOperator, "当前任务已完成，无需返航");
        }

        boolean forceOffline = "offline".equalsIgnoreCase(scenario);
        boolean forceLowBattery = "low_battery".equalsIgnoreCase(scenario);
        boolean forceHighRiskWeather = "high_risk_weather".equalsIgnoreCase(scenario);
        if (safeOperator.equals("未知操作人")) {
            return failedReturn(safeOperator, "操作人不能为空");
        }
        if (forceOffline) {
            return failedReturn(safeOperator, "无人机离线，无法执行返航指令");
        }
        int effectiveBattery = forceLowBattery ? 12 : battery;
        if (effectiveBattery < BATTERY_THRESHOLD) {
            return failedReturn(safeOperator, String.format(
                    "无人机电量过低（%d%%），低于返航最低要求（%d%%）",
                    effectiveBattery,
                    BATTERY_THRESHOLD
            ));
        }
        if (forceHighRiskWeather) {
            return failedReturn(safeOperator, "当前存在高风险天气告警：暴雨大风，不满足返航安全条件");
        }

        missionStepAtReturn.set(step);
        returnStep.set(0);
        returnCompleted.set(false);
        returnInProgress.set(true);
        DroneStatus.OperationRecord record = recordOperation(
                safeOperator,
                "成功",
                "安全检查通过，无人机开始返航"
        );
        return new ReturnRequestResult(true, record.reason(), currentStatus());
    }

    private ReturnRequestResult failedReturn(String operator, String reason) {
        DroneStatus.OperationRecord record = recordOperation(operator, "失败", reason);
        return new ReturnRequestResult(false, record.reason(), currentStatus());
    }

    private DroneStatus.OperationRecord recordOperation(String operator, String result, String reason) {
        DroneStatus.OperationRecord record = operationRecordService.recordReturn(operator, result, reason);
        lastOperation.set(record);
        return record;
    }

    public record ReturnRequestResult(boolean success, String message, RealtimeStatusSnapshot status) { }

    private record AlertHandling(String handler, String result, String handledAt) { }
}
