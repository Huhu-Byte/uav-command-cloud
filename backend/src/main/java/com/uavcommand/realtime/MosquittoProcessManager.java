package com.uavcommand.realtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mosquitto 进程管理器，仅在 {@code app.dji-mqtt.embedded-broker=true} 时启用。
 *
 * <p>替代 Moquette 内嵌方案，因为 DJI Dock 3 要求 MQTT 5.0，
 * 而 Moquette 0.17 不完全支持。Mosquitto 2.1.2 原生支持 MQTT 5.0。</p>
 *
 * <p>生命周期：</p>
 * <ul>
 *   <li>{@code @PostConstruct} — 在项目 tmp 目录生成 mosquitto.conf，启动 Mosquitto 子进程</li>
 *   <li>{@code @PreDestroy} — 销毁 Mosquitto 子进程，清理临时配置文件</li>
 * </ul>
 *
 * <p>配置文件写入 {@code java.io.tmpdir/uav-mosquitto/mosquitto.conf}，
 * 内容为：listener {port} 0.0.0.0 + allow_anonymous true。</p>
 */
@Component
@ConditionalOnProperty(name = "app.dji-mqtt.embedded-broker", havingValue = "true")
public class MosquittoProcessManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(MosquittoProcessManager.class);
    private static final String MOSQUITTO_EXE = "C:\\Program Files\\Mosquitto\\mosquitto.exe";
    private static final int DEFAULT_PORT = 1884;
    private static final long MAX_WAIT_SEC = 10;

    private final DjiMqttProperties mqttProperties;
    private Process mosquittoProcess;
    private Path configFile;

    public MosquittoProcessManager(DjiMqttProperties mqttProperties) {
        this.mqttProperties = mqttProperties;
    }

    @PostConstruct
    public void start() throws IOException, InterruptedException {
        int port = mqttProperties.getBrokerPort() > 0 ? mqttProperties.getBrokerPort() : DEFAULT_PORT;

        // 在系统临时目录下创建独立配置目录
        Path workDir = Path.of(System.getProperty("java.io.tmpdir"), "uav-mosquitto");
        Files.createDirectories(workDir);
        configFile = workDir.resolve("mosquitto.conf");

        String config = "listener " + port + " 0.0.0.0\nallow_anonymous true\n";
        Files.writeString(configFile, config);
        LOGGER.info("Mosquitto 配置文件已写入: {}", configFile);

        // 启动 Mosquitto 子进程
        ProcessBuilder builder = new ProcessBuilder(
                MOSQUITTO_EXE, "-c", configFile.toAbsolutePath().toString(), "-v");
       builder.directory(workDir.toFile());
       builder.redirectErrorStream(true);
        // stdout/stderr 合并后写入日志文件，方便排查 MQTT 连接问题
        builder.redirectOutput(workDir.resolve("mosquitto.log").toFile());
       mosquittoProcess = builder.start();

        // 等待进程退出或超时（启动成功时 Mosquitto 会持续运行）
        boolean exited = mosquittoProcess.waitFor(MAX_WAIT_SEC, TimeUnit.SECONDS);
        if (exited) {
            int code = mosquittoProcess.exitValue();
            mosquittoProcess = null;
            throw new IOException("Mosquitto 启动失败，退出码=" + code);
        }
        LOGGER.info("Mosquitto 子进程已启动 (PID={})，监听 0.0.0.0:{}", mosquittoProcess.pid(), port);
    }

    @PreDestroy
    public void stop() {
        if (mosquittoProcess != null && mosquittoProcess.isAlive()) {
            mosquittoProcess.destroy();
            try {
                mosquittoProcess.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                mosquittoProcess.destroyForcibly();
                Thread.currentThread().interrupt();
            }
            LOGGER.info("Mosquitto 子进程已停止 (PID={})", mosquittoProcess.pid());
        }
        if (configFile != null) {
            try {
                Files.deleteIfExists(configFile);
                LOGGER.debug("已删除临时配置文件: {}", configFile);
            } catch (IOException e) {
                LOGGER.warn("无法删除临时配置文件: {}", configFile);
            }
        }
    }
}
