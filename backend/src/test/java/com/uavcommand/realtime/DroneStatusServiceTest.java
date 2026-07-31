package com.uavcommand.realtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DroneStatusServiceTest {
    @Mock
    private OperationRecordService operationRecordService;

    @Mock
    private AlertRecordService alertRecordService;

    @Test
    void returnsVersionedSnapshotForTheSimulator() {
        when(operationRecordService.latest()).thenReturn(Optional.empty());
        when(alertRecordService.latest()).thenReturn(Optional.empty());
        DroneStatusService service = new DroneStatusService(operationRecordService, alertRecordService);

        RealtimeStatusSnapshot snapshot = service.nextStatus();

        assertEquals("1.0", snapshot.schemaVersion());
        assertEquals("SIMULATOR", snapshot.source().type());
        assertEquals(3, snapshot.devices().size());
        assertEquals(1, snapshot.alerts().size());
        assertEquals("RETURN_TO_HOME", snapshot.returnCommand().type());
        assertEquals("drone-02", snapshot.task().deviceId());
    }

    @Test
    void restoresLatestAlertHandlingWhenServiceRestarts() {
        when(operationRecordService.latest()).thenReturn(Optional.empty());
        when(alertRecordService.latest()).thenReturn(Optional.of(new DroneStatus.AlertRecord(
                "张晨",
                "2026-07-23 16:23:00",
                "已确认现场情况",
                "一般",
                "风速接近预警阈值",
                "当前风速 10 m/s",
                "16:20",
                "园区东侧气象站"
        )));
        DroneStatusService service = new DroneStatusService(operationRecordService, alertRecordService);

        RealtimeStatusSnapshot status = service.nextStatus();

        assertTrue(status.alerts().get(0).acknowledged());
        assertTrue(status.alerts().get(0).handlingStatus().equals("已确认"));
        assertTrue(status.alerts().get(0).handledBy().equals("张晨"));
    }

    @Test
    void keepsAlertPendingWhenRecordWriteFails() {
        when(operationRecordService.latest()).thenReturn(Optional.empty());
        when(alertRecordService.latest()).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("数据库暂时不可用"))
                .when(alertRecordService)
                .recordAcknowledgement(any(), any(), any(), any(), any(), any(), any());
        DroneStatusService service = new DroneStatusService(operationRecordService, alertRecordService);
        service.nextStatus();

        assertThrows(IllegalStateException.class, () -> service.acknowledgeCurrentAlert("张晨", "已确认现场情况"));
        assertFalse(service.currentStatus().alerts().get(0).acknowledged());
    }
}
