package com.uavcommand.realtime;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

@Service
public class InspectionResultService {
    private final InspectionResultRepository inspectionResultRepository;

    public InspectionResultService(InspectionResultRepository inspectionResultRepository) {
        this.inspectionResultRepository = inspectionResultRepository;
    }

    @PostConstruct
    void createDemoResultsWhenEmpty() {
        if (inspectionResultRepository.count() != 0) return;
        LocalDateTime now = LocalDateTime.now();
        inspectionResultRepository.saveAll(List.of(
                new InspectionResultEntity("PHOTO", "东侧围栏连接点", "园区东侧例行巡检", "巡检无人机 02", now.minusMinutes(18), "东侧围栏 K12", "已归档"),
                new InspectionResultEntity("VIDEO", "仓库屋顶连续巡视", "园区东侧例行巡检", "巡检无人机 02", now.minusMinutes(24), "仓库区 A3", "可播放"),
                new InspectionResultEntity("PHOTO", "北门周界全景", "北门周界安全巡检", "巡检无人机 01", now.minusHours(2), "北门检查点", "已归档"),
                new InspectionResultEntity("PHOTO", "光伏板表面记录", "屋顶光伏设备检查", "巡检无人机 01", null, "屋顶光伏区", "待采集")
        ));
    }

    public List<ResultView> list() {
        return inspectionResultRepository.findAllByOrderByCapturedAtDesc().stream()
                .map(result -> new ResultView(result.getId(), result.getType(), result.getTitle(), result.getTaskName(), result.getDevice(), result.getCapturedAt(), result.getLocation(), result.getStatus()))
                .toList();
    }

    public record ResultView(Long id, String type, String title, String taskName, String device, LocalDateTime capturedAt, String location, String status) { }
}
