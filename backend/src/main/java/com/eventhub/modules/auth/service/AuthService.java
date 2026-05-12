package com.eventhub.modules.auth.service;

import java.util.List;
import java.util.Locale;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventhub.common.security.AuthenticatedSubject;
import com.eventhub.infra.jwt.JwtTokenProvider;
import com.eventhub.infra.jwt.model.AccessTokenClaims;
import com.eventhub.modules.auth.dto.request.LoginRequest;
import com.eventhub.modules.auth.dto.request.RegisterRequest;
import com.eventhub.modules.auth.dto.request.UpdateUserStatusRequest;
import com.eventhub.modules.auth.entity.RoleEntity;
import com.eventhub.modules.auth.entity.UserEntity;
import com.eventhub.modules.auth.enums.UserStatus;
import com.eventhub.modules.auth.exception.AuthException;
import com.eventhub.modules.auth.mapper.RoleMapper;
import com.eventhub.modules.auth.mapper.UserMapper;
import com.eventhub.modules.auth.mapper.param.UserCreateParam;
import com.eventhub.modules.auth.vo.LoginResponse;
import com.eventhub.modules.auth.vo.UserInfo;

import lombok.RequiredArgsConstructor;

/**
 * 认证授权应用服务。
 * 负责注册、登录、当前用户资料查询和管理员用户管理；JWT 过滤器所需的主体加载已拆到 AuthenticatedSubjectService。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String ROLE_USER = "USER";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

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
            UserCreateParam createParam = UserCreateParam.enabledUser(
                    username,
                    email,
                    passwordEncoder.encode(request.password()));
            int affectedRows = userMapper.insert(createParam);
            if (affectedRows != 1 || createParam.getId() == null) {
                /*
                 * 正常情况下 MyBatis 会通过数据库 generated keys 把 users.id 回填到参数对象。
                 * 如果这里没有拿到 id，说明 Mapper XML、驱动或数据库主键回填配置出现了基础设施问题，
                 * 需要快速失败，避免继续写入 user_roles 造成难以排查的半完成注册流程。
                 */
                throw new IllegalStateException("Failed to retrieve generated user id");
            }

            Long userId = createParam.getId();
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
                jwtTokenProvider.generateAccessToken(new AccessTokenClaims(userInfo.id())),
                "Bearer",
                jwtTokenProvider.accessTokenTtlSeconds(),
                userInfo);
    }

    /**
     * 根据认证上下文返回当前用户。
     * 安全上下文只保存最小主体信息，接口响应需要回到 auth 模块查询最新用户资料并组装 UserInfo。
     *
     * @param authenticatedSubject SecurityContext 中的当前认证主体
     * @return 用户摘要
     */
    public UserInfo currentUser(AuthenticatedSubject authenticatedSubject) {
        return getUserInfo(authenticatedSubject.subjectId());
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
     * @param userId  用户主键
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
                roleMapper.findRoleCodesByUserId(user.id()));
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
