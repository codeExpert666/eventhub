package com.eventhub.modules.auth.service;

import com.eventhub.modules.auth.dto.request.LoginRequest;
import com.eventhub.modules.auth.dto.request.RegisterRequest;
import com.eventhub.modules.auth.dto.request.UpdateUserStatusRequest;
import com.eventhub.modules.auth.entity.RoleEntity;
import com.eventhub.modules.auth.entity.UserEntity;
import com.eventhub.modules.auth.enums.UserStatus;
import com.eventhub.modules.auth.exception.AuthException;
import com.eventhub.modules.auth.mapper.RoleMapper;
import com.eventhub.modules.auth.mapper.UserMapper;
import com.eventhub.modules.auth.security.AuthenticatedUser;
import com.eventhub.modules.auth.security.JwtTokenService;
import com.eventhub.modules.auth.vo.LoginResponse;
import com.eventhub.modules.auth.vo.UserInfo;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证授权应用服务。
 * 负责注册、登录、当前用户查询、管理员用户管理，以及 JWT 过滤器所需的用户状态校验。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String ROLE_USER = "USER";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    /**
     * 注册普通用户。
     * 用户创建和默认 USER 角色绑定放在同一事务内，避免出现账号已创建但没有角色的半完成状态。
     *
     * @param request 注册请求
     * @return 注册后的用户摘要
     */
    @Transactional
    public UserInfo register(RegisterRequest request) {
        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());

        if (userMapper.existsByUsername(username)) {
            throw AuthException.duplicateUsername();
        }
        if (userMapper.existsByEmail(email)) {
            throw AuthException.duplicateEmail();
        }

        try {
            Long userId = userMapper.insert(username, email, passwordEncoder.encode(request.password()));
            RoleEntity userRole = roleMapper.findByCode(ROLE_USER)
                    .orElseThrow(() -> new IllegalStateException("Default USER role is missing"));
            roleMapper.addRoleToUser(userId, userRole.id());
            return getUserInfo(userId);
        } catch (DuplicateKeyException exception) {
            /*
             * 并发注册时，两个请求可能同时通过 exists 预检查。
             * 数据库唯一约束是最终防线；这里把底层唯一键异常转换成稳定的业务提示。
             */
            throw AuthException.duplicateAccount();
        }
    }

    /**
     * 用户登录。
     * 登录失败时不区分账号不存在和密码错误，降低账号枚举风险。
     *
     * @param request 登录请求
     * @return 登录 token 与用户摘要
     */
    public LoginResponse login(LoginRequest request) {
        String usernameOrEmail = normalizeLoginIdentifier(request.usernameOrEmail());
        UserEntity user = userMapper.findByUsernameOrEmail(usernameOrEmail)
                .orElseThrow(AuthException::badCredentials);

        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw AuthException.badCredentials();
        }
        if (user.status() == UserStatus.DISABLED) {
            throw AuthException.disabledUser();
        }

        UserInfo userInfo = toUserInfo(user);
        return new LoginResponse(
                jwtTokenService.generateAccessToken(userInfo),
                "Bearer",
                jwtTokenService.accessTokenTtlSeconds(),
                userInfo
        );
    }

    /**
     * 根据认证上下文返回当前用户。
     *
     * @param authenticatedUser SecurityContext 中的当前用户
     * @return 用户摘要
     */
    public UserInfo currentUser(AuthenticatedUser authenticatedUser) {
        return authenticatedUser.toUserInfo();
    }

    /**
     * 查询全部用户。
     * 当前仅供管理员接口验证 RBAC 闭环，后续可继续补分页、筛选和排序。
     *
     * @return 用户摘要列表
     */
    public List<UserInfo> listUsers() {
        return userMapper.findAll()
                .stream()
                .map(this::toUserInfo)
                .toList();
    }

    /**
     * 管理员更新用户状态。
     *
     * @param userId 用户主键
     * @param request 状态更新请求
     * @return 更新后的用户摘要
     */
    @Transactional
    public UserInfo updateStatus(Long userId, UpdateUserStatusRequest request) {
        int affectedRows = userMapper.updateStatus(userId, request.status());
        if (affectedRows == 0) {
            throw AuthException.userNotFound();
        }
        return getUserInfo(userId);
    }

    /**
     * JWT 过滤器使用的用户加载方法。
     * 即使 token 签名和有效期都合法，也必须重新查库确认用户没有被禁用。
     *
     * @param userId token subject 中的用户 id
     * @return 当前认证用户
     */
    public AuthenticatedUser loadAuthenticatedUser(Long userId) {
        UserEntity user = userMapper.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
        if (user.status() == UserStatus.DISABLED) {
            throw new DisabledException("User is disabled: " + userId);
        }
        List<String> roles = roleMapper.findRoleCodesByUserId(user.id());
        return new AuthenticatedUser(user.id(), user.username(), user.email(), user.status(), roles);
    }

    private UserInfo getUserInfo(Long userId) {
        UserEntity user = userMapper.findById(userId)
                .orElseThrow(AuthException::userNotFound);
        return toUserInfo(user);
    }

    private UserInfo toUserInfo(UserEntity user) {
        return new UserInfo(
                user.id(),
                user.username(),
                user.email(),
                user.status(),
                roleMapper.findRoleCodesByUserId(user.id())
        );
    }

    private String normalizeUsername(String username) {
        return username.trim();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeLoginIdentifier(String usernameOrEmail) {
        String trimmed = usernameOrEmail.trim();
        if (trimmed.contains("@")) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        return trimmed;
    }
}
