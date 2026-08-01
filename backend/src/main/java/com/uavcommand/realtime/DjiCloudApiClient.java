package com.uavcommand.realtime;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 为未来的只读 DJI API 调用提供统一的配置检查、超时边界和重试逻辑。
 * 该类当前没有接入任何真实地址，也不会发送控制指令。
 */
@Service
public class DjiCloudApiClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(DjiCloudApiClient.class);
    private final DjiCloudApiProperties properties;
    private final DjiMqttProperties mqttProperties;

    public DjiCloudApiClient(DjiCloudApiProperties properties, DjiMqttProperties mqttProperties) {
        this.properties = properties;
        this.mqttProperties = mqttProperties;
    }

    public Readiness readiness() {
        // 修复问题5a：从 DjiMqttProperties 读取真实 MQTT 配置，不再硬编码 false/null
       MqttStatus mqtt = new MqttStatus(
               mqttProperties.isEnabled(),
                mqttProperties.isEnabled() ? mqttProperties.getBrokerUrl() : null,
                mqttProperties.isHandshakeEnabled(),
                mqttProperties.getGatewaySn(),
                mqttProperties.isEmbeddedBroker(),
                mqttProperties.getBrokerPort()
       );
        if (!properties.isEnabled()) {
            return new Readiness(false, false, "真实 DJI Cloud API 尚未启用，系统继续使用本机模拟器", 0, 0, 0, mqtt);
        }
        if (isBlank(properties.getBaseUrl()) || isBlank(properties.getClientId()) || isBlank(properties.getClientSecret())) {
            return new Readiness(true, false, "真实 DJI Cloud API 已启用，但服务器环境变量尚未完整配置", 0, 0, 0, mqtt);
        }
        if (!isSafeBaseUrl(properties.getBaseUrl())) {
            return new Readiness(true, false, "DJI API 地址无效；真实环境必须使用 HTTPS，本机联调可使用 localhost", 0, 0, 0, mqtt);
        }
        if (properties.getConnectTimeoutMs() <= 0
                || properties.getReadTimeoutMs() <= 0
                || properties.getMaxRetries() < 0
                || properties.getMaxRetries() > 5) {
            return new Readiness(true, false, "DJI API 的超时或重试配置无效，已拒绝建立连接", 0, 0, 0, mqtt);
        }
        // 修复问题5b：格式对齐，逗号在行尾
        return new Readiness(
                true,
                true,
                "真实 DJI Cloud API 配置已就绪；当前仍未发送任何真实设备请求",
                properties.getConnectTimeoutMs(),
                properties.getReadTimeoutMs(),
                properties.getMaxRetries(),
                mqtt
        );
    }

    /**
     * 未来只读状态查询的统一重试入口。调用方只能在 readiness 为 configured 时使用。
     * 日志只记录固定操作类型、次数和异常类别，避免 URL、账号、令牌或密钥进入日志。
     */
    <T> T executeReadOnly(ReadOperation operation, Supplier<T> request) {
        requireConfigured();
        Objects.requireNonNull(operation, "DJI 只读操作类型不能为空");
        Objects.requireNonNull(request, "DJI 只读请求不能为空");

        int totalAttempts = properties.getMaxRetries() + 1;
        RestClientException lastError = null;
        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                return request.get();
            } catch (RestClientException error) {
                lastError = error;
                LOGGER.warn("DJI 只读请求 {} 第 {}/{} 次失败，异常类型：{}", operation, attempt, totalAttempts,
                        error.getClass().getSimpleName());
            }
        }
        throw new IllegalStateException("DJI 只读请求在重试后仍未成功，请查看服务器日志", lastError);
    }

    /**
     * 为阶段 C 第 4 项预留的只读 GET 入口。当前没有控制器调用它，因此不会主动连接真实设备。
     * 认证头或签名必须等官方接入方式确认后再增加，不能猜测协议。
     */
    <T> T getReadOnly(ReadOperation operation, String relativePath, Class<T> responseType) {
        validateRelativePath(relativePath);
        Objects.requireNonNull(responseType, "DJI 响应类型不能为空");
        RestClient restClient = createReadOnlyClient();
        return executeReadOnly(operation, () -> restClient.get()
                .uri(relativePath)
                .retrieve()
                .body(responseType));
    }

    RestClient createReadOnlyClient() {
        requireConfigured();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    private void requireConfigured() {
        Readiness readiness = readiness();
        if (!readiness.configured()) {
            throw new IllegalStateException(readiness.message());
        }
    }

    private void validateRelativePath(String relativePath) {
        if (isBlank(relativePath)) {
            throw new IllegalArgumentException("DJI 只读请求路径不能为空");
        }
        URI uri;
        try {
            uri = URI.create(relativePath);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("DJI 只读请求路径格式无效", error);
        }
        if (uri.isAbsolute() || !relativePath.startsWith("/") || uri.getHost() != null) {
            throw new IllegalArgumentException("DJI 只读请求只允许使用相对路径");
        }
    }

    private boolean isSafeBaseUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null) {
                return false;
            }
            if ("https".equalsIgnoreCase(uri.getScheme())) {
                return true;
            }
            return "http".equalsIgnoreCase(uri.getScheme()) && isLoopbackHost(uri.getHost());
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private boolean isLoopbackHost(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public enum ReadOperation {
        DEVICE_STATUS,
        ALERTS
    }

    public record Readiness(
            boolean enabled,
            boolean configured,
            String message,
            int connectTimeoutMs,
            int readTimeoutMs,
            int maxRetries,
            MqttStatus mqtt
    ) { }

    public record MqttStatus(
            boolean enabled,
            String brokerUrl,
            boolean handshakeEnabled,
            String gatewaySn,
            boolean embeddedBroker,
            int brokerPort
    ) { }
}
