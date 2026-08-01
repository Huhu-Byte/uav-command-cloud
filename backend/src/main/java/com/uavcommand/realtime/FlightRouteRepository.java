package com.uavcommand.realtime;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FlightRouteRepository extends JpaRepository<FlightRouteEntity, Long> {
    List<FlightRouteEntity> findAllByOrderByModifiedAtDesc();
}
