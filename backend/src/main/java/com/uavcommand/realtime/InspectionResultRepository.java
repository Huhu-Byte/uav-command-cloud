package com.uavcommand.realtime;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionResultRepository extends JpaRepository<InspectionResultEntity, Long> {
    List<InspectionResultEntity> findAllByOrderByCapturedAtDesc();
}
