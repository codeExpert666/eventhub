package com.eventhub.modules.auth.security;

import java.util.List;

import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.eventhub.infra.security.principal.AuthenticatedPrincipal;
import com.eventhub.modules.auth.entity.UserEntity;
import com.eventhub.modules.auth.enums.UserStatus;
import com.eventhub.modules.auth.mapper.RoleMapper;
import com.eventhub.modules.auth.mapper.UserMapper;

import lombok.RequiredArgsConstructor;

/**
 * auth 模块提供的认证主体加载服务。
 *
 * <p>
 * 该类位于 auth 模块和 Spring Security 的交界处，只负责读取用户认证信息：
 * 查询用户是否存在、状态是否可用、角色权限是什么，并构造安全上下文需要的
 * {@link AuthenticatedPrincipal}。登录流程、注册流程、token 签发和登出语义不放在这里。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class AuthenticatedPrincipalService {

    private static final String ROLE_AUTHORITY_PREFIX = "ROLE_";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;

    /**
     * 根据用户 ID 加载最新认证主体。
     *
     * <p>
     * 即使 JWT 签名和有效期都合法，也必须重新查库确认用户仍存在且没有被禁用，
     * 避免旧 token 绕过账号状态变更。
     * </p>
     *
     * @param userId 用户 ID
     * @return 当前认证主体
     */
    public AuthenticatedPrincipal loadByUserId(Long userId) {
        UserEntity user = userMapper.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
        return toAuthenticatedPrincipal(user);
    }

    private AuthenticatedPrincipal toAuthenticatedPrincipal(UserEntity user) {
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new DisabledException("User is disabled: " + user.getId());
        }
        return new AuthenticatedPrincipal(
                user.getId(),
                user.getUsername(),
                toAuthorities(roleMapper.findRoleCodesByUserId(user.getId())));
    }

    private List<String> toAuthorities(List<String> roleCodes) {
        return roleCodes.stream()
                .map(this::toRoleAuthority)
                .toList();
    }

    private String toRoleAuthority(String roleCode) {
        if (roleCode.startsWith(ROLE_AUTHORITY_PREFIX)) {
            return roleCode;
        }
        return ROLE_AUTHORITY_PREFIX + roleCode;
    }
}
