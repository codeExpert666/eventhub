package com.eventhub.modules.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * refresh token 哈希规则测试。
 * 这些用例只验证哈希工具的稳定契约，不依赖 Spring 容器和数据库。
 */
class RefreshTokenHasherTest {

    private final RefreshTokenHasher refreshTokenHasher = new RefreshTokenHasher();

    /**
     * refresh token 明文不能直接落库，哈希结果必须与原 token 不同。
     */
    @Test
    void hashShouldNotEqualOriginalToken() {
        String refreshToken = "refresh-token-plain-value";

        String hashed = refreshTokenHasher.hash(refreshToken);

        assertThat(hashed).isNotEqualTo(refreshToken);
        assertThat(hashed).startsWith("sha256:");
    }

    /**
     * 相同 refresh token 必须生成稳定哈希，后续 refresh API 才能用提交的 token 定位会话。
     */
    @Test
    void sameTokenShouldProduceStableHash() {
        String refreshToken = "same-refresh-token";

        assertThat(refreshTokenHasher.hash(refreshToken))
                .isEqualTo(refreshTokenHasher.hash(refreshToken));
    }

    /**
     * 不同 refresh token 必须生成不同哈希，避免不同设备会话互相误匹配。
     */
    @Test
    void differentTokensShouldProduceDifferentHashes() {
        assertThat(refreshTokenHasher.hash("first-refresh-token"))
                .isNotEqualTo(refreshTokenHasher.hash("second-refresh-token"));
    }

    /**
     * 空 token 没有业务意义，应该在进入持久化前快速失败。
     */
    @Test
    void blankTokenShouldBeRejected() {
        assertThatThrownBy(() -> refreshTokenHasher.hash(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("refreshToken must not be blank");
    }
}
