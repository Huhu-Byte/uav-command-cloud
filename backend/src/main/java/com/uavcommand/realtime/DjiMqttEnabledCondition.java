package com.uavcommand.realtime;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 条件：仅当 app.dji-mqtt.enabled=true 且 gateway-sn 非空时才创建 MQTT 相关 Bean。
 */
public class DjiMqttEnabledCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String enabled = context.getEnvironment().getProperty("app.dji-mqtt.enabled", "false");
        String gatewaySn = context.getEnvironment().getProperty("app.dji-mqtt.gateway-sn", "");

        boolean isEnabled = "true".equalsIgnoreCase(enabled);
        boolean hasGatewaySn = gatewaySn != null && !gatewaySn.isBlank();

        return isEnabled && hasGatewaySn;
    }
}
