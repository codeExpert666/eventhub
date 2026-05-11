package com.eventhub.infra.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.eventhub.infra.jwt.config.JwtProperties;
import com.eventhub.infra.jwt.model.AccessTokenClaims;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;

/**
 * JWT access token 签发与解析组件。
 *
 * <p>
 * JWT 可以简单理解成一个“带签名的字符串凭证”。
 * 服务端登录成功后把用户的最小身份信息写入 token，并使用服务端密钥签名；
 * 客户端之后每次请求携带 token，服务端通过校验签名和过期时间确认 token 是否可信。
 * </p>
 *
 * <p>
 * 这个类只负责 JWT 本身的签发和解析：
 * - 写入 issuer、subject、issuedAt、expiration 等标准字段；
 * - 使用配置中的 secret 生成签名密钥；
 * - 校验 token 签名、过期时间和 issuer。
 * </p>
 *
 * <p>
 * 它刻意不处理 HTTP 请求头、不操作 {@code SecurityContext}、不查询用户表。
 * 这些职责分别由 JwtAuthenticationFilter 和 AuthenticatedSubjectLoader 承担，
 * 这样可以让 JWT 密码学逻辑与 Spring Security 请求认证逻辑解耦。
 * </p>
 */
@Component
// Lombok 会为 final 字段生成构造器，Spring 通过构造器注入 JwtProperties。
@RequiredArgsConstructor
public class JwtTokenProvider {

    /**
     * JWT 相关配置。
     *
     * <p>
     * 包含 issuer、secret、access token 有效期等参数。
     * 这些值来自 application.yml / application-dev.yml / application-prod.yml 等配置文件，
     * 通过 JwtProperties 上的 @ConfigurationProperties 绑定。
     * </p>
     */
    private final JwtProperties jwtProperties;

    /**
     * 使用配置中的默认有效期签发 access token。
     *
     * <p>
     * 这是业务代码最常用的签发入口。
     * 登录成功后，调用方只需要传入最小身份声明，
     * token 有效期统一使用配置文件中的 eventhub.security.jwt.access-token-ttl。
     * </p>
     *
     * @param claims token 中需要保存的最小身份声明
     * @return JWT 字符串
     */
    public String generateAccessToken(AccessTokenClaims claims) {
        return generateAccessToken(claims, jwtProperties.getAccessTokenTtl());
    }

    /**
     * 使用指定有效期签发 access token。
     *
     * <p>
     * 生产代码通常不直接使用这个重载方法，而是使用默认有效期。
     * 测试代码可以传入特殊 ttl，例如负数有效期，构造“已经过期的 token”，
     * 从而覆盖 JWT 过期后的认证失败分支。
     * </p>
     *
     * <p>
     * JWT 中常见的标准字段：
     * - issuer：签发方，本项目用它区分 token 是否由 eventhub 签发；
     * - subject：主体标识，本项目放用户 ID；
     * - issuedAt：签发时间；
     * - expiration：过期时间。
     * </p>
     *
     * @param claims token 中需要保存的最小身份声明
     * @param ttl    token 有效期
     * @return JWT 字符串
     */
    public String generateAccessToken(AccessTokenClaims claims, Duration ttl) {
        /*
         * 1. 计算签发时间和过期时间。
         *
         * Instant 表示 UTC 时间线上的一个时间点，适合用于 token 过期时间这种跨时区场景。
         * JWT/JJWT API 仍使用 java.util.Date，因此后面会通过 Date.from(...) 转换。
         */
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);

        /*
         * 2. 构建并签发 JWT。
         *
         * compact() 会把 header、payload、signature 三部分序列化成最终的 JWT 字符串：
         * header.payload.signature
         */
        return Jwts.builder()
                // iss：签发方。解析时会要求 issuer 一致，避免误接受其他系统签发的 token。
                .issuer(jwtProperties.getIssuer())
                // sub：主体。这里只放用户 ID，避免在 token 中放入过多易变化或敏感的用户信息。
                .subject(String.valueOf(claims.subjectId()))
                // iat：签发时间。
                .issuedAt(Date.from(now))
                // exp：过期时间。超过该时间后，JJWT 解析时会抛出过期相关异常。
                .expiration(Date.from(expiresAt))
                // 使用服务端密钥对 token 签名，防止客户端篡改 payload 后仍被服务端接受。
                .signWith(signingKey())
                // 生成最终的 JWT 字符串。
                .compact();
    }

    /**
     * 解析并校验 JWT。
     *
     * <p>
     * 这里的“解析”不只是 Base64 解码 payload，
     * 还包含安全校验：
     * - 使用同一个 signingKey 校验签名，确认 token 没被篡改；
     * - 校验 expiration，确认 token 没有过期；
     * - 校验 issuer，确认 token 是本系统签发的。
     * </p>
     *
     * <p>
     * 如果 token 过期、签名非法、issuer 不匹配或格式错误，
     * JJWT 会抛出 JwtException 或相关运行时异常，
     * JwtAuthenticationFilter 会捕获这些异常并统一转换为 401 响应。
     * </p>
     *
     * @param token 去掉 Bearer 前缀后的 JWT 字符串
     * @return token 中的主体 claims
     */
    public AccessTokenClaims parseAccessToken(String token) {
        /*
         * 1. 创建 JWT 解析器并配置校验规则。
         *
         * verifyWith(signingKey())：使用服务端密钥校验签名；
         * requireIssuer(...)：要求 token 中的 iss 必须等于配置中的 issuer；
         * parseSignedClaims(token)：解析带签名的 JWT，并在解析过程中执行签名、过期时间等校验。
         */
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .requireIssuer(jwtProperties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        /*
         * 2. 从 subject 中还原业务主体 ID。
         *
         * 签发 token 时 subject 写入的是 claims.subjectId() 的字符串形式，
         * 解析成功后再转回 Long，封装成项目自己的 AccessTokenClaims。
         * 注意这里如果 subject 不符合项目约定（非数字）， 可能抛出 IllegalArgumentException 异常
         */
        Long subjectId = Long.valueOf(claims.getSubject());
        return new AccessTokenClaims(subjectId);
    }

    /**
     * 读取默认 access token 有效秒数。
     *
     * <p>
     * 这个方法通常用于登录响应：
     * 服务端签发 token 后，可以把 expiresIn 秒数返回给客户端，
     * 客户端据此决定何时提示重新登录或刷新 token。
     * </p>
     *
     * @return 有效秒数
     */
    public long accessTokenTtlSeconds() {
        return jwtProperties.getAccessTokenTtl().toSeconds();
    }

    /**
     * 根据配置中的 secret 构造 HMAC-SHA 签名密钥。
     *
     * <p>
     * 本项目当前使用 JJWT 的 {@code signWith(SecretKey)} 和 {@code verifyWith(SecretKey)}。
     * 只要签发和解析使用同一个密钥，服务端就能验证 token 是否由自己签发且未被篡改。
     * </p>
     *
     * <p>
     * secret 来自配置文件或环境变量。
     * JwtProperties 已经通过 @NotBlank 和 @Size(min = 32) 做了启动期校验，
     * 避免使用空密钥或明显过短的弱密钥启动应用。
     * </p>
     *
     * @return JJWT 可使用的签名密钥
     */
    private SecretKey signingKey() {
        /*
         * 将字符串密钥按 UTF-8 转成字节数组，再交给 JJWT 构造 HMAC-SHA Key。
         * 如果密钥长度不满足算法要求，Keys.hmacShaKeyFor 会抛出异常，避免生成不安全的签名。
         */
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
