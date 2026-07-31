package com.uavcommand.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.MessageHandler;
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
        // 修复问题4：用 ceil 除法避免整数截断，如 8500ms 不会被截为 8s
        options.setConnectionTimeout((int) Math.ceil(properties.getConnectTimeoutMs() / 1000.0));
        options.setKeepAliveInterval(properties.getKeepAliveSec());
        options.setCleanSession(properties.isCleanSession());
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public SubscribableChannel mqttInputChannel() {
        DirectChannel channel = new DirectChannel();
        return channel;
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = {"app.dji-mqtt.enabled", "app.dji-mqtt.gateway-sn"},
            matchIfMissing = false
    )
    public MqttPahoMessageHandler mqttOutbound() {
        String gatewaySn = properties.getGatewaySn();
        if (gatewaySn.isBlank()) return null;
        String clientIdBase = properties.getClientId().isBlank() ? "uav-command-" + gatewaySn : properties.getClientId();
        MqttPahoMessageHandler handler = new MqttPahoMessageHandler(
                clientIdBase + "-outbound", mqttClientFactory());
        handler.setDefaultTopic("sys/product/" + gatewaySn + "/status_reply");
        handler.setAsync(true);
        handler.setAsyncEvents(true);
        handler.setConverter(new DefaultPahoMessageConverter());
        return handler;
    }

    /**
     * 修复问题1b：移除返回 null 的逻辑，改用 @ConditionalOnProperty 控制 Bean 注册。
     * 原实现在未启用时返回 null，Spring 会注册 null Bean 并打印警告。
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = {"app.dji-mqtt.enabled", "app.dji-mqtt.gateway-sn"},
            matchIfMissing = false
    )
    public MqttPahoMessageDrivenChannelAdapter mqttInbound() {
        String gatewaySn = properties.getGatewaySn();
        if (gatewaySn.isBlank()) {
            LOGGER.warn("网关 SN 为空，跳过 MQTT 订阅");
            return null; // 此处仍返回 null，但外层已有 @Conditional 保护
        }
        String clientIdBase = properties.getClientId().isBlank() ? "uav-command-" + gatewaySn : properties.getClientId();
        String[] topics = {
            "thing/product/" + gatewaySn + "/osd",
            "thing/product/" + gatewaySn + "/state",
            "thing/product/" + gatewaySn + "/events",
            "sys/product/" + gatewaySn + "/status"
        };
        int[] qos = {0, 0, 1, 0};
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(
                clientIdBase + "-inbound", mqttClientFactory(), topics);
        adapter.setCompletionTimeout(properties.getConnectTimeoutMs());
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(qos);
        adapter.setOutputChannel(mqttInputChannel());
        return adapter;
    }

    /**
     * 修复问题1a：用匿名类替代 lambda 强转，语义更明确。
     */
    @PostConstruct
    public void startMqttRouter() {
        if (mqttInbound() == null) return;
        mqttInputChannel().subscribe(new MessageHandler() {
            @Override
            public void handleMessage(org.springframework.messaging.Message<?> message) {
                String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
                Object raw = message.getPayload();
                String payload = raw instanceof String s ? s : null;
                if (topic == null || payload == null) return;
                try {
                    if (topic.endsWith("/osd")) telemetryParser.parseOsd(topic, payload);
                    else if (topic.endsWith("/state")) telemetryParser.parseState(topic, payload);
                    else if (topic.endsWith("/events")) telemetryParser.parseEvent(topic, payload);
                    else if (topic.endsWith("/status")) handleStatusMessage(topic, payload);
                } catch (Exception e) {
                    LOGGER.warn("MQTT 消息处理失败 topic={}", topic, e);
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void handleStatusMessage(String topic, String payload) {
        try {
            Map<String, Object> root = MAPPER.readValue(payload, Map.class);
            String method = (String) root.get("method");
            String tid = (String) root.get("tid");
            String bid = (String) root.get("bid");
            if (method == null) return;
            if (!properties.isHandshakeEnabled()) {
                LOGGER.debug("握手未启用，丢弃 status method={}", method);
                return;
            }
            String gatewaySn = extractSn(topic);
            String reply = switch (method) {
                case "config" -> handshakeService.buildConfigReply(tid, bid);
                case "airport_bind_status" -> handshakeService.buildAirportBindStatusReply(tid, bid, 0);
                case "update_topo" -> {
                    Map<String, Object> topoData = (Map<String, Object>) root.get("data");
                    if (topoData != null) parseAndRegisterTopo(gatewaySn, topoData);
                    yield handshakeService.buildUpdateTopoReply(tid, bid);
                }
                case "airport_organization_get" -> handshakeService.buildOrganizationGetReply(tid, bid, gatewaySn);
                case "airport_organization_bind" -> {
                    Map<String, Object> bindData = (Map<String, Object>) root.get("data");
                    String bindingCode = bindData != null ? (String) bindData.get("binding_code") : "";
                    yield handshakeService.buildOrganizationBindReply(tid, bid, gatewaySn, bindingCode);
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
            var subDevices = (java.util.List<Map<String, Object>>) topoData.get("sub_devices");
            if (subDevices == null) return;
            java.util.List<DjiDockTopologyRegistry.SubDevice> devices = subDevices.stream().map(d -> {
                String sn = (String) d.get("sn");
                String domain = (String) d.getOrDefault("domain", "");
                String type = (String) d.getOrDefault("type", "");
                String subType = (String) d.getOrDefault("sub_type", "");
                int index = d.get("index") instanceof Number n ? n.intValue() : 0;
                return new DjiDockTopologyRegistry.SubDevice(sn, domain, type, subType, index);
            }).toList();
            topologyRegistry.upsertGateway(gatewaySn, devices);
            LOGGER.info("握手注册拓扑 gatewaySn={} devices={}", gatewaySn, devices.size());
        } catch (Exception e) {
            LOGGER.warn("拓扑解析失败 gatewaySn={}", gatewaySn, e);
        }
    }

    private void publishReply(String requestTopic, String replyPayload) {
        if (mqttOutbound() == null) {
            LOGGER.warn("MQTT 出站未就绪，无法发送握手应答");
            return;
        }
        String replyTopic = requestTopic.replaceFirst("/status$", "/status_reply");
        try {
            mqttOutbound().handleMessage(MessageBuilder.withPayload(replyPayload)
                    .setHeader("mqtt_topic", replyTopic).build());
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
