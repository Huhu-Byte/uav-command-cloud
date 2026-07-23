package com.uavcommand.realtime;

import java.util.List;

/** 前端指挥大屏需要的一次完整状态快照。 */
public record DroneStatus(
        int progress,
        int altitude,
        int battery,
        String estimatedCompletion,
        int onlineDeviceCount,
        double todayDistance,
        int inspectedPoints,
        int totalPoints,
        List<DeviceStatus> devices,
        AlertStatus alert
) {
    public record DeviceStatus(String name, String status, int battery) { }

    public record AlertStatus(
            boolean active,
            String level,
            String title,
            String detail,
            String occurredAt,
            String device,
            boolean acknowledged,
            String handlingStatus,
            String handledBy,
            String handledAt,
            String handlingResult
    ) { }
}
