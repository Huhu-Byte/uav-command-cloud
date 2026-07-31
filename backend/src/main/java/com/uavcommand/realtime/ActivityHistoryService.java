package com.uavcommand.realtime;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

@Service
public class ActivityHistoryService {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String ALL = "ALL";
    private static final String RETURN = "RETURN";
    private static final String ALERT = "ALERT";

    private final OperationRecordRepository operationRecordRepository;
    private final AlertRecordRepository alertRecordRepository;

    public ActivityHistoryService(
            OperationRecordRepository operationRecordRepository,
            AlertRecordRepository alertRecordRepository
    ) {
        this.operationRecordRepository = operationRecordRepository;
        this.alertRecordRepository = alertRecordRepository;
    }

    public List<DroneStatus.ActivityRecord> history(String type, String result) {
        String normalizedType = normalizeType(type);
        String normalizedResult = normalizeResult(result);

        Stream<ActivityEntry> returnEntries = operationRecordRepository.findAll().stream()
                .map(entity -> new ActivityEntry(
                        entity.getOperatedAt(),
                        new DroneStatus.ActivityRecord(
                                "return-" + entity.getId(),
                                RETURN,
                                entity.getOperatedAt().format(TIME_FORMAT),
                                entity.getOperator(),
                                entity.getResult(),
                                entity.getAction(),
                                entity.getReason(),
                                "巡检无人机 02"
                        )
                ));
        Stream<ActivityEntry> alertEntries = alertRecordRepository.findAll().stream()
                .map(entity -> new ActivityEntry(
                        entity.getHandledAt(),
                        new DroneStatus.ActivityRecord(
                                "alert-" + entity.getId(),
                                ALERT,
                                entity.getHandledAt().format(TIME_FORMAT),
                                entity.getHandler(),
                                "成功",
                                entity.getTitle(),
                                "处理结果：" + entity.getResult() + "；告警说明：" + entity.getDetail(),
                                entity.getDevice()
                        )
                ));

        return Stream.concat(returnEntries, alertEntries)
                .filter(entry -> ALL.equals(normalizedType) || entry.record().type().equals(normalizedType))
                .filter(entry -> ALL.equals(normalizedResult) || entry.record().result().equals(normalizedResult))
                .sorted(Comparator.comparing(ActivityEntry::timestamp).reversed())
                .map(ActivityEntry::record)
                .toList();
    }

    private String normalizeType(String type) {
        String normalized = type == null || type.isBlank() ? ALL : type.trim().toUpperCase(Locale.ROOT);
        if (!ALL.equals(normalized) && !RETURN.equals(normalized) && !ALERT.equals(normalized)) {
            throw new IllegalArgumentException("历史类型只支持 ALL、RETURN 或 ALERT");
        }
        return normalized;
    }

    private String normalizeResult(String result) {
        String normalized = result == null || result.isBlank() ? ALL : result.trim();
        if (!ALL.equals(normalized) && !"成功".equals(normalized) && !"失败".equals(normalized)) {
            throw new IllegalArgumentException("结果只支持 ALL、成功 或 失败");
        }
        return normalized;
    }

    private record ActivityEntry(LocalDateTime timestamp, DroneStatus.ActivityRecord record) { }
}
