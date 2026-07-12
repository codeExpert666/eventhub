package com.eventhub.modules.auth.service;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.eventhub.modules.auth.exception.AuthException;

/**
 * opaque refresh token 格式校验器。
 *
 * <p>
 * 当前 refresh token 由 32 字节随机数经过 Base64 URL-safe 无 padding 编码得到，长度固定为 43。
 * 该组件只做格式边界校验，不从 token 中解析用户、会话或权限信息；身份仍以数据库中的
 * {@code auth_sessions.refresh_token_hash} 匹配结果为准。
 * </p>
 */
@Component
public class RefreshTokenParser {

    // 32 字节随机数 Base64 URL-safe 编码后为 44 字符，去掉 1 个 padding 后固定为 43。
    private static final int OPAQUE_REFRESH_TOKEN_LENGTH = 43;
    // URL-safe Base64 只允许字母、数字、'-'、'_'；withoutPadding 场景不接受 '='。
    private static final Pattern URL_SAFE_BASE64_WITHOUT_PADDING = Pattern.compile("^[A-Za-z0-9_-]+$");

    /**
     * 校验并返回原始 refresh token。
     *
     * @param refreshToken 客户端提交的 refresh token
     * @return 通过格式校验的 refresh token
     */
    public String parse(String refreshToken) {
        if (refreshToken == null
                || refreshToken.length() != OPAQUE_REFRESH_TOKEN_LENGTH
                || !URL_SAFE_BASE64_WITHOUT_PADDING.matcher(refreshToken).matches()) {
            throw AuthException.invalidRefreshToken();
        }
        return refreshToken;
    }
}
