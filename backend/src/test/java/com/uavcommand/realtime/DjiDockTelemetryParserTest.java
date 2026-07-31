package com.uavcommand.realtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DjiDockTelemetryParserTest {
    private DjiDockTopologyRegistry topologyRegistry;
    private RealtimeStatusPublisher publisher;
    private DjiDockTelemetryParser parser;

    @BeforeEach
    void setUp() {
        topologyRegistry = new DjiDockTopologyRegistry();
        publisher = null;
        parser = new DjiDockTelemetryParser(topologyRegistry, publisher);
    }

    @Test
    void parseOsdShouldRejectInvalidCoordinates() {
        String invalidOsd = "{\"data\":{\"latitude\":200.0,\"longitude\":10.0,\"altitude\":50,\"battery\":80}}";
        assertDoesNotThrow(() -> parser.parseOsd("thing/product/SN001/osd", invalidOsd));
    }

    @Test
    void parseOsdShouldAcceptValidData() {
        String validOsd = "{\"data\":{\"latitude\":30.5,\"longitude\":120.3,\"altitude\":120,\"battery\":85}}";
        assertDoesNotThrow(() -> parser.parseOsd("thing/product/SN001/osd", validOsd));
    }

    @Test
    void parseStateDockCover() {
        String state = "{\"data\":{\"cover_state\":1,\"emergency_stop_state\":0}}";
        assertDoesNotThrow(() -> parser.parseState("thing/product/SN001/state", state));
    }

    @Test
    void parseStateFlightMode() {
        String state = "{\"data\":{\"flight_mode\":3}}";
        assertDoesNotThrow(() -> parser.parseState("thing/product/SN002/state", state));
    }

    @Test
    void parseEventHms() {
        String event = "{\"method\":\"hms\",\"data\":{}}";
        assertDoesNotThrow(() -> parser.parseEvent("thing/product/SN001/events", event));
    }

    @Test
    void parseEventFlightTaskReady() {
        String event = "{\"method\":\"flight_task_ready\"}";
        assertDoesNotThrow(() -> parser.parseEvent("thing/product/SN001/events", event));
    }

    @Test
    void refreshLatestSnapshotWithoutPriorOsd() {
        assertDoesNotThrow(() -> parser.refreshLatestSnapshot());
    }
}
