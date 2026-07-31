package com.uavcommand.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class InspectionTaskServiceTest {
    @Test
    void createsPendingTaskWithZeroProgress() {
        InspectionTaskRepository repository = mock(InspectionTaskRepository.class);
        when(repository.save(any(InspectionTaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        InspectionTaskService service = new InspectionTaskService(repository);

        InspectionTaskService.TaskView task = service.create(
                "张晨",
                new InspectionTaskService.CreateTaskRequest(
                        "北侧围栏例行巡检",
                        "巡检无人机 01",
                        LocalDateTime.of(2026, 7, 25, 9, 30),
                        "一次性",
                        "待规划路线（航线规划暂缓）"
                )
        );

        assertEquals("待执行", task.status());
        assertEquals(0, task.progress());
        assertEquals("张晨", task.operator());
    }

    @Test
    void rejectsTaskWithoutName() {
        InspectionTaskService service = new InspectionTaskService(mock(InspectionTaskRepository.class));

        assertThrows(IllegalArgumentException.class, () -> service.create(
                "张晨",
                new InspectionTaskService.CreateTaskRequest(
                        " ",
                        "巡检无人机 01",
                        LocalDateTime.of(2026, 7, 25, 9, 30),
                        "一次性",
                        ""
                )
        ));
    }
}
