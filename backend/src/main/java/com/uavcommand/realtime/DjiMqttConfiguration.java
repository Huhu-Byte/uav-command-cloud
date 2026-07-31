package com.uavcommand.realtime;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;

@Configuration
public class DjiMqttConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(DjiMqttConfiguration.class);
    private final DjiMqttProperties properties;
    private final DjiDockTelemetryParser telemetryParser;

    public DjiMqttConfiguration(DjiMqttProperties properties, DjiDockTelemetryParser telemetryParser) {
        this.properties = properties;
        this.telemetryParser = telemetryParser;
    }

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{properties.getBrokerUrl()});
        if (!properties.getUsername().isBlank()) {
            options.setUserName(properties.getUsername());
        }
        if (!properties.getPassword().isBlank()) {
            options.setPassword(properties.getPassword().toCharArray());
        }
        options.setConnectionTimeout(properties.getConnectTimeoutMs() / 1000);
        options.setKeepAliveInterval(properties.getKeepAliveSec());
        options.setCleanSession(properties.isCleanSession());
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter mqttInbound() {
        if (!properties.isEnabled()) {
            LOGGER.info("DJI MQTT 未启用，不创建订阅适配器");
            return null;
        }
        String gatewaySn = properties.getGatewaySn();
        if (gatewaySn.isBlank()) {
            LOGGER.warn("DJI MQTT 已启用但未配置网关 SN，不创建订阅");
            return null;
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
                clientIdBase + "-inbound",
                mqttClientFactory(),
                topics);
        adapter.setCompletionTimeout(properties.getConnectTimeoutMs());
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(qos);
        adapter.setOutputChannel(mqttInputChannel());
        return adapter;
    }

    @Bean
    public Object mqttMessageRouter() {
        if (mqttInbound() == null) return null;
        mqttInputChannel().subscribe(message -> {
            String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
            String payload = (String) message.getPayload();
            if (topic == null || payload == null) return;
            try {
                if (topic.endsWith("/osd")) {
                    telemetryParser.parseOsd(topic, payload);
                } else if (topic.endsWith("/state")) {
                    telemetryParser.parseState(topic, payload);
                } else if (topic.endsWith("/events")) {
                    telemetryParser.parseEvent(topic, payload);
                } else if (topic.endsWith("/status")) {
                    telemetryParser.parseStatus(topic, payload);
                }
            } catch (Exception e) {
                LOGGER.warn("MQTT 消息处理失败 topic={} error={}", topic, e.getMessage());
            }
        });
        return "mqtt-router-configured";
    }
}
