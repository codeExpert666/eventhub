package com.eventhub.infra.security.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.eventhub.infra.security.config.AuthTokenProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;

/**
 * JWT 编解码组件。
 *
 * <p>
 * 该类只负责 JWT 底层技术能力：
 * 生成 JWT、解析 JWT、校验签名、校验过期时间、校验 issuer 并提取 claims。
 * 它不读取 HTTP Header，不操作 {@code SecurityContext}，也不查询用户表。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class JwtCodec {

    private static final String CLAIM_SESSION_ID = "sid";
    private static final String CLAIM_TOKEN_TYPE = "typ";

    private final AuthTokenProperties authTokenProperties;

    /**
     * 使用配置中的默认有效期签发 access token。
     *
     * @param claims token 中需要保存的最小身份声明
     * @return JWT 字符串
     */
    public String generateAccessToken(JwtClaims claims) {
        return generateAccessToken(claims, authTokenProperties.getAccessToken().getTtl());
    }

    /**
     * 使用指定有效期签发 access token。
     *
     * <p>
     * 生产代码通常使用默认有效期；测试代码可以传入负数有效期构造过期 token，
     * 从而验证认证失败分支。
     * </p>
     *
     * @param claims token 中需要保存的最小身份声明
     * @param ttl    token 有效期
     * @return JWT 字符串
     */
    public String generateAccessToken(JwtClaims claims, Duration ttl) {
        Objects.requireNonNull(claims, "claims must not be null");
        if (!JwtClaims.ACCESS_TOKEN_TYPE.equals(claims.tokenType())) {
            throw new IllegalArgumentException("JWT typ must be access");
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);

        return Jwts.builder()
                .issuer(authTokenProperties.getIssuer())
                .subject(String.valueOf(claims.subjectId()))
                .id(claims.tokenId())
                .claim(CLAIM_SESSION_ID, claims.sessionId())
                .claim(CLAIM_TOKEN_TYPE, claims.tokenType())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey())
                .compact();
    }

    /**
     * 解析并校验 access token。
     *
     * <p>
     * JJWT 的解析过程会同时完成签名校验、过期时间校验和 issuer 校验。
     * 如果 token 被篡改、过期、issuer 不匹配或格式错误，会抛出运行时异常，
     * 由上层认证过滤器统一转换为 401 响应。
     * </p>
     *
     * @param token 去掉 Bearer 前缀后的 JWT 字符串
     * @return token 中的最小身份声明
     */
    public JwtClaims parseAccessToken(String token) {
        Claims payload = Jwts.parser()
                .verifyWith(signingKey())
                .requireIssuer(authTokenProperties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String subject = payload.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("JWT subject is required");
        }

        String tokenId = payload.getId();
        if (tokenId == null || tokenId.isBlank()) {
            throw new IllegalArgumentException("JWT jti is required");
        }

        String sessionId = payload.get(CLAIM_SESSION_ID, String.class);
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("JWT sid is required");
        }

        String tokenType = payload.get(CLAIM_TOKEN_TYPE, String.class);
        if (!JwtClaims.ACCESS_TOKEN_TYPE.equals(tokenType)) {
            throw new IllegalArgumentException("JWT typ must be access");
        }

        try {
            return new JwtClaims(Long.valueOf(subject), tokenId, sessionId, tokenType);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("JWT subject must be numeric", exception);
        }
    }

    private SecretKey signingKey() {
        /*
         * AuthTokenProperties 已通过启动期校验保证 access token 签名密钥非空且长度满足最低要求。
         * Keys.hmacShaKeyFor 会在算法要求不满足时继续快速失败，避免使用弱密钥签发 token。
         */
        return Keys.hmacShaKeyFor(authTokenProperties.getAccessToken()
                .getSigningSecret()
                .getBytes(StandardCharsets.UTF_8));
    }
}
