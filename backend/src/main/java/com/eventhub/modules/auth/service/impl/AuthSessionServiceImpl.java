package com.eventhub.modules.auth.service.impl;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventhub.modules.auth.entity.AuthSessionEntity;
import com.eventhub.modules.auth.mapper.AuthSessionMapper;
import com.eventhub.modules.auth.service.AuthSessionService;
import com.eventhub.modules.auth.service.RefreshTokenHasher;

import lombok.RequiredArgsConstructor;

/**
 * 认证会话应用服务实现。
 *
 * <p>
 * 该实现只封装服务端会话的持久化语义：创建时哈希 refresh token，吊销时只允许 ACTIVE 会话转为 REVOKED。
 * 登录流程会调用创建方法生成 ACTIVE 会话；登出流程暂不接入服务端吊销，后续可在当前服务边界内继续扩展。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class AuthSessionServiceImpl implements AuthSessionService {

    private final AuthSessionMapper authSessionMapper;
    private final RefreshTokenHasher refreshTokenHasher;

    /**
     * 创建 ACTIVE 认证会话。
     *
     * <p>
     * 这里的 {@link Transactional} 是 Spring 声明式事务边界，不是数据库事务本身。
     * Spring 会在代理调用本方法时开启或加入事务，并把底层 JDBC 操作绑定到同一个数据库连接上；
     * 方法正常返回时提交，抛出运行时异常时回滚。
     * </p>
     *
     * <p>
     * 当前实现表面上只有一次 insert，但 insert 后仍会校验受影响行数和主键回填结果。
     * 如果该方法被登录流程之外的调用方单独复用，事务可以保证“会话创建成功”这一业务语义整体成立：
     * 一旦后续校验失败，就回滚已经写入的 ACTIVE 会话，避免调用方感知失败但数据库残留有效会话。
     * 在 {@code AuthServiceImpl.login} 的外层事务内调用时，默认 REQUIRED 传播行为会加入外层事务。
     * </p>
     */
    @Override
    @Transactional
    public AuthSessionEntity createActiveSession(
            Long userId,
            String sessionId,
            String refreshToken,
            LocalDateTime issuedAt,
            LocalDateTime refreshExpiresAt) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        Objects.requireNonNull(refreshExpiresAt, "refreshExpiresAt must not be null");

        AuthSessionEntity session = AuthSessionEntity.activeSession(
                sessionId,
                userId,
                refreshTokenHasher.hash(refreshToken),
                issuedAt,
                refreshExpiresAt
        );
        int affectedRows = authSessionMapper.insert(session);
        if (affectedRows != 1 || session.getId() == null) {
            throw new IllegalStateException("Failed to create auth session");
        }
        return session;
    }

    @Override
    public Optional<AuthSessionEntity> findBySessionId(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        return authSessionMapper.findBySessionId(sessionId);
    }

    @Override
    @Transactional
    public boolean revokeSession(String sessionId, LocalDateTime revokedAt, String revokeReason) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(revokedAt, "revokedAt must not be null");
        Objects.requireNonNull(revokeReason, "revokeReason must not be null");
        return authSessionMapper.revokeBySessionId(sessionId, revokedAt, revokeReason) == 1;
    }
}
