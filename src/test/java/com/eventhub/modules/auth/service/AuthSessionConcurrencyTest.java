package com.eventhub.modules.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
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
import org.springframework.test.context.ActiveProfiles;

import com.eventhub.modules.auth.entity.AuthSessionEntity;
import com.eventhub.modules.auth.entity.UserEntity;
import com.eventhub.modules.auth.enums.AuthSessionStatus;
import com.eventhub.modules.auth.mapper.AuthSessionMapper;
import com.eventhub.modules.auth.mapper.UserMapper;

/**
 * 认证会话 refresh 轮换并发测试。
 *
 * <p>
 * 该测试绕过 HTTP 层，直接验证 AuthSessionService 的条件更新防线：
 * 两个线程使用同一个旧 refresh token 和同一个旧 version 时，数据库只能允许一个线程完成轮换。
 * </p>
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthSessionConcurrencyTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private AuthSessionService authSessionService;

    @Autowired
    private AuthSessionMapper authSessionMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RefreshTokenHasher refreshTokenHasher;

    /**
     * 验证同一个旧 refresh token 并发轮换时，只有一个条件更新能成功。
     */
    @Test
    void concurrentRotateShouldAllowOnlyOneSuccess() throws Exception {
        Long userId = createUser();
        String oldRefreshToken = nextValue("old-refresh-token");
        String sessionId = "sess-" + nextValue("concurrent-rotate");
        LocalDateTime issuedAt = LocalDateTime.now().withNano(0);
        authSessionService.createActiveSession(
                userId,
                sessionId,
                oldRefreshToken,
                issuedAt,
                issuedAt.plusDays(30)
        );
        AuthSessionEntity snapshot = authSessionMapper.findBySessionId(sessionId).orElseThrow();
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> rotateTask = () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to start concurrent rotate");
            }
            String newRefreshToken = nextValue("new-refresh-token");
            LocalDateTime refreshedAt = LocalDateTime.now().withNano(0);
            return authSessionService.rotateRefreshToken(
                    snapshot,
                    oldRefreshToken,
                    newRefreshToken,
                    refreshedAt,
                    refreshedAt.plusDays(30)
            );
        };

        try {
            Future<Boolean> firstResult = executorService.submit(rotateTask);
            Future<Boolean> secondResult = executorService.submit(rotateTask);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

            start.countDown();

            List<Boolean> results = List.of(
                    firstResult.get(10, TimeUnit.SECONDS),
                    secondResult.get(10, TimeUnit.SECONDS)
            );
            assertThat(results).containsExactlyInAnyOrder(true, false);

            AuthSessionEntity rotated = authSessionMapper.findBySessionId(sessionId).orElseThrow();
            assertThat(rotated.getStatus()).isEqualTo(AuthSessionStatus.ACTIVE);
            assertThat(rotated.getVersion()).isEqualTo(1);
            assertThat(rotated.getRefreshTokenHash()).isNotEqualTo(refreshTokenHasher.hash(oldRefreshToken));
        } finally {
            executorService.shutdownNow();
        }
    }

    private Long createUser() {
        String username = nextValue("session-concurrency-user");
        UserEntity user = UserEntity.enabledUser(
                username,
                username + "@eventhub.test",
                "$2y$10$test-password-hash-placeholder"
        );
        assertThat(userMapper.insert(user)).isEqualTo(1);
        assertThat(user.getId()).isNotNull();
        return user.getId();
    }

    private static String nextValue(String prefix) {
        return prefix + "-" + SEQUENCE.incrementAndGet();
    }
}
