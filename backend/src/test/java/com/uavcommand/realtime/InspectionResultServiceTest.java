package com.uavcommand.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InspectionResultServiceTest {
    @Test
    void returnsPersistedResultsInRepositoryOrder() {
        InspectionResultRepository repository = mock(InspectionResultRepository.class);
        InspectionResultEntity latest = result(2L, "VIDEO", LocalDateTime.of(2026, 7, 27, 9, 30));
        InspectionResultEntity earlier = result(1L, "PHOTO", LocalDateTime.of(2026, 7, 27, 9, 15));
        when(repository.findAllByOrderByCapturedAtDesc()).thenReturn(List.of(latest, earlier));

        List<InspectionResultService.ResultView> results = new InspectionResultService(repository).list();

        assertEquals(List.of(2L, 1L), results.stream().map(InspectionResultService.ResultView::id).toList());
        assertEquals(List.of("VIDEO", "PHOTO"), results.stream().map(InspectionResultService.ResultView::type).toList());
    }

    private InspectionResultEntity result(Long id, String type, LocalDateTime capturedAt) {
        InspectionResultEntity result = new InspectionResultEntity(
                type,
                "Result " + id,
                "Task " + id,
                "Drone " + id,
                capturedAt,
                "Location " + id,
                "ARCHIVED"
        );
        ReflectionTestUtils.setField(result, "id", id);
        return result;
    }
}
