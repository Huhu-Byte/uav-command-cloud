package com.uavcommand.realtime;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;

/**
 * 对象存储服务（MinIO 优先），用于上传 KMZ 航线文件和巡检媒体。
 *
 * <p>未启用时（app.oss.enabled=false）自动降级为 Mock 模式，返回模拟的 URL 和 STS 凭证，
 * 方便本地开发调试。</p>
 *
 * <p>STS 凭证返回给 DJI Dock 机场，机场直接通过 MinIO S3 接口上传巡检照片和视频。</p>
 */
@Service
public class OssService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OssService.class);

    private final OssProperties properties;
    private volatile MinioClient minioClient;

    public OssService(OssProperties properties) {
        this.properties = properties;
        if (properties.isEnabled()) {
            try {
                this.minioClient = MinioClient.builder()
                        .endpoint(properties.getEndpoint())
                        .credentials(properties.getAccessKey(), properties.getSecretKey())
                        .region(properties.getRegion())
                        .build();
                ensureBucket();
                LOGGER.info("对象存储已启用 provider={} endpoint={} bucket={}", properties.getProvider(), properties.getEndpoint(), properties.getBucket());
            } catch (Exception e) {
                LOGGER.error("对象存储初始化失败，自动降级为 Mock 模式", e);
                this.minioClient = null;
            }
        } else {
            LOGGER.info("对象存储未启用，使用 Mock 模式");
        }
    }

    /**
     * 上传 KMZ 文件并返回可访问 URL 和指纹。
     *
     * @param kmzBytes  KMZ 文件字节
     * @param flightId  任务 ID，用于 objectKey 命名
     * @return 上传结果
     */
    public UploadResult uploadKmz(byte[] kmzBytes, String flightId, String fingerprint) {
        String objectKey = properties.getWaylinePrefix() + "/" + flightId + ".kmz";
        String url;
        if (minioClient != null) {
            try {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(properties.getBucket())
                        .object(objectKey)
                        .stream(new ByteArrayInputStream(kmzBytes), kmzBytes.length, -1)
                        .contentType("application/vnd.google-earth.kmz")
                        .build());
                // 生成预签名 URL（24 小时有效），供 Dock 下载航线文件
                String downloadEndpoint = properties.getPublicEndpoint().isBlank()
                        ? properties.getEndpoint() : properties.getPublicEndpoint();
                url = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                        .bucket(properties.getBucket())
                        .object(objectKey)
                        .method(Method.GET)
                        .expiry(24, java.util.concurrent.TimeUnit.HOURS)
                        .build());
                // 如果配置了公网域名，替换 endpoint 部分
                if (!properties.getPublicEndpoint().isBlank()) {
                    url = url.replaceFirst("https?://[^/]+", properties.getPublicEndpoint());
                }
                LOGGER.info("KMZ 上传成功 objectKey={} size={}KB fingerprint={}", objectKey, kmzBytes.length / 1024, fingerprint.substring(0, 8));
            } catch (Exception e) {
                LOGGER.error("KMZ 上传失败 objectKey={}", objectKey, e);
                throw new RuntimeException("KMZ 上传到对象存储失败", e);
            }
        } else {
            // Mock 模式：返回模拟的 URL
            url = (properties.getEndpoint().isBlank() ? "http://localhost:9000" : properties.getEndpoint())
                    + "/" + properties.getBucket() + "/" + objectKey
                    + "?mock-signature=" + fingerprint.substring(0, 16);
            LOGGER.info("KMZ Mock 上传 objectKey={} size={}KB mockUrl={}", objectKey, kmzBytes.length / 1024, url);
        }
        return new UploadResult(objectKey, url, fingerprint, kmzBytes.length);
    }

    /**
     * 获取 STS 临时凭证（DJI Dock 上传媒体文件用）。
     * 生产环境应调 MinIO assume-role 或阿里云 STS，这里返回长期凭证封装成 STS 格式（短期安全）。
     */
    public StsCredentialsResponse getStsCredentials() {
        long expireAtSec = Instant.now().getEpochSecond() + properties.getStsExpireSec();
        // Mock 模式返回假凭证；真实环境建议调用 MinIO assumeRole 生成临时凭证
        String accessKeyId = properties.isEnabled() ? properties.getAccessKey() : "mock-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
        String accessKeySecret = properties.isEnabled() ? properties.getSecretKey() : "mock-secret-" + UUID.randomUUID().toString().substring(0, 16);
        String securityToken = properties.isEnabled() ? "" : "mock-security-token-" + UUID.randomUUID().toString().replace("-", "");
        String endpoint = properties.getPublicEndpoint().isBlank()
                ? properties.getEndpoint() : properties.getPublicEndpoint();

        StsCredentialsResponse.CredentialsToken credentials = new StsCredentialsResponse.CredentialsToken(
                accessKeyId, accessKeySecret, securityToken, properties.getStsExpireSec() - 300L
        );
        return new StsCredentialsResponse(
                properties.getBucket(),
                credentials,
                endpoint.isBlank() ? "http://localhost:9000" : endpoint,
                properties.getMediaPrefix() + "/",
                StsCredentialsResponse.OssProvider.MINIO,
                properties.getRegion()
        );
    }

    private void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(properties.getBucket()).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
                LOGGER.info("自动创建存储桶 bucket={}", properties.getBucket());
            }
        } catch (Exception e) {
            LOGGER.warn("存储桶检查失败 bucket={}", properties.getBucket(), e);
        }
    }

    /** KMZ 上传结果。 */
    public record UploadResult(String objectKey, String url, String fingerprint, long sizeBytes) {}

    /** STS 临时凭证响应（DJI 接口规范兼容）。 */
    public record StsCredentialsResponse(
            String bucket,
            CredentialsToken credentials,
            String endpoint,
            String objectKeyPrefix,
            OssProvider provider,
            String region
    ) {
        public record CredentialsToken(
                String accessKeyId,
                String accessKeySecret,
                String securityToken,
                long expire
        ) {}

        public enum OssProvider {
            ALIYUN("ali"),
            AWS("aws"),
            MINIO("minio");
            private final String value;
            OssProvider(String value) { this.value = value; }
            @com.fasterxml.jackson.annotation.JsonValue
            public String getValue() { return value; }
        }
    }
}
