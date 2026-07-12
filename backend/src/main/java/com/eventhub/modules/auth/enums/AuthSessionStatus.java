package com.eventhub.modules.auth.enums;

/**
 * 服务端认证会话状态。
 *
 * <p>
 * 当前只保留最小状态集合：有效和已吊销。refresh token 是否过期由
 * {@code auth_sessions.refresh_expires_at} 派生，避免同一事实同时由状态和时间字段表达。
 * </p>
 */
public enum AuthSessionStatus {

    /**
     * 会话有效。
     * 后续 refresh token 校验必须同时满足状态为 ACTIVE 且未超过 refresh 过期时间。
     */
    ACTIVE,

    /**
     * 会话已吊销。
     * logout、管理员踢下线、用户禁用或检测到 refresh token 重放时，都可以把会话置为该状态。
     */
    REVOKED
}
