package com.uavcommand.realtime;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.springframework.stereotype.Service;

@Service
public class AlertRecordService {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AlertRecordRepository alertRecordRepository;

    public AlertRecordService(AlertRecordRepository alertRecordRepository) {
        this.alertRecordRepository = alertRecordRepository;
    }

    public void recordAcknowledgement(
            String handler,
            String result,
            String level,
            String title,
            String detail,
            String occurredAt,
            String device
    ) {
        alertRecordRepository.save(new AlertRecordEntity(
                handler,
                LocalDateTime.now(),
                result,
                level,
                title,
                detail,
                occurredAt,
                device
        ));
    }

    public Optional<DroneStatus.AlertRecord> latest() {
        return alertRecordRepository.findFirstByOrderByHandledAtDesc().map(this::toStatusRecord);
    }

    private DroneStatus.AlertRecord toStatusRecord(AlertRecordEntity entity) {
        return new DroneStatus.AlertRecord(
                entity.getHandler(),
                entity.getHandledAt().format(TIME_FORMAT),
                entity.getResult(),
                entity.getAlertLevel(),
                entity.getTitle(),
                entity.getDetail(),
                entity.getOccurredAt(),
                entity.getDevice()
        );
    }
}
