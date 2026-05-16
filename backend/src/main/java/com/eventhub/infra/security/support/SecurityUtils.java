package com.eventhub.infra.security.support;

import java.util.List;
import java.util.Optional;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.eventhub.infra.security.principal.AuthenticatedPrincipal;

/**
 * Spring Security 上下文读取工具。
 *
 * <p>
 * 该工具只封装“如何从 SecurityContext 读取当前登录身份”，不承载业务判断。
 * 业务服务仍应优先通过方法参数显式接收当前用户 ID 或当前 principal，
 * 避免核心业务逻辑隐式依赖线程上下文。
 * </p>
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前认证主体。
     *
     * <p>
     * 返回空的三类典型情况：
     * <ul>
     *     <li>{@code authentication == null}：当前请求还没有建立任何认证对象，常见于未携带 token 的请求。</li>
     *     <li>{@code !authentication.isAuthenticated()}：认证对象存在，但还没有完成可信认证，不能当作已登录用户使用。</li>
     *     <li>{@code AnonymousAuthenticationToken}：Spring Security 为匿名访问创建的占位认证对象，
     *     它代表“游客身份”，不是本项目登录后的 {@link AuthenticatedPrincipal}。</li>
     * </ul>
     * </p>
     *
     * @return 当前认证主体；未登录、匿名用户或 principal 类型不匹配时返回空
     */
    public static Optional<AuthenticatedPrincipal> getCurrentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        /*
         * 这里统一过滤“不能代表真实登录用户”的认证状态：
         * 1. null 表示 SecurityContext 中没有认证信息；
         * 2. isAuthenticated=false 表示认证尚未成功；
         * 3. AnonymousAuthenticationToken 表示匿名用户占位，不是业务登录用户。
         */
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedPrincipal authenticatedPrincipal) {
            return Optional.of(authenticatedPrincipal);
        }
        return Optional.empty();
    }

    /**
     * 获取当前认证主体；如果当前请求没有认证主体，则快速失败。
     *
     * @return 当前认证主体
     */
    public static AuthenticatedPrincipal getRequiredCurrentPrincipal() {
        return getCurrentPrincipal()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal in security context"));
    }

    /**
     * 获取当前用户 ID。
     *
     * @return 当前用户 ID
     */
    public static Optional<Long> getCurrentUserId() {
        return getCurrentPrincipal().map(AuthenticatedPrincipal::userId);
    }

    /**
     * 获取当前用户名。
     *
     * @return 当前用户名
     */
    public static Optional<String> getCurrentUsername() {
        return getCurrentPrincipal().map(AuthenticatedPrincipal::username);
    }

    /**
     * 获取当前用户权限。
     *
     * @return 当前用户权限；未登录时返回空列表
     */
    public static List<String> getCurrentAuthorities() {
        return getCurrentPrincipal()
                .map(AuthenticatedPrincipal::authorities)
                .orElseGet(List::of);
    }
}
