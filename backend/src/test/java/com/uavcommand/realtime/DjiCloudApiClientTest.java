package com.uavcommand.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class DjiCloudApiClientTest {
    @Test
    void staysDisabledWithoutServerConfiguration() {
        DjiCloudApiClient client = new DjiCloudApiClient(new DjiCloudApiProperties(), new DjiMqttProperties());

        DjiCloudApiClient.Readiness readiness = client.readiness();

        assertFalse(readiness.enabled());
        assertFalse(readiness.configured());
        assertTrue(readiness.message().contains("本机模拟器"));
    }

    @Test
    void reportsIncompleteConfigurationWithoutExposingSecrets() {
        DjiCloudApiProperties properties = configuredProperties();
        properties.setClientSecret("");
        DjiCloudApiClient client = new DjiCloudApiClient(properties, new DjiMqttProperties());

        DjiCloudApiClient.Readiness readiness = client.readiness();

        assertTrue(readiness.enabled());
        assertFalse(readiness.configured());
        assertFalse(readiness.toString().contains("client-123"));
        assertFalse(readiness.toString().contains("secret-456"));
    }

    @Test
    void rejectsUnsafeBaseUrlAndExcessiveRetries() {
        DjiCloudApiProperties properties = configuredProperties();
        properties.setBaseUrl("http://api.example.com");
        DjiCloudApiClient client = new DjiCloudApiClient(properties, new DjiMqttProperties());

        assertFalse(client.readiness().configured());

        properties.setBaseUrl("https://api.example.com");
        properties.setMaxRetries(6);
        assertFalse(client.readiness().configured());
    }

    @Test
    void retriesReadOnlyFailuresAndThenReturnsResult() {
        DjiCloudApiProperties properties = configuredProperties();
        properties.setMaxRetries(2);
        DjiCloudApiClient client = new DjiCloudApiClient(properties, new DjiMqttProperties());
        AtomicInteger attempts = new AtomicInteger();

        String result = client.executeReadOnly(DjiCloudApiClient.ReadOperation.DEVICE_STATUS, () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new ResourceAccessException("sensitive-url-or-token");
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void stopsAfterConfiguredRetryLimit() {
        DjiCloudApiProperties properties = configuredProperties();
        properties.setMaxRetries(1);
        DjiCloudApiClient client = new DjiCloudApiClient(properties, new DjiMqttProperties());
        AtomicInteger attempts = new AtomicInteger();

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> client.executeReadOnly(
                DjiCloudApiClient.ReadOperation.ALERTS,
                () -> {
                    attempts.incrementAndGet();
                    throw new ResourceAccessException("secret-456");
                }
        ));

        assertEquals(2, attempts.get());
        assertFalse(error.getMessage().contains("secret-456"));
    }

    @Test
    void onlyAllowsRelativeReadPaths() {
        DjiCloudApiClient client = new DjiCloudApiClient(configuredProperties(), new DjiMqttProperties());

        assertThrows(IllegalArgumentException.class, () -> client.getReadOnly(
                DjiCloudApiClient.ReadOperation.DEVICE_STATUS,
                "https://other.example.com/status",
                String.class
        ));
    }

    private DjiCloudApiProperties configuredProperties() {
        DjiCloudApiProperties properties = new DjiCloudApiProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("https://api.example.com");
        properties.setClientId("client-123");
        properties.setClientSecret("secret-456");
        properties.setConnectTimeoutMs(3000);
        properties.setReadTimeoutMs(5000);
        properties.setMaxRetries(2);
        return properties;
    }
}
