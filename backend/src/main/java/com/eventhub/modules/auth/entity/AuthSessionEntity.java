package com.eventhub.modules.auth.entity;

import java.time.LocalDateTime;

import com.eventhub.modules.auth.enums.AuthSessionStatus;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * auth_sessions 表对应的持久化对象。
 *
 * <p>
 * 该对象只表达数据库行数据，不直接签发 token，也不参与 HTTP 响应序列化。
 * refresh token 明文只允许短暂存在于调用栈中，落库字段必须使用 {@code refreshTokenHash}。
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
public class AuthSessionEntity {

    /**
     * auth_sessions.id，自增主键。
     */
    private Long id;

    /**
     * auth_sessions.session_id，对外稳定会话标识。
     */
    private String sessionId;

    /**
     * auth_sessions.user_id，会话所属用户。
     */
    private Long userId;

    /**
     * auth_sessions.refresh_token_hash，refresh token 哈希值，不保存明文。
     */
    private String refreshTokenHash;

    /**
     * auth_sessions.status，会话状态。
     */
    private AuthSessionStatus status;

    /**
     * auth_sessions.issued_at，会话签发时间。
     */
    private LocalDateTime issuedAt;

    /**
     * auth_sessions.refresh_expires_at，refresh token 过期时间。
     */
    private LocalDateTime refreshExpiresAt;

    /**
     * auth_sessions.last_refreshed_at，最近一次 refresh 成功时间。
     */
    private LocalDateTime lastRefreshedAt;

    /**
     * auth_sessions.last_seen_at，最近一次观察到该会话活动的时间。
     */
    private LocalDateTime lastSeenAt;

    /**
     * auth_sessions.revoked_at，会话吊销时间。
     */
    private LocalDateTime revokedAt;

    /**
     * auth_sessions.revoke_reason，会话吊销原因。
     */
    private String revokeReason;

    /**
     * auth_sessions.client_ip_hash，客户端 IP 哈希。
     */
    private String clientIpHash;

    /**
     * auth_sessions.user_agent_hash，User-Agent 哈希。
     */
    private String userAgentHash;

    /**
     * auth_sessions.user_agent_summary，设备展示摘要。
     */
    private String userAgentSummary;

    /**
     * auth_sessions.version，乐观锁版本。
     */
    private Integer version;

    /**
     * auth_sessions.created_at，创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * auth_sessions.updated_at，更新时间。
     */
    private LocalDateTime updatedAt;

    /**
     * 构造一条新建的 ACTIVE 会话。
     *
     * @param sessionId        会话标识
     * @param userId           用户主键
     * @param refreshTokenHash refresh token 哈希
     * @param issuedAt         签发时间
     * @param refreshExpiresAt refresh token 过期时间
     * @return 可交给 MyBatis 插入的会话实体
     */
    public static AuthSessionEntity activeSession(
            String sessionId,
            Long userId,
            String refreshTokenHash,
            LocalDateTime issuedAt,
            LocalDateTime refreshExpiresAt) {
        AuthSessionEntity session = new AuthSessionEntity();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setRefreshTokenHash(refreshTokenHash);
        session.setStatus(AuthSessionStatus.ACTIVE);
        session.setIssuedAt(issuedAt);
        session.setRefreshExpiresAt(refreshExpiresAt);
        session.setLastSeenAt(issuedAt);
        session.setVersion(0);
        return session;
    }
}
