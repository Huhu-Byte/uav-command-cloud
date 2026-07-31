package com.uavcommand.realtime;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRecordRepository extends JpaRepository<AlertRecordEntity, Long> {
    Optional<AlertRecordEntity> findFirstByOrderByHandledAtDesc();
}
