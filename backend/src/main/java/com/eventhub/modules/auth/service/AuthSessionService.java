package com.eventhub.modules.auth.service;

import java.time.LocalDateTime;
import java.util.Optional;

import com.eventhub.modules.auth.entity.AuthSessionEntity;

/**
 * 认证会话应用服务接口。
 *
 * <p>
 * 当前登录成功路径会创建 ACTIVE 会话，refresh 路径会基于旧 token hash 和 version 轮换凭证。
 * logout 服务端吊销和设备会话列表可在该服务边界内继续扩展。
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
     * 根据 refresh token 明文查询会话。
     *
     * <p>
     * 调用方只提交明文 token，服务内部负责计算哈希后查询，避免上层服务直接处理落库哈希格式。
     * </p>
     *
     * @param refreshToken refresh token 明文
     * @return 命中的会话记录
     */
    Optional<AuthSessionEntity> findByRefreshToken(String refreshToken);

    /**
     * 轮换单个会话的 refresh token。
     *
     * <p>
     * 实现必须使用旧 refresh token hash、旧 version、会话状态和过期时间做条件更新，
     * 以保证同一个旧 token 并发提交时最多只有一个请求成功。
     * </p>
     *
     * @param session                 refresh 前读到的会话快照
     * @param oldRefreshToken         旧 refresh token 明文
     * @param newRefreshToken         新 refresh token 明文
     * @param refreshedAt             本次 refresh 时间
     * @param newRefreshExpiresAt     新 refresh token 过期时间
     * @return true 表示轮换成功
     */
    boolean rotateRefreshToken(
            AuthSessionEntity session,
            String oldRefreshToken,
            String newRefreshToken,
            LocalDateTime refreshedAt,
            LocalDateTime newRefreshExpiresAt);

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
