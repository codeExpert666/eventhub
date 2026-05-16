package com.eventhub.infra.security.principal;

import java.util.List;

/**
 * 当前登录用户在 Spring Security 上下文中的最小身份模型。
 *
 * <p>
 * 这个对象会作为 {@code Authentication.principal} 写入 {@code SecurityContext}。
 * 它只保存认证鉴权必需的信息，不承载完整用户资料、邮箱、头像、手机号、密码哈希或接口响应 VO。
 * 这样可以避免安全上下文膨胀，也便于后续从单体拆到网关、资源服务或独立认证服务时保持契约稳定。
 * </p>
 *
 * @param userId      当前用户 ID，当前阶段对应 users.id
 * @param username    当前用户名，用于日志、审计和接口边界读取，不作为最终授权依据
 * @param authorities Spring Security 可识别的权限字符串，例如 ROLE_USER、ROLE_ADMIN
 */
public record AuthenticatedPrincipal(
        Long userId,
        String username,
        List<String> authorities) {

    /**
     * record 紧凑构造器。
     *
     * <p>
     * authorities 会被复制成不可变列表，防止调用方在 principal 创建后继续修改原始集合，
     * 导致同一次请求中的授权判断出现不可预期变化。
     * </p>
     */
    public AuthenticatedPrincipal {
        authorities = authorities == null ? List.of() : List.copyOf(authorities);
    }
}
