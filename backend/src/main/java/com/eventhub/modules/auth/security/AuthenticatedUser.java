package com.eventhub.modules.auth.security;

import com.eventhub.modules.auth.enums.UserStatus;
import com.eventhub.modules.auth.vo.UserInfo;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * 写入 Spring Security 上下文的当前用户。
 * Controller 可以通过 {@code @AuthenticationPrincipal} 直接取得该对象，避免重复解析 token。
 *
 * @param id 用户主键
 * @param username 用户名
 * @param email 邮箱
 * @param status 用户状态
 * @param roles 角色编码集合
 */
public record AuthenticatedUser(
        Long id,
        String username,
        String email,
        UserStatus status,
        List<String> roles
) {
    public AuthenticatedUser {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    /**
     * 转换为对外响应使用的用户摘要。
     *
     * @return 用户摘要
     */
    public UserInfo toUserInfo() {
        return new UserInfo(id, username, email, status, roles);
    }

    /**
     * 转换为 Spring Security 识别的权限集合。
     * hasRole("ADMIN") 底层会匹配 ROLE_ADMIN，因此这里统一增加 ROLE_ 前缀。
     *
     * @return GrantedAuthority 集合
     */
    public Collection<GrantedAuthority> authorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .map(GrantedAuthority.class::cast)
                .toList();
    }
}
