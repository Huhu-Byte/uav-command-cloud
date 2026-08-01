package com.uavcommand.realtime;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 媒体文件服务：管理 DJI Dock 上传的巡检照片和视频记录。
 *
 * <p>机场执行航线任务后，通过 STS 凭证将媒体文件上传到对象存储，
 * 然后通过 MQTT events（file_upload_callback）上报文件信息，
 * 后端收到后调用 {@link #recordUpload} 归档记录。</p>
 */
@Service
public class MediaFileService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MediaFileService.class);

    private final MediaFileRepository mediaFileRepository;

    public MediaFileService(MediaFileRepository mediaFileRepository) {
        this.mediaFileRepository = mediaFileRepository;
    }

    public List<MediaFileView> list() {
        return mediaFileRepository.findAllByOrderByUploadedAtDesc().stream().map(this::toView).toList();
    }

    public List<MediaFileView> findByFlightId(String flightId) {
        return mediaFileRepository.findByFlightId(flightId).stream().map(this::toView).toList();
    }

    /** 记录机场上报的媒体文件。 */
    public MediaFileView recordUpload(UploadCallbackRequest request) {
        MediaFileEntity entity = new MediaFileEntity(
                request.flightId(),
                request.fileName(),
                request.fileType(),
                request.fileSize(),
                request.objectKey(),
                request.downloadUrl(),
                request.gatewaySn(),
                request.taskName(),
                request.capturedAt(),
                LocalDateTime.now()
        );
        MediaFileEntity saved = mediaFileRepository.save(entity);
        LOGGER.info("媒体文件归档 fileName={} fileType={} size={}KB flightId={}",
                request.fileName(), request.fileType(), request.fileSize() / 1024, request.flightId());
        return toView(saved);
    }

    private MediaFileView toView(MediaFileEntity entity) {
        return new MediaFileView(
                entity.getId(), entity.getFlightId(), entity.getFileName(), entity.getFileType(),
                entity.getFileSize(), entity.getObjectKey(), entity.getDownloadUrl(),
                entity.getGatewaySn(), entity.getTaskName(), entity.getCapturedAt(),
                entity.getUploadedAt(), entity.getStatus()
        );
    }

    public record UploadCallbackRequest(
            String flightId, String fileName, String fileType, long fileSize,
            String objectKey, String downloadUrl, String gatewaySn,
            String taskName, LocalDateTime capturedAt
    ) {}

    public record MediaFileView(
            Long id, String flightId, String fileName, String fileType,
            long fileSize, String objectKey, String downloadUrl,
            String gatewaySn, String taskName, LocalDateTime capturedAt,
            LocalDateTime uploadedAt, String status
    ) {}
}
