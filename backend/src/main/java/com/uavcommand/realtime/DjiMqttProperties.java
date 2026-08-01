package com.uavcommand.realtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** MQTT 连接参数。Broker 地址、账号、密码和设备编号仅允许通过环境变量注入，默认不连接。 */
@ConfigurationProperties(prefix = "app.dji-mqtt")
public class DjiMqttProperties {
    private boolean enabled;
    private String brokerUrl = "";
    private String clientId = "";
    private String username = "";
    private String password = "";
    private int connectTimeoutMs = 5000;
    private int keepAliveSec = 30;
    private boolean cleanSession = true;
    private boolean handshakeEnabled;
    private String gatewaySn = "";
    private String deviceBindingCode = "";
    /** 内嵌 Broker 监听端口，0 表示使用默认 1883。 */
    private int brokerPort = 0;
    private boolean embeddedBroker;
    private volatile boolean connected;
    private volatile String lastError = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBrokerUrl() { return brokerUrl; }
    public void setBrokerUrl(String brokerUrl) { this.brokerUrl = brokerUrl == null ? "" : brokerUrl.trim(); }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId == null ? "" : clientId.trim(); }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username == null ? "" : username.trim(); }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password == null ? "" : password.trim(); }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public int getKeepAliveSec() { return keepAliveSec; }
    public void setKeepAliveSec(int keepAliveSec) { this.keepAliveSec = keepAliveSec; }
    public boolean isCleanSession() { return cleanSession; }
    public void setCleanSession(boolean cleanSession) { this.cleanSession = cleanSession; }
    public boolean isHandshakeEnabled() { return handshakeEnabled; }
    public void setHandshakeEnabled(boolean handshakeEnabled) { this.handshakeEnabled = handshakeEnabled; }
    public String getGatewaySn() { return gatewaySn; }
    public void setGatewaySn(String gatewaySn) { this.gatewaySn = gatewaySn == null ? "" : gatewaySn.trim(); }
    public String getDeviceBindingCode() { return deviceBindingCode; }
    public void setDeviceBindingCode(String deviceBindingCode) { this.deviceBindingCode = deviceBindingCode == null ? "" : deviceBindingCode.trim(); }
    public int getBrokerPort() { return brokerPort; }
    public void setBrokerPort(int brokerPort) { this.brokerPort = brokerPort; }
    public boolean isEmbeddedBroker() { return embeddedBroker; }
    public void setEmbeddedBroker(boolean embeddedBroker) { this.embeddedBroker = embeddedBroker; }
    public boolean isConnected() { return connected; }
    public void setConnected(boolean connected) { this.connected = connected; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError == null ? "" : lastError.trim(); }
}
