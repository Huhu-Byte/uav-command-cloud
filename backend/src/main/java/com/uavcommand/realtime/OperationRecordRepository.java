package com.uavcommand.realtime;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationRecordRepository extends JpaRepository<OperationRecordEntity, Long> {
    Optional<OperationRecordEntity> findFirstByOrderByOperatedAtDesc();
}
