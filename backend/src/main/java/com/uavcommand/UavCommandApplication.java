package com.uavcommand;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.uavcommand.realtime.DjiCloudApiProperties;
import com.uavcommand.realtime.DjiMqttProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({DjiCloudApiProperties.class, DjiMqttProperties.class})
public class UavCommandApplication {
    public static void main(String[] args) {
        SpringApplication.run(UavCommandApplication.class, args);
    }
}
