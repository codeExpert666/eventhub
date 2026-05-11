package com.eventhub.common.security;

import java.util.List;

/**
 * 当前认证主体的跨模块安全契约。
 *
 * <p>
 * 可以把它理解成“已经通过认证的当前用户摘要”。
 * JwtAuthenticationFilter 解析 JWT 后，会加载用户最新状态和权限，
 * 然后把这个对象放进 Spring Security 的 Authentication.principal 中。
 * 后续 Controller 可以通过 {@code @AuthenticationPrincipal AuthenticatedSubject authenticatedSubject}
 * 直接拿到当前登录主体。
 * </p>
 *
 * <p>
 * 该对象只保留完成认证授权所需的最小信息，
 * 避免把 auth 模块的用户实体、响应 VO 或状态枚举泄漏到基础设施层。
 * 这样 infra.security 只依赖一个稳定的小契约，不需要知道 users 表或 UserEntity 的完整结构。
 * </p>
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
    /**
     * record 的紧凑构造器。
     *
     * <p>
     * Java record 会自动生成全参构造器、getter 风格访问方法、equals、hashCode 和 toString。
     * 这里显式写出紧凑构造器，是为了在对象创建时统一清洗 authorities 字段。
     * </p>
     *
     * <p>
     * 如果调用方传入 null，就转换成空列表，避免后续授权转换时出现 NullPointerException。
     * 如果调用方传入普通可变 List，就用 {@link List#copyOf(java.util.Collection)} 创建不可变副本，
     * 防止 AuthenticatedSubject 创建完成后，外部继续修改原始权限列表，导致 SecurityContext 中的权限被意外改变。
     * </p>
     */
    public AuthenticatedSubject {
        authorities = authorities == null ? List.of() : List.copyOf(authorities);
    }
}
