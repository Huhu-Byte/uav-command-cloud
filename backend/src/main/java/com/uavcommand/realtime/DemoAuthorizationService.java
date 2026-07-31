package com.uavcommand.realtime;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DemoAuthorizationService {
    private static final Set<String> CONTROL_ROLES = Set.of("FLIGHT_OPERATOR", "ADMIN");
    private final boolean demoAuthEnabled;

    public DemoAuthorizationService(@Value("${app.security.demo-auth-enabled:true}") boolean demoAuthEnabled) {
        this.demoAuthEnabled = demoAuthEnabled;
    }

    public String requireControlOperator(String userName, String role) {
        if (!demoAuthEnabled) {
            throw new SecurityException("当前环境未接入真实登录，已禁止使用演示身份执行控制操作");
        }
        if (userName == null || userName.isBlank()) {
            throw new SecurityException("请先以飞行操作员或管理员身份登录");
        }
        if (!CONTROL_ROLES.contains(role)) {
            throw new SecurityException("当前身份没有执行控制操作的权限");
        }
        return userName.trim();
    }
}
