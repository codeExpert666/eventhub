package com.eventhub.modules.auth.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;

import com.eventhub.modules.auth.entity.AuthSessionEntity;
import com.eventhub.modules.auth.entity.UserEntity;
import com.eventhub.modules.auth.enums.AuthSessionStatus;
import com.eventhub.modules.auth.service.RefreshTokenHasher;

/**
 * auth_sessions 表 MyBatis 映射测试。
 *
 * <p>
 * 这些用例通过 SpringBootTest 启动完整测试上下文，让 Flyway 迁移先在 H2 MySQL 模式下执行，
 * 再验证 Mapper 的插入、查询、唯一约束和状态更新能力。
 * </p>
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthSessionMapperTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private AuthSessionMapper authSessionMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RefreshTokenHasher refreshTokenHasher;

    /**
     * 验证会话插入后可以按 session_id 和 refresh_token_hash 查询回来。
     */
    @Test
    void insertAndFindShouldPersistActiveSession() {
        Long userId = createUser();
        AuthSessionEntity session = newSession(userId, "insert-select");
        session.setClientIpHash(refreshTokenHasher.hash("127.0.0.1"));
        session.setUserAgentHash(refreshTokenHasher.hash("JUnit User-Agent"));
        session.setUserAgentSummary("JUnit on H2");

        int affectedRows = authSessionMapper.insert(session);

        assertThat(affectedRows).isEqualTo(1);
        assertThat(session.getId()).isNotNull();

        Optional<AuthSessionEntity> foundBySessionId = authSessionMapper.findBySessionId(session.getSessionId());
        assertThat(foundBySessionId).isPresent();
        assertThat(foundBySessionId.get().getUserId()).isEqualTo(userId);
        assertThat(foundBySessionId.get().getStatus()).isEqualTo(AuthSessionStatus.ACTIVE);
        assertThat(foundBySessionId.get().getRefreshTokenHash()).isEqualTo(session.getRefreshTokenHash());
        assertThat(foundBySessionId.get().getClientIpHash()).isEqualTo(session.getClientIpHash());
        assertThat(foundBySessionId.get().getUserAgentHash()).isEqualTo(session.getUserAgentHash());
        assertThat(foundBySessionId.get().getUserAgentSummary()).isEqualTo(session.getUserAgentSummary());

        Optional<AuthSessionEntity> foundByRefreshHash = authSessionMapper.findByRefreshTokenHash(
                session.getRefreshTokenHash()
        );
        assertThat(foundByRefreshHash).isPresent();
        assertThat(foundByRefreshHash.get().getSessionId()).isEqualTo(session.getSessionId());
    }

    /**
     * 验证 session_id 唯一约束是并发或重复创建同一会话的最终防线。
     */
    @Test
    void duplicateSessionIdShouldBeRejectedByUniqueConstraint() {
        Long userId = createUser();
        AuthSessionEntity firstSession = newSession(userId, "duplicate-session");
        AuthSessionEntity secondSession = newSession(userId, "duplicate-session-other-token");
        secondSession.setSessionId(firstSession.getSessionId());

        assertThat(authSessionMapper.insert(firstSession)).isEqualTo(1);

        assertThatThrownBy(() -> authSessionMapper.insert(secondSession))
                .isInstanceOf(DuplicateKeyException.class);
    }

    /**
     * 验证单会话吊销会把 ACTIVE 会话更新为 REVOKED，并写入吊销审计字段。
     */
    @Test
    void revokeBySessionIdShouldUpdateActiveSessionToRevoked() {
        Long userId = createUser();
        AuthSessionEntity session = newSession(userId, "revoke");
        authSessionMapper.insert(session);

        LocalDateTime revokedAt = LocalDateTime.now().withNano(0);
        int affectedRows = authSessionMapper.revokeBySessionId(
                session.getSessionId(),
                revokedAt,
                "LOGOUT"
        );

        assertThat(affectedRows).isEqualTo(1);
        AuthSessionEntity revoked = authSessionMapper.findBySessionId(session.getSessionId()).orElseThrow();
        assertThat(revoked.getStatus()).isEqualTo(AuthSessionStatus.REVOKED);
        assertThat(revoked.getRevokedAt()).isNotNull();
        assertThat(revoked.getRevokeReason()).isEqualTo("LOGOUT");
        assertThat(revoked.getVersion()).isEqualTo(1);
    }

    /**
     * 验证 Mapper 能正确绑定 ACTIVE 和 REVOKED 枚举值。
     * 该方法只验证持久化映射能力，真实业务吊销仍应优先使用 revokeBySessionId。
     */
    @Test
    void updateStatusShouldSupportActiveAndRevokedValues() {
        Long userId = createUser();
        AuthSessionEntity session = newSession(userId, "status-values");
        authSessionMapper.insert(session);

        assertThat(authSessionMapper.updateStatus(session.getSessionId(), AuthSessionStatus.REVOKED)).isEqualTo(1);
        assertThat(authSessionMapper.findBySessionId(session.getSessionId()).orElseThrow().getStatus())
                .isEqualTo(AuthSessionStatus.REVOKED);

        assertThat(authSessionMapper.updateStatus(session.getSessionId(), AuthSessionStatus.ACTIVE)).isEqualTo(1);
        assertThat(authSessionMapper.findBySessionId(session.getSessionId()).orElseThrow().getStatus())
                .isEqualTo(AuthSessionStatus.ACTIVE);
    }

    /**
     * 验证并发创建不同 session_id 的会话不会互相冲突。
     */
    @Test
    void concurrentCreateDifferentSessionsShouldNotConflict() throws Exception {
        Long userId = createUser();
        int taskCount = 6;
        ExecutorService executorService = Executors.newFixedThreadPool(taskCount);
        CountDownLatch ready = new CountDownLatch(taskCount);
        CountDownLatch start = new CountDownLatch(1);
        List<String> sessionIds = new ArrayList<>();
        List<Callable<String>> tasks = new ArrayList<>();

        for (int index = 0; index < taskCount; index++) {
            String sessionIdSeed = nextValue("concurrent-" + index);
            AuthSessionEntity session = newSession(userId, sessionIdSeed);
            sessionIds.add(session.getSessionId());
            tasks.add(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start concurrent session creation");
                }
                authSessionMapper.insert(session);
                return session.getSessionId();
            });
        }

        try {
            List<Future<String>> futures = tasks.stream()
                    .map(executorService::submit)
                    .toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

            start.countDown();

            for (Future<String> future : futures) {
                assertThat(future.get(10, TimeUnit.SECONDS)).isIn(sessionIds);
            }
            for (String sessionId : sessionIds) {
                assertThat(authSessionMapper.findBySessionId(sessionId)).isPresent();
            }
        } finally {
            executorService.shutdownNow();
        }
    }

    private Long createUser() {
        String username = nextValue("session-user");
        UserEntity user = UserEntity.enabledUser(
                username,
                username + "@eventhub.test",
                "$2y$10$test-password-hash-placeholder"
        );
        int affectedRows = userMapper.insert(user);
        assertThat(affectedRows).isEqualTo(1);
        assertThat(user.getId()).isNotNull();
        return user.getId();
    }

    private AuthSessionEntity newSession(Long userId, String seed) {
        LocalDateTime issuedAt = LocalDateTime.now().withNano(0);
        String uniqueSeed = nextValue(seed);
        return AuthSessionEntity.activeSession(
                "sess-" + uniqueSeed,
                userId,
                refreshTokenHasher.hash("refresh-token-" + uniqueSeed),
                issuedAt,
                issuedAt.plusDays(30)
        );
    }

    private static String nextValue(String prefix) {
        return prefix + "-" + SEQUENCE.incrementAndGet();
    }
}
