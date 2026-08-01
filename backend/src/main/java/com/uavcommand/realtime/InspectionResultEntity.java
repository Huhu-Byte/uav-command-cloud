package com.uavcommand.realtime;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inspection_results")
public class InspectionResultEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "result_type", nullable = false, length = 20)
    private String type;
    @Column(nullable = false, length = 120)
    private String title;
    @Column(name = "task_name", nullable = false, length = 80)
    private String taskName;
    @Column(nullable = false, length = 120)
    private String device;
    @Column(name = "captured_at")
    private LocalDateTime capturedAt;
    @Column(nullable = false, length = 160)
    private String location;
    @Column(name = "result_status", nullable = false, length = 20)
    private String status;
    @Column(name = "workspace_id")
    private Long workspaceId = 1L;
    @Column(name = "media_source", length = 50)
    private String mediaSource = "LOCAL_CATALOG";

    protected InspectionResultEntity() { }

    public InspectionResultEntity(String type, String title, String taskName, String device, LocalDateTime capturedAt, String location, String status) {
        this.type = type;
        this.title = title;
        this.taskName = taskName;
        this.device = device;
        this.capturedAt = capturedAt;
        this.location = location;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getTaskName() { return taskName; }
    public String getDevice() { return device; }
    public LocalDateTime getCapturedAt() { return capturedAt; }
    public String getLocation() { return location; }
    public String getStatus() { return status; }
    public Long getWorkspaceId() { return workspaceId; }
    public String getMediaSource() { return mediaSource; }
}
