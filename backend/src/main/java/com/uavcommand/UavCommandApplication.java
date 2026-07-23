package com.uavcommand;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class UavCommandApplication {
    public static void main(String[] args) {
        SpringApplication.run(UavCommandApplication.class, args);
    }
}
