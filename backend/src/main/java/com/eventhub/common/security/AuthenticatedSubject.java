package com.eventhub.common.security;

import java.util.List;

/**
 * 当前认证主体的跨模块安全契约。
 * 该对象只保留完成认证授权所需的最小信息，避免把 auth 模块的用户实体、响应 VO 或状态枚举泄漏到基础设施层。
 *
 * @param subjectId 认证主体标识，当前阶段对应 users.id，使用 subject 命名是为了避免和具体用户表强绑定
 * @param principalName 认证主体的可读名称，当前由 users.username 提供，但跨模块契约不承诺它永远等于用户表字段
 * @param authorities Spring Security 可识别的权限字符串，例如 ROLE_USER、ROLE_ADMIN
 */
public record AuthenticatedSubject(
        Long subjectId,
        String principalName,
        List<String> authorities
) {
    public AuthenticatedSubject {
        authorities = authorities == null ? List.of() : List.copyOf(authorities);
    }
}
