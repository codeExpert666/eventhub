package com.eventhub.modules.auth.service.impl;

import com.eventhub.infra.security.config.AuthTokenProperties;
import com.eventhub.infra.security.jwt.JwtClaims;
import com.eventhub.infra.security.jwt.JwtCodec;
import com.eventhub.modules.auth.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * token 业务语义服务实现。
 *
 * <p>
 * 当前阶段 access token 只接收用户主键和服务端会话标识，并写入最小认证声明。
 * 用户名、邮箱、状态和角色不会进入 token 签发边界，最终用户状态和权限仍在每次请求时重新加载最新数据。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private static final int REFRESH_TOKEN_BYTE_LENGTH = 32;

    private final JwtCodec jwtCodec;
    private final AuthTokenProperties authTokenProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String issueAccessToken(Long userId, String sessionId) {
        return jwtCodec.generateAccessToken(new JwtClaims(
                userId,
                UUID.randomUUID().toString(),
                sessionId,
                JwtClaims.ACCESS_TOKEN_TYPE
        ));
    }

    @Override
    public String issueRefreshToken() {
        // refresh token 是 opaque token（本质就是随机字符串）：客户端只负责保存和提交，不能从 token 本身解析出用户信息。
        // 这里直接生成 32 字节随机数，重点是保证足够高的随机性，降低被猜中的概率。
        byte[] randomBytes = new byte[REFRESH_TOKEN_BYTE_LENGTH];

        // SecureRandom 是 JDK 提供的密码学安全随机数生成器，比普通 Random 更适合生成 token、盐值等安全敏感数据。
        secureRandom.nextBytes(randomBytes);

        // 使用 Base64 URL-safe 编码把二进制随机数转成可放入 HTTP/JSON 的字符串。
        // withoutPadding 会去掉末尾的 '='，让 token 更短，也避免某些客户端或 URL 场景处理 padding 时出错。
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }

    @Override
    public long accessTokenTtlSeconds() {
        return authTokenProperties.getAccessToken().getTtl().toSeconds();
    }

    @Override
    public long refreshTokenTtlSeconds() {
        return authTokenProperties.getRefreshToken().getTtl().toSeconds();
    }
}
