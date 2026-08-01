package com.uavcommand.realtime;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

/**
 * DJI 上云 API 的 JWT token 服务。
 *
 * <p>本服务负责生成、验证和刷新 JWT access_token。当 {@code app.dji-cloud.enabled=false} 时，
 * 生成模拟 token 供本地开发使用；当 enabled=true 时使用配置的密钥签名。</p>
 *
 * <p>架构说明：DJI 上云 API 中，第三方云平台（即本系统）自行签发 JWT token。
 * DJI Pilot 2 / 机场通过登录接口获取 token，后续请求携带 x-auth-token 头。</p>
 */
@Service
public class DjiTokenService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DjiTokenService.class);
    private static final String ISSUER = "uav-command-cloud";
    private static final String SUBJECT = "CloudApiSample";
    /** token 提前刷新时间（秒），在到期前 5 分钟自动刷新。 */
    private static final int REFRESH_AHEAD_SEC = 300;

    private final DjiCloudApiProperties properties;
    /** 已签发 token 的缓存：token → 过期时间戳（秒），用于快速校验和清理。 */
    private final Map<String, Long> tokenCache = new ConcurrentHashMap<>();

    public DjiTokenService(DjiCloudApiProperties properties) {
        this.properties = properties;
    }

    /**
     * 生成 JWT access_token。
     *
     * @param userId    用户标识
     * @param username  用户名
     * @param userType  用户类型（1=Web 端，2=Pilot 端）
     * @param workspaceId 工作空间 ID
     * @return 包含 token 和过期时间的记录
     */
    public TokenResult generateToken(String userId, String username, int userType, String workspaceId) {
        long nowSec = Instant.now().getEpochSecond();
        long expireAtSec = nowSec + properties.getTokenExpireSec();

        String token = JWT.create()
                .withIssuer(ISSUER)
                .withSubject(SUBJECT)
                .withClaim("id", userId)
                .withClaim("username", username)
                .withClaim("user_type", userType)
                .withClaim("workspace_id", workspaceId)
                .withIssuedAt(Date.from(Instant.ofEpochSecond(nowSec)))
                .withExpiresAt(Date.from(Instant.ofEpochSecond(expireAtSec)))
                .sign(Algorithm.HMAC256(properties.getTokenSecret()));

        tokenCache.put(token, expireAtSec);
        LOGGER.info("签发 token userId={} userType={} workspace={} 有效期={}秒", userId, userType, workspaceId, properties.getTokenExpireSec());
        return new TokenResult(token, expireAtSec, properties.getTokenExpireSec());
    }

    /**
     * 验证 token 并返回 claims。返回 null 表示无效或过期。
     */
    public DecodedJWT validateToken(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            DecodedJWT jwt = JWT.require(Algorithm.HMAC256(properties.getTokenSecret()))
                    .withIssuer(ISSUER)
                    .build()
                    .verify(token);
            // 检查缓存中是否仍存在（已注销的 token 会被移除）
            if (!tokenCache.containsKey(token)) {
                return null;
            }
            return jwt;
        } catch (Exception e) {
            LOGGER.debug("token 验证失败：{}", e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 刷新 token。即使旧 token 已过期，只要签名有效就能解析 claims 并签发新 token。
     *
     * @param oldToken 旧 token
     * @return 新的 token 结果，或 null 表示旧 token 签名无效
     */
    public TokenResult refreshToken(String oldToken) {
        if (oldToken == null || oldToken.isBlank()) return null;
        try {
            // 先验证签名（允许过期）
            DecodedJWT jwt = JWT.decode(oldToken);
            // 验证签名有效性（不检查过期）
            JWT.require(Algorithm.HMAC256(properties.getTokenSecret()))
                    .withIssuer(ISSUER)
                    .build()
                    .verify(oldToken);

            String userId = jwt.getClaim("id").asString();
            String username = jwt.getClaim("username").asString();
            int userType = jwt.getClaim("user_type").asInt();
            String workspaceId = jwt.getClaim("workspace_id").asString();

            // 注销旧 token
            tokenCache.remove(oldToken);

            return generateToken(userId, username, userType, workspaceId);
        } catch (Exception e) {
            LOGGER.warn("token 刷新失败：{}", e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 注销 token（登出时调用）。
     */
    public void revokeToken(String token) {
        if (token != null) {
            tokenCache.remove(token);
            LOGGER.info("注销 token");
        }
    }

    /**
     * 检查 token 是否需要刷新（距过期不足 5 分钟）。
     */
    public boolean needsRefresh(String token) {
        Long expireAt = tokenCache.get(token);
        if (expireAt == null) return true;
        long nowSec = Instant.now().getEpochSecond();
        return (expireAt - nowSec) < REFRESH_AHEAD_SEC;
    }

    /**
     * 获取当前有效的 token 数量（监控用）。
     */
    public int activeTokenCount() {
        long nowSec = Instant.now().getEpochSecond();
        tokenCache.entrySet().removeIf(e -> e.getValue() < nowSec);
        return tokenCache.size();
    }

    /** token 生成结果。 */
    public record TokenResult(String accessToken, long expireAtSec, int expiresInSec) {}
}
