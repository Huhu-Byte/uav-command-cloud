package com.uavcommand.realtime;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 媒体文件 REST 接口。
 *
 * <p>提供媒体文件列表查询和上传回调接口。
 * 路径前缀 /api/v1/media。</p>
 */
@RestController
@RequestMapping("/api/v1/media")
public class MediaFileController {
    private final MediaFileService mediaFileService;

    public MediaFileController(MediaFileService mediaFileService) {
        this.mediaFileService = mediaFileService;
    }

    /** 获取所有媒体文件列表。 */
    @GetMapping
    public List<MediaFileService.MediaFileView> list() {
        return mediaFileService.list();
    }

    /** 按任务 flightId 查询媒体文件。 */
    @GetMapping("/flight/{flightId}")
    public List<MediaFileService.MediaFileView> byFlight(@PathVariable String flightId) {
        return mediaFileService.findByFlightId(flightId);
    }

    /**
     * 媒体文件上传回调（DJI Dock 通过 MQTT events 上报后，后端调用此接口归档）。
     * 也可由前端直接调用测试。
     */
    @PostMapping("/upload-callback")
    public MediaFileService.MediaFileView uploadCallback(@RequestBody MediaFileService.UploadCallbackRequest request) {
        return mediaFileService.recordUpload(request);
    }
}
