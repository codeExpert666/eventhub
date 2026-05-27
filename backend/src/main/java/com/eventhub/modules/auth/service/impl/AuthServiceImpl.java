package com.eventhub.modules.auth.service.impl;

import com.eventhub.common.api.PageRequest;
import com.eventhub.common.api.PageResponse;
import com.eventhub.infra.security.principal.AuthenticatedPrincipal;
import com.eventhub.modules.auth.dto.request.*;
import com.eventhub.modules.auth.entity.AuthSessionEntity;
import com.eventhub.modules.auth.entity.RoleEntity;
import com.eventhub.modules.auth.entity.UserEntity;
import com.eventhub.modules.auth.enums.AuthSessionStatus;
import com.eventhub.modules.auth.enums.UserStatus;
import com.eventhub.modules.auth.exception.AuthException;
import com.eventhub.modules.auth.mapper.RoleMapper;
import com.eventhub.modules.auth.mapper.UserMapper;
import com.eventhub.modules.auth.mapper.param.UserQueryCriteria;
import com.eventhub.modules.auth.mapper.result.UserRoleCodeResult;
import com.eventhub.modules.auth.service.AuthService;
import com.eventhub.modules.auth.service.AuthSessionService;
import com.eventhub.modules.auth.service.RefreshTokenParser;
import com.eventhub.modules.auth.service.TokenService;
import com.eventhub.modules.auth.vo.LoginResponse;
import com.eventhub.modules.auth.vo.TokenPairResponse;
import com.eventhub.modules.auth.vo.UserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
    private static final String AUTHORIZATION_SCHEME_BEARER = "Bearer";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuthSessionService authSessionService;
    private final RefreshTokenParser refreshTokenParser;

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
     * <p>
     * 登录成功后创建服务端认证会话，并将 access token 的 sid claim 与该会话关联。
     * refresh token 明文只在本次响应中返回；落库时由 AuthSessionService 转换为哈希值。
     * </p>
     *
     * @param request 登录请求
     * @return 登录 token 与用户摘要
     */
    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        /*
         * 先统一登录标识格式，再按“用户名或邮箱”查询用户。
         * 查询不到用户时抛出 badCredentials，和后续密码错误保持同一种失败语义，
         * 避免调用方通过错误响应差异判断某个用户名或邮箱是否存在。
         */
        String usernameOrEmail = normalizeLoginIdentifier(request.usernameOrEmail());
        UserEntity user = userMapper.findByUsernameOrEmail(usernameOrEmail)
                .orElseThrow(AuthException::badCredentials);

        /*
         * 密码校验必须使用 PasswordEncoder.matches，由具体编码器处理哈希算法和盐值。
         * 状态校验放在密码校验之后，既保证禁用账号无法登录，也避免在密码错误时额外泄露账号状态。
         */
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw AuthException.badCredentials();
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            throw AuthException.disabledUser();
        }

        /*
         * 登录响应和 token 只需要用户摘要信息。
         * 这里先转换为 UserInfo，避免 passwordHash 等持久化内部字段越过 service 边界。
         */
        UserInfo userInfo = toUserInfo(user);

        /*
         * 每次登录创建独立服务端会话：
         * - sessionId 会写入 access token 的 sid claim，用于后续定位服务端会话；
         * - refresh token 明文只返回给客户端一次，落库细节由 AuthSessionService 负责处理；
         * - refresh token 的过期时间和签发时间使用同一个 issuedAt 基准，避免时间计算出现细微偏差。
         */
        String sessionId = newSessionId();
        String refreshToken = tokenService.issueRefreshToken();
        LocalDateTime issuedAt = LocalDateTime.now();
        long refreshExpiresIn = tokenService.refreshTokenTtlSeconds();
        LocalDateTime refreshExpiresAt = issuedAt.plusSeconds(refreshExpiresIn);

        /*
         * 会话创建与登录响应处于同一事务边界内。
         * 如果服务端会话没有成功落库，就不应该把已经关联该 sessionId 的 token 返回给客户端。
         */
        authSessionService.createActiveSession(
                userInfo.id(),
                sessionId,
                refreshToken,
                issuedAt,
                refreshExpiresAt
        );

        /*
         * 最后再签发 access token，确保 token 中的 sid 已经对应一条可查询的服务端会话。
         * refresh token 本身不放入 access token，只通过响应体返回给客户端保存。
         */
        return new LoginResponse(
                tokenService.issueAccessToken(userInfo, sessionId),
                refreshToken,
                AUTHORIZATION_SCHEME_BEARER,
                tokenService.accessTokenTtlSeconds(),
                refreshExpiresIn,
                sessionId,
                userInfo
        );
    }

    /**
     * 使用 refresh token 轮换新的 token pair。
     *
     * <p>
     * refresh token 是长期凭证，失败原因统一收敛为 {@code AUTH-401}：
     * 调用方不应通过响应差异判断 token 是过期、篡改、重放、会话吊销还是用户禁用。
     * 对客户端来说这些失败都应清理凭证并重新登录；对攻击者则不能通过响应差异探测
     * token 是否曾经有效、会话或用户状态，以及是否发生过重放。
     * 真正的并发安全由 AuthSessionService 内部的条件更新保证。
     * </p>
     *
     * @param request refresh token 请求
     * @return 新的 token pair 与用户摘要
     */
    @Override
    @Transactional
    public TokenPairResponse refresh(RefreshTokenRequest request) {
        /*
         * 考虑到 service 之间也可能会相互调用，从而绕过 controller 的校验。
         * 这里有必要进行防御性校验。
         */
        Objects.requireNonNull(request, "request must not be null");

        /*
         * refresh token 先做最小格式校验，再进入哈希查询。
         * 格式错误和后续业务校验失败都使用同一种认证失败语义，避免对外暴露差异。
         */
        String oldRefreshToken = refreshTokenParser.parse(request.refreshToken());
        LocalDateTime refreshedAt = LocalDateTime.now();

        /*
         * 通过旧 refresh token 定位服务端会话。
         * 会话必须仍处于 ACTIVE 且 refresh token 未过期；任一条件不满足都视为不可续期。
         */
        AuthSessionEntity session = authSessionService.findByRefreshToken(oldRefreshToken)
                .orElseThrow(AuthException::invalidRefreshToken);
        if (session.getStatus() != AuthSessionStatus.ACTIVE
                || session.getRefreshExpiresAt() == null
                || !session.getRefreshExpiresAt().isAfter(refreshedAt)) {
            throw AuthException.invalidRefreshToken();
        }

        /*
         * refresh 期间仍要回读用户状态，避免已禁用账号继续通过旧会话续期。
         * 对外仍返回统一失败，具体原因只应留在服务端日志或审计中。
         */
        UserEntity user = userMapper.findById(session.getUserId())
                .orElseThrow(AuthException::invalidRefreshToken);
        if (user.getStatus() != UserStatus.ENABLED) {
            throw AuthException.invalidRefreshToken();
        }

        UserInfo userInfo = toUserInfo(user);
        String newRefreshToken = tokenService.issueRefreshToken();
        long refreshExpiresIn = tokenService.refreshTokenTtlSeconds();
        LocalDateTime newRefreshExpiresAt = refreshedAt.plusSeconds(refreshExpiresIn);

        /*
         * 轮换必须依赖数据库条件更新：旧 token hash、会话版本、状态和过期时间同时命中时才成功。
         * 这能保证同一个旧 refresh token 在并发或重放场景下最多成功一次。
         */
        boolean rotated = authSessionService.rotateRefreshToken(
                session,
                oldRefreshToken,
                newRefreshToken,
                refreshedAt,
                newRefreshExpiresAt
        );
        if (!rotated) {
            throw AuthException.invalidRefreshToken();
        }

        // 轮换成功后才签发新的 access token，确保响应中的 token pair 与服务端会话状态一致。
        return new TokenPairResponse(
                tokenService.issueAccessToken(userInfo, session.getSessionId()),
                newRefreshToken,
                AUTHORIZATION_SCHEME_BEARER,
                tokenService.accessTokenTtlSeconds(),
                refreshExpiresIn,
                session.getSessionId(),
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
        // Web 入口的 @ModelAttribute 正常会创建非 null 查询对象；这里仍显式校验，
        // 是为了约束 service 层契约，避免绕过 Controller 直接传 null 时产生语义不清的 NPE。
        Objects.requireNonNull(request, "request must not be null");
        PageRequest pageRequest = request.toPageRequest();
        UserQueryCriteria criteria = request.toCriteria();
        long total = userMapper.countByCriteria(criteria);
        if (total == 0) {
            /*
             * List.of() 的零参数版本返回一个不可变空列表，元素类型不是由空列表本身决定的，
             * 而是由当前调用上下文推断出来的。这里 PageResponse.of 需要 List<UserInfo>，
             * 因此编译器会把 List.of() 推断为 List<UserInfo>。
             *
             * 需要注意：Java 泛型存在类型擦除，UserInfo 这个泛型参数主要用于编译期类型检查；
             * 运行时这个空 List 对象本身并不会保存“元素类型是 UserInfo”的信息。
             */
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
     * <p>
     * 这里的事务边界不是因为单条 UPDATE 本身必须依赖事务；单条 SQL 在没有显式事务时也可以由数据库自动提交。
     * 但该方法表达的是一个完整业务操作：先更新用户状态，再回读用户与角色信息作为接口响应。
     * 将“状态更新 + 回读响应”放在同一事务内，可以减少更新提交后、回读完成前被并发删除或修改导致的响应不一致；
     * 同时也为后续追加审计日志、通知、会话失效、缓存清理等步骤保留一起提交或一起回滚的业务边界。
     * </p>
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

    /**
     * 按用户 ID 批量查询用户角色编码，并按用户维度聚合。
     *
     * <p>
     * 该方法主要服务于用户分页列表场景：分页查询已经拿到了当前页的用户集合，
     * 再一次性查询这些用户的角色编码，避免在遍历用户时逐个调用角色查询接口造成 N+1 查询问题。
     * </p>
     *
     * <p>
     * 返回结果以用户 ID 作为 key，以该用户拥有的角色编码列表作为 value。
     * 如果入参为空，直接返回不可变空 Map，避免发起无意义的数据库查询。
     * 如果某个用户没有角色记录，则返回 Map 中不会包含该用户 ID，调用方需要自行提供默认空角色列表。
     * </p>
     *
     * @param users 当前页用户实体列表
     * @return 用户 ID 到角色编码列表的映射
     */
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

    private String newSessionId() {
        // UUID 是 JDK 提供的通用唯一标识符类型，这里使用 randomUUID() 生成随机版本的 UUID。
        // sessionId 只作为服务端认证会话的公开标识，不携带用户 ID、状态或过期时间等业务信息。
        // 真正的会话状态仍然以 auth_sessions 表为准，这样后续才能支持服务端吊销和会话管理。
        // toString() 会把 UUID 转成标准字符串格式，例如 xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx，便于存库和放入 JWT sid claim。
        return UUID.randomUUID().toString();
    }
}
