package com.uavcommand.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ActivityHistoryServiceTest {
    @Test
    void combinesHistoryInReverseChronologicalOrderAndFiltersByType() {
        OperationRecordRepository operationRepository = mock(OperationRecordRepository.class);
        AlertRecordRepository alertRepository = mock(AlertRecordRepository.class);
        when(operationRepository.findAll()).thenReturn(List.of(operation(1L, LocalDateTime.of(2026, 7, 27, 9, 10))));
        when(alertRepository.findAll()).thenReturn(List.of(alert(2L, LocalDateTime.of(2026, 7, 27, 9, 20))));
        ActivityHistoryService service = new ActivityHistoryService(operationRepository, alertRepository);

        List<DroneStatus.ActivityRecord> combined = service.history("ALL", "ALL");
        List<DroneStatus.ActivityRecord> returns = service.history("RETURN", "ALL");

        assertEquals(List.of("alert-2", "return-1"), combined.stream().map(DroneStatus.ActivityRecord::key).toList());
        assertEquals(List.of("return-1"), returns.stream().map(DroneStatus.ActivityRecord::key).toList());
    }

    private OperationRecordEntity operation(Long id, LocalDateTime operatedAt) {
        OperationRecordEntity record = new OperationRecordEntity("Operator", operatedAt, "Return", "SUCCESS", "Safety check passed");
        ReflectionTestUtils.setField(record, "id", id);
        return record;
    }

    private AlertRecordEntity alert(Long id, LocalDateTime handledAt) {
        AlertRecordEntity record = new AlertRecordEntity(
                "Handler",
                handledAt,
                "Acknowledged",
                "GENERAL",
                "Wind warning",
                "Wind is approaching the threshold",
                "2026-07-27 09:00:00",
                "Drone 02"
        );
        ReflectionTestUtils.setField(record, "id", id);
        return record;
    }
}
