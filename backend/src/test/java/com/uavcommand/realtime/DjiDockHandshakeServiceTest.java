package com.uavcommand.realtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DjiDockHandshakeServiceTest {
    private DjiDockHandshakeService service;

    @BeforeEach
    void setUp() {
        DjiCloudApiProperties cloudProps = new DjiCloudApiProperties();
        cloudProps.setClientId("test-app-id");
        cloudProps.setClientSecret("test-license");
        DjiMqttProperties mqttProps = new DjiMqttProperties();
        DjiDockTopologyRegistry registry = new DjiDockTopologyRegistry();
        service = new DjiDockHandshakeService(cloudProps, mqttProps, registry);
    }

    @Test
    void configReplyContainsAppId() {
        String reply = service.buildConfigReply("tid-1", "bid-1");
        assertTrue(reply.contains("test-app-id"));
        assertTrue(reply.contains("config"));
        assertTrue(reply.contains("ntp.aliyun.com"));
    }

    @Test
    void bindStatusReplyContainsBindStatus() {
        String reply = service.buildAirportBindStatusReply("tid-2", "bid-2", 0);
        assertTrue(reply.contains("airport_bind_status"));
        assertTrue(reply.contains("\"bind_status\":0"));
    }

    @Test
    void updateTopoReplyReturnsSuccess() {
        String reply = service.buildUpdateTopoReply("tid-3", "bid-3");
        assertTrue(reply.contains("update_topo"));
        assertTrue(reply.contains("\"result\":0"));
    }

    @Test
    void organizationGetNoMatchReturnsEmpty() {
        String reply = service.buildOrganizationGetReply("tid-4", "bid-4", "UNKNOWN_SN");
        assertTrue(reply.isEmpty());
    }

    @Test
    void organizationBindMismatchedCodeReturnsFailure() {
        String reply = service.buildOrganizationBindReply("tid-5", "bid-5", "SN001", "wrong-code");
        assertTrue(reply.contains("\"result\":1"));
    }
}
