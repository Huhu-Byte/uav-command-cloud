package com.uavcommand.realtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 真实 DJI Cloud API 的服务器端配置。密钥只允许由部署环境注入。 */
@ConfigurationProperties(prefix = "app.dji-cloud")
public class DjiCloudApiProperties {
    private boolean enabled;
    private String baseUrl = "";
    private String clientId = "";
    private String clientSecret = "";
    private String appId = "";
    private String appKey = "";
    private String appLicense = "";
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 5000;
    private int maxRetries = 2;
    /** JWT token 有效期（秒），默认 24 小时。 */
    private int tokenExpireSec = 86400;
    /** JWT 签名密钥，生产环境必须通过环境变量注入。 */
    private String tokenSecret = "uav-command-default-secret";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl == null ? "" : baseUrl.trim(); }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId == null ? "" : clientId.trim(); }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret == null ? "" : clientSecret.trim(); }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId == null ? "" : appId.trim(); }
    public String getAppKey() { return appKey; }
    public void setAppKey(String appKey) { this.appKey = appKey == null ? "" : appKey.trim(); }
    public String getAppLicense() { return appLicense; }
    public void setAppLicense(String appLicense) { this.appLicense = appLicense == null ? "" : appLicense.trim(); }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public int getTokenExpireSec() { return tokenExpireSec; }
    public void setTokenExpireSec(int tokenExpireSec) { this.tokenExpireSec = tokenExpireSec; }
    public String getTokenSecret() { return tokenSecret; }
    public void setTokenSecret(String tokenSecret) { this.tokenSecret = tokenSecret == null ? "" : tokenSecret; }
}
