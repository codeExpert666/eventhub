package com.eventhub.infra.jwt;

import com.eventhub.infra.jwt.config.JwtProperties;
import com.eventhub.infra.jwt.model.AccessTokenClaims;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * JWT access token 签发与解析组件。
 * 该类只负责 token 的密码学签名、issuer、有效期和 claims 读写，不接触 HTTP 请求、SecurityContext 或 auth 业务模型。
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    /**
     * 使用配置中的默认有效期签发 access token。
     *
     * @param claims token 中需要保存的最小身份声明
     * @return JWT 字符串
     */
    public String generateAccessToken(AccessTokenClaims claims) {
        return generateAccessToken(claims, jwtProperties.getAccessTokenTtl());
    }

    /**
     * 使用指定有效期签发 access token。
     * 生产代码使用默认有效期；测试会传入负有效期构造过期 token，以覆盖认证失败分支。
     *
     * @param claims token 中需要保存的最小身份声明
     * @param ttl token 有效期
     * @return JWT 字符串
     */
    public String generateAccessToken(AccessTokenClaims claims, Duration ttl) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        return Jwts.builder()
                .issuer(jwtProperties.getIssuer())
                .subject(String.valueOf(claims.subjectId()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey())
                .compact();
    }

    /**
     * 解析并校验 JWT。
     * 如果 token 过期、签名非法或 issuer 不匹配，JJWT 会抛出 JwtException，由过滤器统一转换为 401。
     *
     * @param token Bearer token 原始值
     * @return token 中的主体 claims
     */
    public AccessTokenClaims parseAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .requireIssuer(jwtProperties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Long subjectId = Long.valueOf(claims.getSubject());
        return new AccessTokenClaims(subjectId);
    }

    /**
     * 读取默认 access token 有效秒数。
     *
     * @return 有效秒数
     */
    public long accessTokenTtlSeconds() {
        return jwtProperties.getAccessTokenTtl().toSeconds();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
