package com.uavcommand.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DJI 上云 API 登录接口。
 *
 * <p>DJI Pilot 2 / 机场上云时调用此接口获取 access_token 和 MQTT 连接信息。
 * 路径遵循 DJI Cloud API 规范：/manage/api/v1/login 和 /manage/api/v1/token/refresh。</p>
 *
 * <p>当前使用简化的用户校验（环境变量配置的用户名/密码），
 * 生产环境可替换为数据库用户表或 LDAP。</p>
 */
@RestController
@RequestMapping("/manage/api/v1")
public class LoginController {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoginController.class);

    private final DjiTokenService tokenService;
    private final DjiCloudApiProperties cloudProperties;
    private final DjiMqttProperties mqttProperties;

    @Value("${app.dji-cloud.admin-username:adminPC}")
    private String adminUsername;

    @Value("${app.dji-cloud.admin-password:adminPC}")
    private String adminPassword;

    public LoginController(DjiTokenService tokenService, DjiCloudApiProperties cloudProperties, DjiMqttProperties mqttProperties) {
        this.tokenService = tokenService;
        this.cloudProperties = cloudProperties;
        this.mqttProperties = mqttProperties;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        if (request.username() == null || request.password() == null || request.flag() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "参数不完整");
        }
        if (!adminUsername.equals(request.username())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid username");
        }
        if (!adminPassword.equals(request.password())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid password");
        }

        DjiTokenService.TokenResult token = tokenService.generateToken(
                "1", request.username(), request.flag(), "1"
        );

        LOGGER.info("用户登录成功 username={} flag={}", request.username(), request.flag());

        return new LoginResponse(
                0,
                "success",
                new LoginData(
                        "1",
                        request.username(),
                        "1",
                        request.flag(),
                        mqttProperties.getUsername() != null ? mqttProperties.getUsername() : "",
                        mqttProperties.getPassword() != null ? mqttProperties.getPassword() : "",
                        token.accessToken(),
                        mqttProperties.getBrokerUrl() != null ? mqttProperties.getBrokerUrl() : ""
                )
        );
    }

    @PostMapping("/token/refresh")
    public LoginResponse refreshToken(
            @RequestHeader(name = "x-auth-token", required = false) String oldToken
    ) {
        DjiTokenService.TokenResult newToken = tokenService.refreshToken(oldToken);
        if (newToken == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "token 刷新失败");
        }

        return new LoginResponse(
                0,
                "success",
                new LoginData(
                        "1",
                        adminUsername,
                        "1",
                        1,
                        mqttProperties.getUsername() != null ? mqttProperties.getUsername() : "",
                        mqttProperties.getPassword() != null ? mqttProperties.getPassword() : "",
                        newToken.accessToken(),
                        mqttProperties.getBrokerUrl() != null ? mqttProperties.getBrokerUrl() : ""
                )
        );
    }

    /** DJI 登录请求。 */
    public record LoginRequest(
            String username,
            String password,
            int flag
    ) {}

    /** 统一响应体。 */
    public record LoginResponse(
            int code,
            String message,
            LoginData data
    ) {}

    /** 登录响应数据。 */
    public record LoginData(
            @JsonProperty("user_id") String userId,
            String username,
            @JsonProperty("workspace_id") String workspaceId,
            @JsonProperty("user_type") int userType,
            @JsonProperty("mqtt_username") String mqttUsername,
            @JsonProperty("mqtt_password") String mqttPassword,
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("mqtt_addr") String mqttAddr
    ) {}
}
