package com.eventhub.modules.auth.service;

import java.time.LocalDateTime;
import java.util.Optional;

import com.eventhub.modules.auth.entity.AuthSessionEntity;

/**
 * 认证会话应用服务接口。
 *
 * <p>
 * 当前只定义后续 refresh/logout 需要的最小服务骨架，不接入现有登录响应和 JWT Filter。
 * 这样可以先稳定服务端会话持久化模型，再分阶段引入双 token 与服务端吊销流程。
 * </p>
 */
public interface AuthSessionService {

    /**
     * 创建 ACTIVE 认证会话。
     *
     * @param userId           用户主键
     * @param sessionId        会话标识
     * @param refreshToken     refresh token 明文，只用于本次哈希，不会落库
     * @param issuedAt         签发时间
     * @param refreshExpiresAt refresh token 过期时间
     * @return 已落库的会话实体
     */
    AuthSessionEntity createActiveSession(
            Long userId,
            String sessionId,
            String refreshToken,
            LocalDateTime issuedAt,
            LocalDateTime refreshExpiresAt);

    /**
     * 根据会话标识查询会话。
     *
     * @param sessionId 会话标识
     * @return 会话记录
     */
    Optional<AuthSessionEntity> findBySessionId(String sessionId);

    /**
     * 吊销单个 ACTIVE 会话。
     *
     * @param sessionId    会话标识
     * @param revokedAt    吊销时间
     * @param revokeReason 吊销原因
     * @return true 表示成功从 ACTIVE 变更为 REVOKED
     */
    boolean revokeSession(String sessionId, LocalDateTime revokedAt, String revokeReason);
}
