package com.uavcommand.realtime;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaFileRepository extends JpaRepository<MediaFileEntity, Long> {
    List<MediaFileEntity> findAllByOrderByUploadedAtDesc();
    List<MediaFileEntity> findByFlightId(String flightId);
}
