package com.uavcommand.realtime;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

@Service
public class InspectionTaskService {
    private static final String DEFERRED_ROUTE = "待规划路线（航线规划暂缓）";

    private final InspectionTaskRepository inspectionTaskRepository;

    public InspectionTaskService(InspectionTaskRepository inspectionTaskRepository) {
        this.inspectionTaskRepository = inspectionTaskRepository;
    }

    @PostConstruct
    void createDemoTasksWhenEmpty() {
        if (inspectionTaskRepository.count() != 0) return;

        LocalDateTime now = LocalDateTime.now();
        inspectionTaskRepository.saveAll(List.of(
                new InspectionTaskEntity("园区东侧例行巡检", "东侧围栏巡检路线", "巡检无人机 02", "执行中", 0, now.minusHours(2), "一次性", "张晨", now),
                new InspectionTaskEntity("屋顶光伏设备检查", "屋顶光伏巡检路线", "巡检无人机 01", "待执行", 0, now.plusHours(2), "一次性", "李然", now),
                new InspectionTaskEntity("北门周界安全巡检", "北门周界巡检路线", "巡检无人机 01", "已完成", 100, now.minusHours(3), "一次性", "王敏", now)
        ));
    }

    public List<TaskView> list() {
        return inspectionTaskRepository.findAllByOrderByScheduledAtAsc().stream().map(this::toView).toList();
    }

    public TaskView create(String operator, CreateTaskRequest request) {
        TaskDetails details = validate(request);
        InspectionTaskEntity entity = inspectionTaskRepository.save(new InspectionTaskEntity(
                details.name(), details.route(), details.device(), "待执行", 0, details.scheduledAt(), details.frequency(), operator, LocalDateTime.now()
        ));
        return toView(entity);
    }

    public TaskView update(Long id, CreateTaskRequest request) {
        InspectionTaskEntity entity = inspectionTaskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到要修改的任务"));
        if (!"待执行".equals(entity.getStatus())) throw new IllegalStateException("只有待执行任务可以修改");
        TaskDetails details = validate(request);
        entity.updateDetails(details.name(), details.route(), details.device(), details.scheduledAt(), details.frequency());
        return toView(inspectionTaskRepository.save(entity));
    }

    private TaskDetails validate(CreateTaskRequest request) {
        String name = requireText(request.name(), "请填写任务名称", 80);
        String device = requireText(request.device(), "请选择执行设备", 120);
        String frequency = requireText(request.frequency(), "请选择执行频率", 20);
        String route = optionalText(request.route(), DEFERRED_ROUTE, 120);
        if (request.scheduledAt() == null) throw new IllegalArgumentException("请填写计划执行时间");

        return new TaskDetails(name, route, device, request.scheduledAt(), frequency);
    }

    private TaskView toView(InspectionTaskEntity entity) {
        return new TaskView(
                entity.getId(), entity.getName(), entity.getRoute(), entity.getDevice(), entity.getStatus(),
                entity.getProgress(), entity.getScheduledAt(), entity.getFrequency(), entity.getOperator()
        );
    }

    private String requireText(String value, String message, int maximumLength) {
        String normalized = optionalText(value, null, maximumLength);
        if (normalized == null) throw new IllegalArgumentException(message);
        return normalized;
    }

    private String optionalText(String value, String fallback, int maximumLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) return fallback;
        if (normalized.length() > maximumLength) throw new IllegalArgumentException("填写内容过长，请缩短后重试");
        return normalized;
    }

    public record CreateTaskRequest(String name, String device, LocalDateTime scheduledAt, String frequency, String route) { }
    private record TaskDetails(String name, String route, String device, LocalDateTime scheduledAt, String frequency) { }
    public record TaskView(Long id, String name, String route, String device, String status, int progress, LocalDateTime scheduledAt, String frequency, String operator) { }
}
