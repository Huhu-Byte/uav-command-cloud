package com.uavcommand.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

@Configuration
public class DjiMqttConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(DjiMqttConfiguration.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DjiMqttProperties properties;
    private final DjiDockTelemetryParser telemetryParser;
    private final DjiDockHandshakeService handshakeService;
    private final DjiDockTopologyRegistry topologyRegistry;

    /**
     * 修复缺口2：改为字段注入，required=false 应对 MQTT 未启用时 Bean 不存在的情况。
     * 原实现在 @PostConstruct 和 publishReply() 中直接调用 @Bean 方法，
     * 导致每次调用都绕过 Spring 代理、创建新的 MQTT 连接实例。
     */
    @Autowired(required = false)
    private MqttPahoMessageDrivenChannelAdapter mqttInboundAdapter;

    @Autowired(required = false)
    private MqttPahoMessageHandler mqttOutboundHandler;

    public DjiMqttConfiguration(DjiMqttProperties properties,
                                 DjiDockTelemetryParser telemetryParser,
                                 DjiDockHandshakeService handshakeService,
                                 DjiDockTopologyRegistry topologyRegistry) {
        this.properties = properties;
        this.telemetryParser = telemetryParser;
        this.handshakeService = handshakeService;
        this.topologyRegistry = topologyRegistry;
    }

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{properties.getBrokerUrl()});
        if (!properties.getUsername().isBlank()) options.setUserName(properties.getUsername());
        if (!properties.getPassword().isBlank()) options.setPassword(properties.getPassword().toCharArray());
        // ceil 除法防止 8500ms 被截断为 8s
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

    /**
     * 出站通道：仅在 MQTT 已启用且配置了网关 SN 时注册，
     * 避免未启用时 Spring 容器中出现 null Bean 警告。
     */
    @Bean
    @ConditionalOnProperty(name = "app.dji-mqtt.enabled", havingValue = "true")
    public MqttPahoMessageHandler mqttOutbound() {
        String gatewaySn = properties.getGatewaySn();
        if (gatewaySn.isBlank()) {
            LOGGER.warn("DJI MQTT 已启用但未配置网关 SN，出站通道不创建");
            return null;
        }
        String clientId = properties.getClientId().isBlank()
                ? "uav-command-" + gatewaySn + "-outbound"
                : properties.getClientId() + "-outbound";
        MqttPahoMessageHandler handler = new MqttPahoMessageHandler(clientId, mqttClientFactory());
        handler.setDefaultTopic("sys/product/" + gatewaySn + "/status_reply");
        handler.setAsync(true);
        handler.setAsyncEvents(true);
        handler.setConverter(new DefaultPahoMessageConverter());
        return handler;
    }

    /**
     * 入站通道：仅在 MQTT 已启用且配置了网关 SN 时注册。
     */
    @Bean
    @ConditionalOnProperty(name = "app.dji-mqtt.enabled", havingValue = "true")
    public MqttPahoMessageDrivenChannelAdapter mqttInbound() {
        String gatewaySn = properties.getGatewaySn();
        if (gatewaySn.isBlank()) {
            LOGGER.warn("DJI MQTT 已启用但未配置网关 SN，入站通道不创建");
            return null;
        }
        String clientId = properties.getClientId().isBlank()
                ? "uav-command-" + gatewaySn + "-inbound"
                : properties.getClientId() + "-inbound";
        String[] topics = {
            "thing/product/" + gatewaySn + "/osd",
            "thing/product/" + gatewaySn + "/state",
            "thing/product/" + gatewaySn + "/events",
            "sys/product/" + gatewaySn + "/status"
        };
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(
                clientId, mqttClientFactory(), topics);
        adapter.setCompletionTimeout(properties.getConnectTimeoutMs());
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(new int[]{0, 0, 1, 0});
        adapter.setOutputChannel(mqttInputChannel());
        return adapter;
    }

    /**
     * 消息路由：订阅入站 Channel，按主题后缀分发给解析器和握手处理器。
     * 修复缺口2：使用注入的 mqttInboundAdapter 字段判断，而非直接调用 @Bean 方法。
     */
    @PostConstruct
    public void startMqttRouter() {
        if (mqttInboundAdapter == null) {
            LOGGER.info("DJI MQTT 未启用，跳过消息路由注册");
            return;
        }
        mqttInputChannel().subscribe(new MessageHandler() {
            @Override
            public void handleMessage(org.springframework.messaging.Message<?> message) {
                String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
                Object raw = message.getPayload();
                String payload = raw instanceof String s ? s : null;
                if (topic == null || payload == null) return;
                try {
                    if      (topic.endsWith("/osd"))    telemetryParser.parseOsd(topic, payload);
                    else if (topic.endsWith("/state"))  telemetryParser.parseState(topic, payload);
                    else if (topic.endsWith("/events")) telemetryParser.parseEvent(topic, payload);
                    else if (topic.endsWith("/status")) handleStatusMessage(topic, payload);
                } catch (Exception e) {
                    LOGGER.warn("MQTT 消息处理失败 topic={}", topic, e);
                }
            }
        });
        LOGGER.info("DJI MQTT 消息路由已注册 gatewaySn=", properties.getGatewaySn());
    }

    @SuppressWarnings("unchecked")
    private void handleStatusMessage(String topic, String payload) {
        try {
            Map<String, Object> root = MAPPER.readValue(payload, Map.class);
            String method = (String) root.get("method");
            String tid    = (String) root.get("tid");
            String bid    = (String) root.get("bid");
            if (method == null) return;
            if (!properties.isHandshakeEnabled()) {
                LOGGER.debug("握手未启用，丢弃 status method={}", method);
                return;
            }
            String gatewaySn = extractSn(topic);
            String reply = switch (method) {
                case "config" ->
                    handshakeService.buildConfigReply(tid, bid);
                case "airport_bind_status" ->
                    handshakeService.buildAirportBindStatusReply(tid, bid, 0);
                case "update_topo" -> {
                    Map<String, Object> topoData = (Map<String, Object>) root.get("data");
                    if (topoData != null) parseAndRegisterTopo(gatewaySn, topoData);
                    yield handshakeService.buildUpdateTopoReply(tid, bid);
                }
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
            LOGGER.warn("握手消息处理失败 topic={}", topic, e);
        }
    }

    @SuppressWarnings("unchecked")
    private void parseAndRegisterTopo(String gatewaySn, Map<String, Object> topoData) {
        try {
            var subDeviceList = (java.util.List<Map<String, Object>>) topoData.get("sub_devices");
            if (subDeviceList == null) return;
            java.util.List<DjiDockTopologyRegistry.SubDevice> devices = subDeviceList.stream()
                    .map(d -> new DjiDockTopologyRegistry.SubDevice(
                            (String) d.get("sn"),
                            (String) d.getOrDefault("domain", ""),
                            (String) d.getOrDefault("type", ""),
                            (String) d.getOrDefault("sub_type", ""),
                            d.get("index") instanceof Number n ? n.intValue() : 0
                    )).toList();
            topologyRegistry.upsertGateway(gatewaySn, devices);
            LOGGER.info("握手注册拓扑 gatewaySn={} devices={}", gatewaySn, devices.size());
        } catch (Exception e) {
            LOGGER.warn("拓扑解析失败 gatewaySn={}", gatewaySn, e);
        }
    }

    /**
     * 修复缺口2：使用注入的 mqttOutboundHandler 字段，而非直接调用 @Bean 方法。
     * 原实现每次调用 mqttOutbound() 都会构造新的 MqttPahoMessageHandler 实例（新的 MQTT 连接）。
     */
    private void publishReply(String requestTopic, String replyPayload) {
        if (mqttOutboundHandler == null) {
            LOGGER.warn("MQTT 出站未就绪，无法发送握手应答 topic={}", requestTopic);
            return;
        }
        String replyTopic = requestTopic.replaceFirst("/status$", "/status_reply");
        try {
            mqttOutboundHandler.handleMessage(
                    MessageBuilder.withPayload(replyPayload)
                            .setHeader("mqtt_topic", replyTopic)
                            .build());
            LOGGER.debug("已发送握手应答 topic={}", replyTopic);
        } catch (Exception e) {
            LOGGER.error("握手应答发送失败 topic={}", replyTopic, e);
        }
    }

    private String extractSn(String topic) {
        String[] parts = topic.split("/");
        return parts.length >= 3 ? parts[2] : "unknown";
    }
}
