package com.uavcommand.realtime;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对象存储 REST 接口。
 *
 * <p>提供 DJI Dock 机场需要的 STS 临时凭证获取接口，
 * 机场拿到凭证后直接与 MinIO/OSS 通信，上传巡检照片和视频媒体文件。
 * 路径前缀 /api/v1/oss。</p>
 */
@RestController
@RequestMapping("/api/v1/oss")
public class OssController {
    private final OssService ossService;

    public OssController(OssService ossService) {
        this.ossService = ossService;
    }

    /**
     * 获取 STS 临时凭证（DJI Dock 上传媒体文件使用）。
     *
     * <p>返回的凭证字段与 DJI 官方 Cloud API 兼容：
     * <ul>
     *   <li>bucket：存储桶名</li>
     *   <li>credentials：accessKeyId / accessKeySecret / securityToken / expire</li>
     *   <li>endpoint：对象存储访问端点</li>
     *   <li>objectKeyPrefix：媒体文件上传路径前缀，如 media/</li>
     *   <li>provider：ali / aws / minio</li>
     *   <li>region：区域，如 us-east-1</li>
     * </ul>
     */
    @GetMapping("/sts-credentials")
    public OssService.StsCredentialsResponse getStsCredentials() {
        return ossService.getStsCredentials();
    }
}
