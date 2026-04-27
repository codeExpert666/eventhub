package com.eventhub.modules.auth.security;

import java.util.List;

/**
 * 从 JWT claims 中解析出的认证关键信息。
 * 该对象只表示 token 内容，不能直接等同于当前用户最终可访问系统；
 * 过滤器仍会根据 userId 查询数据库确认账号状态。
 *
 * @param userId token subject 中的用户 id
 * @param username token 签发时的用户名快照
 * @param roles token 签发时的角色快照
 */
public record JwtPrincipalClaims(
        Long userId,
        String username,
        List<String> roles
) {
    public JwtPrincipalClaims {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
