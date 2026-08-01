package com.uavcommand.realtime;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 媒体文件实体：DJI Dock 上传的巡检照片和视频。
 *
 * <p>机场执行航线任务后，通过 STS 凭证将媒体文件上传到对象存储，
 * 然后通过 MQTT events 上报文件信息，后端记录归档。</p>
 */
@Entity
@Table(name = "media_files")
public class MediaFileEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flight_id", length = 64)
    private String flightId;

    @Column(name = "file_name", nullable = false, length = 200)
    private String fileName;

    @Column(name = "file_type", nullable = false, length = 20)
    private String fileType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(name = "download_url", length = 1000)
    private String downloadUrl;

    @Column(name = "gateway_sn", length = 100)
    private String gatewaySn;

    @Column(name = "task_name", length = 80)
    private String taskName;

    @Column(name = "captured_at")
    private LocalDateTime capturedAt;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "已上传";

    protected MediaFileEntity() { }

    public MediaFileEntity(String flightId, String fileName, String fileType, long fileSize,
                           String objectKey, String downloadUrl, String gatewaySn,
                           String taskName, LocalDateTime capturedAt, LocalDateTime uploadedAt) {
        this.flightId = flightId;
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.objectKey = objectKey;
        this.downloadUrl = downloadUrl;
        this.gatewaySn = gatewaySn;
        this.taskName = taskName;
        this.capturedAt = capturedAt;
        this.uploadedAt = uploadedAt;
    }

    public Long getId() { return id; }
    public String getFlightId() { return flightId; }
    public String getFileName() { return fileName; }
    public String getFileType() { return fileType; }
    public long getFileSize() { return fileSize; }
    public String getObjectKey() { return objectKey; }
    public String getDownloadUrl() { return downloadUrl; }
    public String getGatewaySn() { return gatewaySn; }
    public String getTaskName() { return taskName; }
    public LocalDateTime getCapturedAt() { return capturedAt; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public String getStatus() { return status; }
}
