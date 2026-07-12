package com.eventhub.infra.security.jwt;

import java.util.Objects;

/**
 * access token 中保存的最小认证声明。
 *
 * <p>
 * 当前 JWT 只保存稳定的主体标识、token 唯一标识和服务端会话标识。
 * 用户状态、角色和权限不写入 token 作为最终授权依据，而是在每次请求认证时通过 auth 模块加载最新数据，
 * 确保用户禁用和角色变更能及时生效。
 * </p>
 *
 * @param subjectId token subject 中的主体标识，当前对应 users.id
 * @param tokenId   JWT ID，对应标准 claim jti
 * @param sessionId 服务端认证会话标识，对应自定义 claim sid
 * @param tokenType token 类型，对应自定义 claim typ，access token 固定为 access
 */
public record JwtClaims(
        Long subjectId,
        String tokenId,
        String sessionId,
        String tokenType) {

    public static final String ACCESS_TOKEN_TYPE = "access";

    public JwtClaims {
        Objects.requireNonNull(subjectId, "subjectId must not be null");
        if (tokenId == null || tokenId.isBlank()) {
            throw new IllegalArgumentException("tokenId must not be blank");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        if (tokenType == null || tokenType.isBlank()) {
            throw new IllegalArgumentException("tokenType must not be blank");
        }
    }
}
