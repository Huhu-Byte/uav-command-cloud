package com.uavcommand.realtime;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * DJI MQTT 下行指令发布器。
 *
 * <p>负责向机场 MQTT Topic 发布控制指令，格式遵循 DJI Cloud API 规范：
 * <pre>
 *  服务调用:      thing/product/{sn}/services        →  thing/product/{sn}/services_reply
 *  属性设置:      thing/product/{sn}/property/set    →  thing/product/{sn}/property/set_reply
 *  航线任务:      thing/product/{sn}/services        (method=flighttask_create/prepare/execute)
 *  返航指令:      thing/product/{sn}/services        (method=return_home)
 *  握手应答:      thing/product/{sn}/requests_reply  /sys/product/{sn}/status_reply
 * </pre>
 *
 * <p>每条请求带唯一 tid，services_reply 会携带相同 tid，可实现请求-应答匹配。</p>
 */
@Service
public class DjiMqttCommandPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(DjiMqttCommandPublisher.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** 指令默认超时（秒）。 */
    private static final int DEFAULT_TIMEOUT_SEC = 30;

    private final ApplicationContext applicationContext;
    private final DjiMqttProperties mqttProperties;
    /** 等待应答的请求：tid → CompletableFuture<JSON Map>。 */
    private final Map<String, CompletableFuture<Map<String, Object>>> pendingRequests = new ConcurrentHashMap<>();

    public DjiMqttCommandPublisher(ApplicationContext applicationContext, DjiMqttProperties mqttProperties) {
        this.applicationContext = applicationContext;
        this.mqttProperties = mqttProperties;
    }

    /**
     * 发布 flighttask_create（航线任务创建）。
     *
     * @param gatewaySn 网关 SN
     * @param flightId  任务 ID
     * @param fileUrl   KMZ 文件下载地址
     * @param fileSign  KMZ 文件 MD5
     */
    public Map<String, Object> publishFlighttaskCreate(String gatewaySn, String flightId, String fileUrl, String fileSign) {
        Map<String, Object> payload = Map.of(
                "flight_id", flightId,
                "type", "wayline",
                "file", Map.of("url", fileUrl, "sign", fileSign)
        );
        return publishServiceAndWait(gatewaySn, "flighttask_create", payload);
    }

    /**
     * 发布 flighttask_prepare（航线任务预检，上传并解析 KMZ）。
     */
    public Map<String, Object> publishFlighttaskPrepare(String gatewaySn, String flightId) {
        Map<String, Object> payload = Map.of("flight_id", flightId);
        return publishServiceAndWait(gatewaySn, "flighttask_prepare", payload);
    }

    /**
     * 发布 flighttask_execute（航线任务开始执行）。
     */
    public Map<String, Object> publishFlighttaskExecute(String gatewaySn, String flightId) {
        Map<String, Object> payload = Map.of("flight_id", flightId);
        return publishServiceAndWait(gatewaySn, "flighttask_execute", payload);
    }

    /** 发布 flighttask_pause（暂停执行）。 */
    public Map<String, Object> publishFlighttaskPause(String gatewaySn, String flightId) {
        return publishServiceAndWait(gatewaySn, "flighttask_pause", Map.of("flight_id", flightId));
    }

    /** 发布 flighttask_recovery（从暂停恢复）。 */
    public Map<String, Object> publishFlighttaskRecovery(String gatewaySn, String flightId) {
        return publishServiceAndWait(gatewaySn, "flighttask_recovery", Map.of("flight_id", flightId));
    }

    /** 发布 return_home（返航）。 */
    public Map<String, Object> publishReturnHome(String gatewaySn) {
        return publishServiceAndWait(gatewaySn, "return_home", Map.of());
    }

    /** 发布 property/set —— 设置机场属性（如开舱盖、急停、充电模式等）。
     *
     * @param gatewaySn
     * @param property  如 "cover_open"（开舱盖）、"emergency_stop"（急停）等
     * @param value     属性值，如 "1"（开/启用）或 "0"（关/禁用）
     */
    public Map<String, Object> publishPropertySet(String gatewaySn, String property, String value) {
        String tid = UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> wrapper = Map.of(
                "tid", tid,
                "bid", gatewaySn,
                "method", property,
                "timestamp", Instant.now().toEpochMilli(),
                "data", Map.of(property, value)
        );
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        pendingRequests.put(tid, future);
        publish("thing/product/" + gatewaySn + "/property/set", wrapper);
        try {
            return future.get(DEFAULT_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pendingRequests.remove(tid);
            LOGGER.warn("property/set 超时 property={} tid={}", property, tid);
            throw new RuntimeException("指令超时：" + property);
        } catch (Exception e) {
            pendingRequests.remove(tid);
            throw new RuntimeException("指令执行失败", e);
        }
    }

    /** 发布 services 指令并等待 services_reply。超时返回异常，结果包含 data 层（DJI 规范中 code=0 表示成功）。 */
    public Map<String, Object> publishServiceAndWait(String gatewaySn, String method, Map<String, Object> data) {
        // MQTT 未启用时直接返回 Mock 成功，避免等待超时
        if (!mqttProperties.isEnabled()) {
            LOGGER.info("[MQTT DISABLED] mock services method={} gatewaySn={}", method, gatewaySn);
            return Map.of("code", 0, "message", "success", "data", Map.of("mock", true));
        }
        String tid = UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> wrapper = Map.of(
                "tid", tid,
                "bid", gatewaySn,
                "method", method,
                "timestamp", Instant.now().toEpochMilli(),
                "data", data == null ? Map.of() : data
        );
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        pendingRequests.put(tid, future);
        publish("thing/product/" + gatewaySn + "/services", wrapper);
        LOGGER.info("发布 services method={} gatewaySn={} tid={}", method, gatewaySn, tid);
        try {
            Map<String, Object> reply = future.get(DEFAULT_TIMEOUT_SEC, TimeUnit.SECONDS);
            LOGGER.info("services_reply 到达 method={} tid={} replyCode={}", method, tid, reply.get("code"));
            return reply;
        } catch (TimeoutException e) {
            pendingRequests.remove(tid);
            LOGGER.warn("services 超时 method={} gatewaySn={} tid={}", method, gatewaySn, tid);
            throw new RuntimeException("指令超时：" + method);
        } catch (Exception e) {
            pendingRequests.remove(tid);
            throw new RuntimeException("指令执行失败", e);
        }
    }

    /** 发布 services 指令（不等待应答，用于非关键场景）。 */
    public void publishServiceFireAndForget(String gatewaySn, String method, Map<String, Object> data) {
        String tid = UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> wrapper = Map.of(
                "tid", tid,
                "bid", gatewaySn,
                "method", method,
                "timestamp", Instant.now().toEpochMilli(),
                "data", data == null ? Map.of() : data
        );
        publish("thing/product/" + gatewaySn + "/services", wrapper);
        LOGGER.info("发布 services(不等待应答) method={} gatewaySn={}", method, gatewaySn);
    }

    /**
     * 当 services_reply 到达时被 DjiMqttConfiguration 调用，匹配 tid 并唤醒等待线程。
     */
    public void onServicesReply(String tid, Map<String, Object> reply) {
        CompletableFuture<Map<String, Object>> future = pendingRequests.remove(tid);
        if (future != null) {
            future.complete(reply);
        } else {
            LOGGER.debug("收到未匹配的 services_reply tid={}", tid);
        }
    }

    /**
     * 当 property/set_reply 到达时被 DjiMqttConfiguration 调用。
     */
    public void onPropertySetReply(String tid, Map<String, Object> reply) {
        CompletableFuture<Map<String, Object>> future = pendingRequests.remove(tid);
        if (future != null) {
            future.complete(reply);
        } else {
            LOGGER.debug("收到未匹配的 property/set_reply tid={}", tid);
        }
    }

    /** 发布任意 MQTT 消息。当 MQTT 未启用时仅打日志，方便本地 mock 联调。 */
    public void publish(String topic, Object payload) {
        if (!mqttProperties.isEnabled() || !applicationContext.containsBean("mqttOutbound")) {
            String payloadStr;
            try { payloadStr = payload instanceof String s ? s : MAPPER.writeValueAsString(payload); }
            catch (Exception e) { payloadStr = String.valueOf(payload); }
            LOGGER.info("[MQTT DISABLED] 未发布 topic={} payloadLength={}", topic, payloadStr.length());
            return;
        }
        try {
            String payloadStr = payload instanceof String s ? s : MAPPER.writeValueAsString(payload);
            MqttPahoMessageHandler outbound = applicationContext.getBean(MqttPahoMessageHandler.class);
            outbound.handleMessage(MessageBuilder.withPayload(payloadStr)
                    .setHeader("mqtt_topic", topic)
                    .build());
            LOGGER.debug("MQTT 已发布 topic={}", topic);
        } catch (Exception e) {
            LOGGER.error("MQTT 发布失败 topic={}", topic, e);
            throw new RuntimeException("MQTT 发布失败", e);
        }
    }
}
