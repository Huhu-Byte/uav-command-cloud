package com.uavcommand.realtime;

import java.io.IOException;
import java.util.Properties;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.moquette.broker.Server;
import io.moquette.broker.config.IConfig;
import io.moquette.broker.config.MemoryConfig;

/**
 * 内嵌 Moquette MQTT Broker，仅在 UAV_DJI_MQTT_EMBEDDED_BROKER=true 时启动，供本机 DJI Dock 联调使用。
 *
 * <p>修复：原实现用 @Bean(initMethod="startServer") + 方法体内手动调 startServer，导致双重启动。
 * 改为 @Component + @PostConstruct/@PreDestroy，生命周期由 Spring 统一管理，startServer 只调一次。
 * 端口改从 DjiMqttProperties.brokerPort 读取，不再硬编码 1883。</p>
 */
@Component
@ConditionalOnProperty(name = "app.dji-mqtt.embedded-broker-provider", havingValue = "moquette")
public class EmbeddedMqttBroker {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddedMqttBroker.class);
    private static final int DEFAULT_PORT = 1884;

    private final DjiMqttProperties mqttProperties;
    private Server server;

    public EmbeddedMqttBroker(DjiMqttProperties mqttProperties) {
        this.mqttProperties = mqttProperties;
    }

    @PostConstruct
    public void start() throws IOException {
        int port = mqttProperties.getBrokerPort() > 0 ? mqttProperties.getBrokerPort() : DEFAULT_PORT;
        Properties props = new Properties();
        props.setProperty("port", String.valueOf(port));
        props.setProperty("host", "0.0.0.0");
       props.setProperty("allow_anonymous", "true");
        props.setProperty("allow_zero_byte_client_id", "true");
       IConfig config = new MemoryConfig(props);
        server = new Server();
        server.startServer(config);
        LOGGER.info("内嵌 MQTT Broker (Moquette) 已启动 port={}", port);
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            server.stopServer();
            LOGGER.info("内嵌 MQTT Broker (Moquette) 已停止");
        }
    }
}
