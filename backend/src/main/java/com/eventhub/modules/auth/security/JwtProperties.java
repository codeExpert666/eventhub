package com.eventhub.modules.auth.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * JWT 配置属性。
 * 将密钥、签发方和有效期从代码中抽离出来，便于本地开发、测试和生产环境分别覆盖。
 */
@Validated
@ConfigurationProperties(prefix = "eventhub.security.jwt")
public class JwtProperties {

    /**
     * JWT 签发方。
     * 解析 token 时会校验该字段，避免误接受其他系统签发的 token。
     */
    @NotBlank
    private String issuer = "eventhub-backend";

    /**
     * HS256 签名密钥。
     * 长度要求至少 32 字节，满足 HMAC-SHA256 的基础强度要求。
     */
    @NotBlank
    @Size(min = 32)
    private String secret;

    /**
     * access token 有效期。
     */
    @NotNull
    private Duration accessTokenTtl = Duration.ofMinutes(30);

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }
}
