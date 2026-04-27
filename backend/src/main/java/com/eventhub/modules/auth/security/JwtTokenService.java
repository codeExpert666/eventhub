package com.eventhub.modules.auth.security;

import com.eventhub.modules.auth.vo.UserInfo;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * JWT 签发与解析服务。
 * 该类只负责 token 的密码学签名、有效期和 claims 读写，不直接决定用户是否有权访问某个接口。
 */
@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLES = "roles";

    private final JwtProperties jwtProperties;

    /**
     * 使用配置中的默认有效期签发 access token。
     *
     * @param user 登录用户摘要
     * @return JWT 字符串
     */
    public String generateAccessToken(UserInfo user) {
        return generateAccessToken(user, jwtProperties.getAccessTokenTtl());
    }

    /**
     * 使用指定有效期签发 access token。
     * 生产代码使用默认有效期；测试会传入负有效期构造过期 token，以覆盖认证失败分支。
     *
     * @param user 登录用户摘要
     * @param ttl token 有效期
     * @return JWT 字符串
     */
    public String generateAccessToken(UserInfo user, Duration ttl) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        return Jwts.builder()
                .issuer(jwtProperties.getIssuer())
                .subject(String.valueOf(user.id()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_USERNAME, user.username())
                .claim(CLAIM_ROLES, user.roles())
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
    public JwtPrincipalClaims parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .requireIssuer(jwtProperties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Long userId = Long.valueOf(claims.getSubject());
        String username = claims.get(CLAIM_USERNAME, String.class);
        return new JwtPrincipalClaims(userId, username, readRoles(claims.get(CLAIM_ROLES)));
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

    private List<String> readRoles(Object rawRoles) {
        if (rawRoles instanceof Collection<?> collection) {
            return collection.stream()
                    .map(String::valueOf)
                    .toList();
        }
        return List.of();
    }
}
