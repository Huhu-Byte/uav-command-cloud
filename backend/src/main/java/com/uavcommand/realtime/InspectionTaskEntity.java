package com.uavcommand.realtime;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inspection_tasks")
public class InspectionTaskEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 120)
    private String route;

    @Column(nullable = false, length = 120)
    private String device;

    @Column(name = "task_status", nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private int progress;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(nullable = false, length = 20)
    private String frequency;

    @Column(nullable = false, length = 80)
    private String operator;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "flight_id", length = 64)
    private String flightId;

    protected InspectionTaskEntity() {
    }

    public InspectionTaskEntity(
            String name,
            String route,
            String device,
            String status,
            int progress,
            LocalDateTime scheduledAt,
            String frequency,
            String operator,
            LocalDateTime createdAt
    ) {
        this(name, route, device, status, progress, scheduledAt, frequency, operator, createdAt, null);
    }

    public InspectionTaskEntity(
            String name,
            String route,
            String device,
            String status,
            int progress,
            LocalDateTime scheduledAt,
            String frequency,
            String operator,
            LocalDateTime createdAt,
            String flightId
    ) {
        this.name = name;
        this.route = route;
        this.device = device;
        this.status = status;
        this.progress = progress;
        this.scheduledAt = scheduledAt;
        this.frequency = frequency;
        this.operator = operator;
        this.createdAt = createdAt;
        this.flightId = flightId;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getRoute() { return route; }
    public String getDevice() { return device; }
    public String getStatus() { return status; }
    public int getProgress() { return progress; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public String getFrequency() { return frequency; }
    public String getOperator() { return operator; }

    public String getFlightId() { return flightId; }

    public void updateDetails(String name, String route, String device, LocalDateTime scheduledAt, String frequency) {
        this.name = name;
        this.route = route;
        this.device = device;
        this.scheduledAt = scheduledAt;
        this.frequency = frequency;
    }

    public void updateFlightStatus(String status, int progress) {
        this.status = status;
        this.progress = progress;
    }

    public void setFlightId(String flightId) { this.flightId = flightId; }
}
