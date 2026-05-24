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
