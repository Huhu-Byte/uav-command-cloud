package com.uavcommand.realtime;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "flight_routes")
public class FlightRouteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 60)
    private String area;

    @Column(nullable = false, length = 20)
    private String mode;

    @Column(columnDefinition = "CLOB", nullable = false)
    private String waypointsJson;

    @Column(nullable = false, length = 80)
    private String createdBy;

    @Column(nullable = false, length = 80)
    private String modifiedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "modified_at", nullable = false)
    private LocalDateTime modifiedAt;

    @Column(name = "used_in_tasks", nullable = false)
    private int usedInTasks;

    protected FlightRouteEntity() {
    }

    public FlightRouteEntity(
            String name,
            String area,
            String mode,
            String waypointsJson,
            String createdBy,
            LocalDateTime createdAt,
            LocalDateTime modifiedAt
    ) {
        this.name = name;
        this.area = area;
        this.mode = mode;
        this.waypointsJson = waypointsJson;
        this.createdBy = createdBy;
        this.modifiedBy = createdBy;
        this.createdAt = createdAt;
        this.modifiedAt = modifiedAt;
        this.usedInTasks = 0;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getArea() { return area; }
    public String getMode() { return mode; }
    public String getWaypointsJson() { return waypointsJson; }
    public String getCreatedBy() { return createdBy; }
    public String getModifiedBy() { return modifiedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getModifiedAt() { return modifiedAt; }
    public int getUsedInTasks() { return usedInTasks; }

    public void updateDetails(String name, String area, String mode, String waypointsJson, String modifier, LocalDateTime now) {
        this.name = name;
        this.area = area;
        this.mode = mode;
        this.waypointsJson = waypointsJson;
        this.modifiedBy = modifier;
        this.modifiedAt = now;
    }

    public void markUsage() {
        this.usedInTasks++;
    }
}
