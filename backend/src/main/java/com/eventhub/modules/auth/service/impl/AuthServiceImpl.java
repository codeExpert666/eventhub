package com.eventhub.modules.auth.service.impl;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventhub.common.api.PageRequest;
import com.eventhub.common.api.PageResponse;
import com.eventhub.infra.security.principal.AuthenticatedPrincipal;
import com.eventhub.modules.auth.dto.request.AdminUserQueryRequest;
import com.eventhub.modules.auth.dto.request.LoginRequest;
import com.eventhub.modules.auth.dto.request.RegisterRequest;
import com.eventhub.modules.auth.dto.request.UpdateUserStatusRequest;
import com.eventhub.modules.auth.entity.RoleEntity;
import com.eventhub.modules.auth.entity.UserEntity;
import com.eventhub.modules.auth.enums.UserStatus;
import com.eventhub.modules.auth.exception.AuthException;
import com.eventhub.modules.auth.mapper.RoleMapper;
import com.eventhub.modules.auth.mapper.UserMapper;
import com.eventhub.modules.auth.mapper.param.UserQueryCriteria;
import com.eventhub.modules.auth.mapper.result.UserRoleCodeResult;
import com.eventhub.modules.auth.service.AuthService;
import com.eventhub.modules.auth.service.TokenService;
import com.eventhub.modules.auth.vo.LoginResponse;
import com.eventhub.modules.auth.vo.UserInfo;

import lombok.RequiredArgsConstructor;

/**
 * 认证授权应用服务实现。
 *
 * <p>
 * 该类负责注册、登录、登出、当前用户资料查询和管理员用户管理。
 * JWT 如何生成由 {@link TokenService} 决定，当前认证主体如何加载由
 * {@code modules.auth.security.AuthenticatedPrincipalService} 决定，避免所有安全逻辑集中在一个服务中。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String ROLE_USER = "USER";
    private static final String TOKEN_TYPE_BEARER = "Bearer";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    /**
     * 注册普通用户。
     * 用户创建和默认 USER 角色绑定放在同一事务内，避免出现账号已创建但没有角色的半完成状态。
     *
     * <p>
     * 注册失败按“业务可预期失败”和“系统不变量失败”分层处理：
     * 用户名、邮箱重复以及并发唯一键冲突会转换成稳定的 409 业务响应；
     * 主键回填失败、默认角色缺失或角色绑定失败属于服务端不变量破坏，会抛出运行时异常并触发事务回滚。
     * </p>
     *
     * @param request 注册请求
     * @return 注册后的用户摘要
     */
    @Override
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
            UserEntity user = UserEntity.enabledUser(
                    username,
                    email,
                    passwordEncoder.encode(request.password())
            );
            int affectedRows = userMapper.insert(user);
            if (affectedRows != 1 || user.getId() == null) {
                /*
                 * MyBatis 正常情况下会把数据库生成的 users.id 回填到实体。
                 * 如果这里没有拿到 id，继续绑定角色会制造半完成账号，因此快速失败并回滚事务。
                 */
                throw new IllegalStateException("Failed to retrieve generated user id");
            }

            Long userId = user.getId();
            RoleEntity userRole = roleMapper.findByCode(ROLE_USER)
                    .orElseThrow(() -> new IllegalStateException("Default USER role is missing"));
            int roleBindingRows = roleMapper.addRoleToUser(userId, userRole.getId());
            if (roleBindingRows != 1) {
                throw new IllegalStateException("Failed to bind default USER role");
            }
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
    @Override
    public LoginResponse login(LoginRequest request) {
        String usernameOrEmail = normalizeLoginIdentifier(request.usernameOrEmail());
        UserEntity user = userMapper.findByUsernameOrEmail(usernameOrEmail)
                .orElseThrow(AuthException::badCredentials);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw AuthException.badCredentials();
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            throw AuthException.disabledUser();
        }

        UserInfo userInfo = toUserInfo(user);
        return new LoginResponse(
                tokenService.issueAccessToken(userInfo),
                TOKEN_TYPE_BEARER,
                tokenService.accessTokenTtlSeconds(),
                userInfo
        );
    }

    /**
     * 用户登出。
     *
     * <p>
     * 当前 access token 不落库，服务端无法主动吊销已签发 token。
     * 因此本方法保持无状态 no-op，只保留业务语义入口：
     * 调用方必须先通过认证，客户端收到成功响应后删除本地 token。
     * 后续如果引入 token 黑名单、登录会话表或审计日志，可以从这里扩展。
     * </p>
     *
     * @param principal 当前认证主体
     */
    @Override
    public void logout(AuthenticatedPrincipal principal) {
        Objects.requireNonNull(principal, "principal must not be null");
    }

    /**
     * 根据认证上下文返回当前用户。
     * 安全上下文只保存最小主体信息，接口响应需要回到 auth 模块查询最新用户资料并组装 UserInfo。
     *
     * @param principal SecurityContext 中的当前认证主体
     * @return 用户摘要
     */
    @Override
    public UserInfo currentUser(AuthenticatedPrincipal principal) {
        return getUserInfo(principal.userId());
    }

    /**
     * 分页查询用户。
     * 当前支持分页、账号字段筛选、状态筛选和时间范围筛选。
     * 列表路径会按当前页用户 ID 批量查询角色，避免每个用户单独查一次角色造成 N+1 查询。
     *
     * @param request 分页与筛选查询参数
     * @return 用户摘要分页结果
     */
    @Override
    public PageResponse<UserInfo> listUsers(AdminUserQueryRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        PageRequest pageRequest = request.toPageRequest();
        UserQueryCriteria criteria = request.toCriteria();
        long total = userMapper.countByCriteria(criteria);
        if (total == 0) {
            return PageResponse.of(List.of(), pageRequest, total);
        }

        List<UserEntity> userEntities = userMapper.findPage(criteria, pageRequest.size(), pageRequest.offset());
        Map<Long, List<String>> rolesByUserId = findRolesByUserIds(userEntities);
        List<UserInfo> users = userEntities
                .stream()
                .map(user -> toUserInfo(user, rolesByUserId.getOrDefault(user.getId(), List.of())))
                .toList();
        return PageResponse.of(users, pageRequest, total);
    }

    /**
     * 管理员更新用户状态。
     *
     * @param userId  用户主键
     * @param request 状态更新请求
     * @return 更新后的用户摘要
     */
    @Override
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
        return toUserInfo(user, roleMapper.findRoleCodesByUserId(user.getId()));
    }

    private UserInfo toUserInfo(UserEntity user, List<String> roles) {
        return new UserInfo(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getStatus(),
                roles
        );
    }

    private Map<Long, List<String>> findRolesByUserIds(List<UserEntity> users) {
        if (users.isEmpty()) {
            return Map.of();
        }
        List<Long> userIds = users.stream()
                .map(UserEntity::getId)
                .toList();
        return roleMapper.findRoleCodesByUserIds(userIds)
                .stream()
                .collect(Collectors.groupingBy(
                        UserRoleCodeResult::getUserId,
                        Collectors.mapping(UserRoleCodeResult::getRoleCode, Collectors.toList())
                ));
    }

    private String normalizeUsername(String username) {
        return username.trim();
    }

    private String normalizeEmail(String email) {
        /*
         * Locale.ROOT 使用语言无关的根区域设置进行小写转换。
         * 邮箱归一化属于系统规则，不应受服务器默认语言环境影响。
         */
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
