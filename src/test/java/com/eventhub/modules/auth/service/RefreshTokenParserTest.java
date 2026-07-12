package com.eventhub.modules.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.eventhub.modules.auth.exception.AuthException;

/**
 * opaque refresh token 格式校验测试。
 * 该测试只覆盖 token 外形约束，身份有效性仍由数据库哈希匹配集成测试覆盖。
 */
class RefreshTokenParserTest {

    private final RefreshTokenParser refreshTokenParser = new RefreshTokenParser();

    /**
     * 当前 refresh token 由 32 字节随机数 Base64 URL-safe 无 padding 编码而来，长度固定为 43。
     */
    @Test
    void parseShouldAcceptCurrentOpaqueTokenShape() {
        String refreshToken = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefg";

        assertThat(refreshTokenParser.parse(refreshToken)).isEqualTo(refreshToken);
    }

    /**
     * 长度不匹配说明不是当前系统签发的 refresh token。
     */
    @Test
    void parseShouldRejectWrongLengthToken() {
        assertThatThrownBy(() -> refreshTokenParser.parse("short"))
                .isInstanceOf(AuthException.class);
    }

    /**
     * refresh token 只允许 URL-safe Base64 字符，不接受 padding、空白或其他符号。
     */
    @Test
    void parseShouldRejectNonUrlSafeBase64Token() {
        assertThatThrownBy(() -> refreshTokenParser.parse("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef="))
                .isInstanceOf(AuthException.class);
    }
}
