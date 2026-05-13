package com.eventhub.modules.auth.service;

import java.util.List;

import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.eventhub.common.security.AuthenticatedSubject;
import com.eventhub.common.security.AuthenticatedSubjectLoader;
import com.eventhub.modules.auth.entity.UserEntity;
import com.eventhub.modules.auth.enums.UserStatus;
import com.eventhub.modules.auth.mapper.RoleMapper;
import com.eventhub.modules.auth.mapper.UserMapper;

import lombok.RequiredArgsConstructor;

/**
 * auth 模块提供的认证主体加载服务。
 * 该类是 auth 业务模型暴露给 infra.security 的窄适配层，负责查询最新用户状态和权限，但不处理 HTTP、Filter 或 JWT
 * 细节。
 */
@Service
@RequiredArgsConstructor
public class AuthenticatedSubjectService implements AuthenticatedSubjectLoader {

    private static final String ROLE_AUTHORITY_PREFIX = "ROLE_";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;

    /**
     * 根据认证主体标识加载最新用户状态和权限。
     * 即使 token 签名和有效期都合法，也必须重新查库确认用户没有被禁用，避免旧 token 绕过账号状态变更。
     * principalName 当前使用用户名填充；这是 auth 模块内部的映射选择，不要求公共安全契约绑定 users.username。
     *
     * @param subjectId token subject 中的主体标识
     * @return 当前认证主体
     */
    @Override
    public AuthenticatedSubject loadBySubjectId(Long subjectId) {
        UserEntity user = userMapper.findById(subjectId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + subjectId));
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new DisabledException("User is disabled: " + subjectId);
        }
        return new AuthenticatedSubject(
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
