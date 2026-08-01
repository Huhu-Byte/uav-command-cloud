package com.uavcommand.realtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 对象存储配置（MinIO / 阿里云 OSS）。 */
@ConfigurationProperties(prefix = "app.oss")
public class OssProperties {
    private boolean enabled;
    /** 存储类型：minio（默认）或 aliyun */
    private String provider = "minio";
    /** 对象存储服务地址，如 http://192.168.1.10:9000 */
    private String endpoint = "";
    private String accessKey = "";
    private String secretKey = "";
    /** 存储桶名，默认 wayline-storage */
    private String bucket = "uav-command";
    /** 区域（仅阿里云需要，MinIO 填 us-east-1 即可） */
    private String region = "us-east-1";
    /** KMZ 上传的目录前缀，如 waylines/kmz */
    private String waylinePrefix = "waylines";
    /** 媒体上传的目录前缀 */
    private String mediaPrefix = "media";
    /** STS 临时凭证有效期（秒），默认 3600 */
    private int stsExpireSec = 3600;
    /** 下载 URL 的公共访问域名（内网穿透/反代后用）。留空则使用 endpoint */
    private String publicEndpoint = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider == null ? "" : provider.trim(); }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint == null ? "" : endpoint.trim(); }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey == null ? "" : accessKey.trim(); }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey == null ? "" : secretKey.trim(); }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket == null ? "" : bucket.trim(); }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region == null ? "" : region.trim(); }
    public String getWaylinePrefix() { return waylinePrefix; }
    public void setWaylinePrefix(String waylinePrefix) { this.waylinePrefix = waylinePrefix == null ? "" : waylinePrefix.trim(); }
    public String getMediaPrefix() { return mediaPrefix; }
    public void setMediaPrefix(String mediaPrefix) { this.mediaPrefix = mediaPrefix == null ? "" : mediaPrefix.trim(); }
    public int getStsExpireSec() { return stsExpireSec; }
    public void setStsExpireSec(int stsExpireSec) { this.stsExpireSec = stsExpireSec; }
    public String getPublicEndpoint() { return publicEndpoint; }
    public void setPublicEndpoint(String publicEndpoint) { this.publicEndpoint = publicEndpoint == null ? "" : publicEndpoint.trim(); }
}
