package com.eventhub.infra.jwt.config;

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
 * 这个类专门负责承接配置文件中的 JWT 参数，
 * 例如 application.yml、application-dev.yml、application-prod.yml 中的：
 * </p>
 *
 * <pre>
 * eventhub:
 *   security:
 *     jwt:
 *       issuer: eventhub-backend
 *       access-token-ttl: 30m
 *       secret: ...
 * </pre>
 *
 * <p>
 * 把密钥、签发方和有效期从代码中抽离出来后，
 * 本地开发、自动化测试和生产环境就可以使用不同配置，
 * 避免把生产密钥硬编码到代码仓库中。
 * </p>
 */
// 开启配置属性校验：当下面字段上的 @NotBlank、@Size、@NotNull 不满足时，应用启动会失败。
@Validated
/*
 * 声明这是一个“配置属性类”，并指定配置前缀为 eventhub.security.jwt。
 *
 * Spring Boot 会把配置文件中该前缀下的属性绑定到这个类的字段上：
 * - eventhub.security.jwt.issuer -> issuer
 * - eventhub.security.jwt.secret -> secret
 * - eventhub.security.jwt.access-token-ttl -> accessTokenTtl
 *
 * 注意 access-token-ttl 和 accessTokenTtl 的命名差异：
 * Spring Boot 支持宽松绑定，配置文件常用 kebab-case，Java 字段常用 camelCase。
 */
/*
 * 配置属性对象是少数允许 setter 的基础设施类：
 * Spring Boot Binder 需要通过 setter 写入配置，业务组件则通过 getter 读取配置。
 *
 * 这里故意不使用 @Data，因为 @Data 会额外生成 toString/equals/hashCode；
 * JwtProperties 包含 secret 字段，避免生成 toString 可以降低密钥被日志误输出的风险。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "eventhub.security.jwt")
public class JwtProperties {

    /**
     * JWT 签发方。
     *
     * <p>
     * 签发 token 时会把 issuer 写入 JWT；
     * 解析 token 时也会校验 issuer，避免误接受其他系统签发的、结构相似的 token。
     * </p>
     *
     * <p>
     * 这里提供默认值，是为了让本地开发和测试在未显式配置 issuer 时也能启动。
     * 生产环境仍可以通过配置文件或环境变量覆盖。
     * </p>
     */
    // 不能为空字符串；如果配置成空值，启动阶段会直接失败。
    @NotBlank
    private String issuer = "eventhub-backend";

    /**
     * HS256 签名密钥。
     *
     * <p>
     * JwtTokenProvider 会用这个密钥生成 HMAC-SHA256 签名。
     * 签名的作用是保证 token 没有被客户端或第三方篡改。
     * </p>
     *
     * <p>
     * 这个字段没有提供代码默认值，是有意为之：
     * 公共 application.yml 不应该内置真实密钥；
     * dev/test profile 可以提供学习和测试用密钥；
     * prod profile 必须通过部署环境显式注入。
     * </p>
     */
    // 密钥不能为空，避免启动出一个无法正确签发/校验 token 的应用实例。
    @NotBlank
    // HS256 至少需要具备足够长度；这里要求最少 32 个字符，降低弱密钥风险。
    @Size(min = 32)
    private String secret;

    /**
     * access token 有效期。
     *
     * <p>
     * Spring Boot 可以把配置中的 30m、1h、PT30M 等时间格式转换成 Duration。
     * JwtTokenProvider 签发 token 时会用该值计算 expiration 过期时间。
     * </p>
     */
    // 有效期不能为空；如果缺失，签发 token 时无法计算过期时间。
    @NotNull
    private Duration accessTokenTtl = Duration.ofMinutes(30);
}
