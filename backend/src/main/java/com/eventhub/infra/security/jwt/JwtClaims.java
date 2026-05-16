package com.eventhub.infra.security.jwt;

/**
 * access token 中保存的最小身份声明。
 *
 * <p>
 * 当前 JWT 只保存稳定的主体标识。用户状态、角色和权限不写入 token 作为最终授权依据，
 * 而是在每次请求认证时通过 auth 模块加载最新数据，确保用户禁用和角色变更能及时生效。
 * </p>
 *
 * @param subjectId token subject 中的主体标识，当前对应 users.id
 */
public record JwtClaims(
        Long subjectId
) {
}
