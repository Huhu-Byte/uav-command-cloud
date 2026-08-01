package com.uavcommand.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Map;

/**
 * DJI MQTT 双通道配置：入站订阅五 Topic、出站按请求类型分发到正确的应答 Topic。
 *
 * <p>2026-07-31 修正（依据 DJI 官方机场上云文档）：
 * <ul>
 *   <li>握手请求（config/bind/organization）在 {@code thing/product/{gwSn}/requests}，应答在 {@code requests_reply}</li>
 *   <li>{@code update_topo} 和设备上下线在 {@code sys/product/{gwSn}/status}，应答在 {@code status_reply}</li>
 *   <li>{@code osd/state/events} 仅供只读接收，不予应答</li>
 * </ul>
 */
@Configuration
public class DjiMqttConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(DjiMqttConfiguration.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DjiMqttProperties properties;
    private final DjiDockTelemetryParser telemetryParser;
    private final DjiDockHandshakeService handshakeService;
    private final DjiDockTopologyRegistry topologyRegistry;
    private final ApplicationContext applicationContext;

    public DjiMqttConfiguration(DjiMqttProperties properties,
                                 DjiDockTelemetryParser telemetryParser,
                                 DjiDockHandshakeService handshakeService,
                                 DjiDockTopologyRegistry topologyRegistry,
                                 ApplicationContext applicationContext) {
        this.properties = properties;
        this.telemetryParser = telemetryParser;
        this.handshakeService = handshakeService;
        this.topologyRegistry = topologyRegistry;
        this.applicationContext = applicationContext;
    }

    @Bean
    @Conditional(DjiMqttEnabledCondition.class)
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{properties.getBrokerUrl()});
        if (!properties.getUsername().isBlank()) options.setUserName(properties.getUsername());
        if (!properties.getPassword().isBlank()) options.setPassword(properties.getPassword().toCharArray());
        options.setConnectionTimeout((int) Math.ceil(properties.getConnectTimeoutMs() / 1000.0));
        options.setKeepAliveInterval(properties.getKeepAliveSec());
        options.setCleanSession(properties.isCleanSession());
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public SubscribableChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    @Conditional(DjiMqttEnabledCondition.class)
    public MqttPahoMessageHandler mqttOutbound() {
        String gatewaySn = properties.getGatewaySn();
        String clientId = properties.getClientId().isBlank()
                ? "uav-command-" + gatewaySn + "-outbound"
                : properties.getClientId() + "-outbound";
        MqttPahoMessageHandler handler = new MqttPahoMessageHandler(clientId, mqttClientFactory());
        handler.setDefaultTopic("sys/product/" + gatewaySn + "/status_reply");
        handler.setAsync(true);
        handler.setAsyncEvents(true);
        handler.setConverter(new DefaultPahoMessageConverter());
        LOGGER.info("MQTT 出站通道已创建 gatewaySn={}", gatewaySn);
        return handler;
    }

    @Bean
    @Conditional(DjiMqttEnabledCondition.class)
    public MqttPahoMessageDrivenChannelAdapter mqttInbound() {
        String gatewaySn = properties.getGatewaySn();
        String clientId = properties.getClientId().isBlank()
                ? "uav-command-" + gatewaySn + "-inbound"
                : properties.getClientId() + "-inbound";
        String[] topics = {
            "thing/product/" + gatewaySn + "/osd",
            "thing/product/" + gatewaySn + "/state",
            "thing/product/" + gatewaySn + "/events",
            "thing/product/" + gatewaySn + "/requests",
            "sys/product/" + gatewaySn + "/status"
        };
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(
                clientId, mqttClientFactory(), topics);
        adapter.setCompletionTimeout(properties.getConnectTimeoutMs());
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(new int[]{0, 0, 1, 0, 0});
        adapter.setOutputChannel(mqttInputChannel());
        LOGGER.info("MQTT 入站通道已创建 gatewaySn={} topics={}", gatewaySn, java.util.Arrays.toString(topics));
        return adapter;
    }

    @PostConstruct
    public void startMqttRouter() {
        if (!applicationContext.containsBean("mqttInbound")) {
            LOGGER.info("DJI MQTT 未启用或未配置网关 SN，跳过消息路由");
            return;
        }
        mqttInputChannel().subscribe(new MessageHandler() {
            @Override
            public void handleMessage(org.springframework.messaging.Message<?> message) {
               String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
               Object raw = message.getPayload();
               String payload = raw instanceof String s ? s : null;
                if (topic == null || payload == null) {
                    LOGGER.debug("MQTT 消息类型不匹配 topic={} rawClass={}", topic, raw != null ? raw.getClass().getSimpleName() : "null");
                    return;
                }
                if (topic.endsWith("/osd")) {
                    LOGGER.info("MQTT OSD 到达 topic={} payloadLength={} payloadStart={}", topic, payload.length(), payload.substring(0, Math.min(payload.length(), 20)));
                }
                try {
                    if (topic.endsWith("/osd"))       telemetryParser.parseOsd(topic, payload);
                    else if (topic.endsWith("/state"))     telemetryParser.parseState(topic, payload);
                    else if (topic.endsWith("/events"))    telemetryParser.parseEvent(topic, payload);
                    else if (topic.endsWith("/requests"))  handleRequestMessage(topic, payload);
                    else if (topic.endsWith("/status"))    handleStatusMessage(topic, payload);
                } catch (Exception e) {
                    LOGGER.warn("MQTT 消息处理失败 topic={}", topic, e);
                }
            }
        });
        LOGGER.info("DJI MQTT 消息路由已注册 gatewaySn={}", properties.getGatewaySn());
    }

    // ── 握手请求（config / bind / organization）──────────────────────────

    @SuppressWarnings("unchecked")
    private void handleRequestMessage(String topic, String payload) {
        try {
            Map<String, Object> root = MAPPER.readValue(payload, Map.class);
            String method = (String) root.get("method");
            String tid    = (String) root.get("tid");
            String bid    = (String) root.get("bid");
            if (method == null) return;
            if (!properties.isHandshakeEnabled()) {
                LOGGER.debug("握手未启用，丢弃 requests method={}", method);
                return;
            }
            String gatewaySn = extractSn(topic);
            String reply = switch (method) {
                case "config" ->
                    handshakeService.buildConfigReply(tid, bid);
                case "airport_bind_status" ->
                    handshakeService.buildAirportBindStatusReply(tid, bid, 0);
                case "airport_organization_get" ->
                    handshakeService.buildOrganizationGetReply(tid, bid, gatewaySn);
                case "airport_organization_bind" -> {
                    Map<String, Object> bindData = (Map<String, Object>) root.get("data");
                    String code = bindData != null ? (String) bindData.get("binding_code") : "";
                    yield handshakeService.buildOrganizationBindReply(tid, bid, gatewaySn, code);
                }
                default -> null;
            };
            if (reply != null && !reply.isEmpty()) {
                publishReply(topic, reply);
            }
        } catch (Exception e) {
            LOGGER.warn("握手请求处理失败 topic={}", topic, e);
        }
    }

    // ── 状态消息（update_topo / 设备上下线）──────────────────────────────

    @SuppressWarnings("unchecked")
    private void handleStatusMessage(String topic, String payload) {
        try {
            Map<String, Object> root = MAPPER.readValue(payload, Map.class);
            String method = (String) root.get("method");
            String gatewaySn = extractSn(topic);

            if ("update_topo".equals(method)) {
                String tid = (String) root.get("tid");
                String bid = (String) root.get("bid");
                Map<String, Object> topoData = (Map<String, Object>) root.get("data");
                if (topoData != null) parseAndRegisterTopo(gatewaySn, topoData);
                String reply = handshakeService.buildUpdateTopoReply(tid, bid);
                if (!reply.isEmpty()) publishReply(topic, reply);
            } else {
                telemetryParser.parseStatus(topic, payload);
            }
        } catch (Exception e) {
            LOGGER.warn("状态消息处理失败 topic={}", topic, e);
        }
    }

    @SuppressWarnings("unchecked")
    private void parseAndRegisterTopo(String gatewaySn, Map<String, Object> topoData) {
        try {
            var list = (java.util.List<Map<String, Object>>) topoData.get("sub_devices");
            if (list == null) return;
            java.util.List<DjiDockTopologyRegistry.SubDevice> devices = list.stream()
                    .map(d -> new DjiDockTopologyRegistry.SubDevice(
                            (String) d.get("sn"),
                            (String) d.getOrDefault("domain", ""),
                            (String) d.getOrDefault("type", ""),
                            (String) d.getOrDefault("sub_type", ""),
                            d.get("index") instanceof Number n ? n.intValue() : 0
                    )).toList();
            topologyRegistry.upsertGateway(gatewaySn, devices);
            LOGGER.info("注册拓扑 gatewaySn={} subDevices={}", gatewaySn, devices.size());
        } catch (Exception e) {
            LOGGER.warn("拓扑解析失败 gatewaySn={}", gatewaySn, e);
        }
    }

    // ── 出站应答 ──────────────────────────────────────────────────────────

    private void publishReply(String requestTopic, String replyPayload) {
        if (!applicationContext.containsBean("mqttOutbound")) {
            LOGGER.warn("MQTT 出站未就绪，应答未发送");
            return;
        }
        String replyTopic = requestTopic
                .replaceFirst("/requests$", "/requests_reply")
                .replaceFirst("/status$", "/status_reply");
        try {
            MqttPahoMessageHandler outbound = applicationContext.getBean(MqttPahoMessageHandler.class);
            outbound.handleMessage(
                    MessageBuilder.withPayload(replyPayload)
                            .setHeader("mqtt_topic", replyTopic)
                            .build());
            LOGGER.debug("应答已发送 topic={}", replyTopic);
        } catch (Exception e) {
            LOGGER.error("应答发送失败 topic={}", replyTopic, e);
        }
    }

    private String extractSn(String topic) {
        String[] parts = topic.split("/");
        return parts.length >= 3 ? parts[2] : "unknown";
    }
}
