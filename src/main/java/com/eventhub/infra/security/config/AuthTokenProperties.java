package com.eventhub.infra.security.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 认证令牌配置属性。
 *
 * <p>
 * 当前认证模型已经从单一 JWT access token 演进为 access token + opaque refresh token 的令牌对。
 * 因此该配置对象不再放在 {@code jwt} 包下，也不再使用 {@code eventhub.security.jwt} 前缀，
 * 避免把 refresh token 误表达成 JWT。
 * </p>
 */
@Validated
@Getter
@Setter
@ConfigurationProperties(prefix = "eventhub.security.auth-token")
public class AuthTokenProperties {

    /**
     * 认证令牌签发方。
     *
     * <p>
     * 当前只写入 JWT access token 的 issuer，解析 access token 时也会校验 issuer，
     * 避免误接受其他系统签发的结构相似 token。
     * </p>
     */
    @NotBlank
    private String issuer = "eventhub-backend";

    /**
     * access token 配置。
     */
    @Valid
    @NotNull
    private AccessToken accessToken = new AccessToken();

    /**
     * refresh token 配置。
     */
    @Valid
    @NotNull
    private RefreshToken refreshToken = new RefreshToken();

    /**
     * JWT access token 配置。
     *
     * <p>
     * 该类声明为 {@code static} 嵌套类，是因为它只是 {@link AuthTokenProperties}
     * 命名空间下的配置分组，不需要访问外层配置实例。这样可以避免非 static 内部类隐式持有外层对象引用，
     * 也让 Spring Boot {@code @ConfigurationProperties} 绑定时把它当作普通 POJO 创建和校验。
     * </p>
     */
    @Getter
    @Setter
    public static class AccessToken {

        /**
         * access token 有效期。
         */
        @NotNull
        private Duration ttl = Duration.ofMinutes(30);

        /**
         * HS256 签名密钥。
         *
         * <p>
         * 该字段不提供代码默认值，dev/test profile 可以提供学习和测试用密钥，
         * prod profile 必须通过环境变量或外部配置显式注入。
         * </p>
         */
        @NotBlank
        @Size(min = 32)
        private String signingSecret;
    }

    /**
     * opaque refresh token 配置。
     *
     * <p>
     * 该类声明为 {@code static} 嵌套类，是因为它只是 {@link AuthTokenProperties}
     * 命名空间下的配置分组，不需要访问外层配置实例。这样可以避免非 static 内部类隐式持有外层对象引用，
     * 也让 Spring Boot {@code @ConfigurationProperties} 绑定时把它当作普通 POJO 创建和校验。
     * </p>
     */
    @Getter
    @Setter
    public static class RefreshToken {

        /**
         * refresh token 有效期。
         *
         * <p>
         * refresh token 是长期续期凭证，当前默认 30 天。
         * 本配置只控制登录响应中的 refresh token 过期窗口；服务端仍只保存 refresh token 哈希。
         * </p>
         */
        @NotNull
        private Duration ttl = Duration.ofDays(30);
    }
}
