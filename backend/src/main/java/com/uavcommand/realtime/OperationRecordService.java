package com.uavcommand.realtime;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.springframework.stereotype.Service;

@Service
public class OperationRecordService {
    private static final DateTimeFormatter OPERATION_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OperationRecordRepository operationRecordRepository;

    public OperationRecordService(OperationRecordRepository operationRecordRepository) {
        this.operationRecordRepository = operationRecordRepository;
    }

    public DroneStatus.OperationRecord recordReturn(String operator, String result, String reason) {
        OperationRecordEntity entity = operationRecordRepository.save(new OperationRecordEntity(
                operator,
                LocalDateTime.now(),
                "返航请求",
                result,
                reason
        ));
        return toStatusRecord(entity);
    }

    public Optional<DroneStatus.OperationRecord> latest() {
        return operationRecordRepository.findFirstByOrderByOperatedAtDesc().map(this::toStatusRecord);
    }

    private DroneStatus.OperationRecord toStatusRecord(OperationRecordEntity entity) {
        return new DroneStatus.OperationRecord(
                entity.getOperator(),
                entity.getOperatedAt().format(OPERATION_TIME_FORMAT),
                entity.getAction(),
                entity.getResult(),
                entity.getReason()
        );
    }
}
