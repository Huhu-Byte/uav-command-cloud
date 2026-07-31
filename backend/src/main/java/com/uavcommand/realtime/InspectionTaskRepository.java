package com.uavcommand.realtime;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionTaskRepository extends JpaRepository<InspectionTaskEntity, Long> {
    List<InspectionTaskEntity> findAllByOrderByScheduledAtAsc();
}
