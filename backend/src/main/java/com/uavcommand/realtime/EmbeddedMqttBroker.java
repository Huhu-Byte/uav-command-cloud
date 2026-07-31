package com.uavcommand.realtime;

import java.io.IOException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.moquette.broker.Server;
import io.moquette.broker.config.IConfig;
import io.moquette.broker.config.MemoryConfig;

/** 内嵌 Moquette MQTT Broker，仅在 UAV_DJI_MQTT_EMBEDDED_BROKER=true 时启动，供本机 DJI Dock 联调使用。 */
@Configuration
@ConditionalOnProperty(name = "app.dji-mqtt.embedded-broker", havingValue = "true")
public class EmbeddedMqttBroker {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddedMqttBroker.class);

    @Bean(initMethod = "startServer", destroyMethod = "stopServer")
    public Server moquetteServer() throws IOException {
        Properties props = new Properties();
        props.setProperty("port", "1883");
        props.setProperty("host", "0.0.0.0");
        props.setProperty("allow_anonymous", "true");
        IConfig config = new MemoryConfig(props);
        Server server = new Server();
        server.startServer(config);
        LOGGER.info("内嵌 MQTT Broker (Moquette) 已启动 port=1883");
        return server;
    }
}
