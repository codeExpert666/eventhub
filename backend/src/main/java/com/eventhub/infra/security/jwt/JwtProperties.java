package com.eventhub.infra.security.jwt;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * JWT 配置属性。
 *
 * <p>
 * 该类只负责承接配置文件中的 JWT 参数，例如签发方、签名密钥和 access token 有效期。
 * 真实生产密钥不应该硬编码到公共配置或代码中，而应通过部署环境注入。
 * </p>
 */
@Validated
@Getter
@Setter
@ConfigurationProperties(prefix = "eventhub.security.jwt")
public class JwtProperties {

    /**
     * JWT 签发方。
     *
     * <p>
     * 签发 token 时写入 issuer，解析 token 时校验 issuer，
     * 避免误接受其他系统签发的结构相似 token。
     * </p>
     */
    @NotBlank
    private String issuer = "eventhub-backend";

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
    private String secret;

    /**
     * access token 有效期。
     */
    @NotNull
    private Duration accessTokenTtl = Duration.ofMinutes(30);
}
