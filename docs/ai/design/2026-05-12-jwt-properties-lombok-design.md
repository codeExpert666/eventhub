# JwtProperties 使用 Lombok 收敛样板代码设计

## 1. 背景
- `JwtProperties` 负责承接 `eventhub.security.jwt` 下的 JWT 配置，包括签发方、签名密钥和 access token 有效期。
- 当前类中手写了 `getIssuer`、`setIssuer`、`getSecret`、`setSecret`、`getAccessTokenTtl`、`setAccessTokenTtl`，这些方法主要服务于 Spring Boot 配置绑定和业务组件读取配置。
- 项目已经引入 Lombok，并已有 ADR 约束 Lombok 的使用边界，因此可以用 Lombok 减少低价值样板代码。

## 2. 目标
- 使用 Lombok 生成 `JwtProperties` 的 getter/setter。
- 保持现有配置项名称、默认值、校验注解和运行时行为不变。
- 避免引入 `@Data` 导致 `secret` 被 `toString` 等方法意外暴露。

## 3. 非目标
- 不修改 JWT 签发、解析和鉴权过滤流程。
- 不调整配置前缀 `eventhub.security.jwt` 或字段命名。
- 不引入构造器绑定、不可变配置对象或新的配置加载机制。
- 不修改数据库、缓存、接口或权限模型。

## 4. 影响范围
- 基础设施配置：`backend/src/main/java/com/eventhub/infra/jwt/config/JwtProperties.java`。
- 文档：新增本设计文档和对应实现说明。
- 不涉及表结构、索引、缓存或外部接口。

## 5. 领域建模
- 本次不新增业务领域对象。
- `JwtProperties` 仍是基础设施层配置属性对象，不是业务领域实体。
- 核心字段保持不变：
  - `issuer`：JWT 签发方。
  - `secret`：HS256 签名密钥。
  - `accessTokenTtl`：access token 有效期。
- 无业务状态流转变化。

## 6. API 设计
- 不新增或修改 HTTP API。
- `JwtTokenProvider` 等调用方继续通过同名 getter 读取配置。
- Lombok 生成的方法签名需要与原手写方法保持一致：
  - `getIssuer()` / `setIssuer(String issuer)`
  - `getSecret()` / `setSecret(String secret)`
  - `getAccessTokenTtl()` / `setAccessTokenTtl(Duration accessTokenTtl)`

## 7. 数据设计
- 不调整数据库表结构。
- 不新增索引或唯一约束。
- 不涉及持久化数据一致性变化。

## 8. 关键流程
- 正常流程：
  1. Spring Boot 启动时识别 `@ConfigurationProperties(prefix = "eventhub.security.jwt")`。
  2. Binder 通过 Lombok 生成的 setter 写入配置值。
  3. `@Validated` 和字段校验注解继续在启动期校验配置。
  4. `JwtTokenProvider` 通过 Lombok 生成的 getter 读取配置。
- 异常流程：
  - 如果 Lombok annotation processing 未生效，编译阶段会暴露找不到 getter/setter 的问题。
  - 如果配置缺失或不满足校验规则，仍由 Bean Validation 在启动阶段失败。
- 状态流转：
  - 本次无业务状态机变化。

## 9. 并发 / 幂等 / 缓存
- 不涉及订单提交、库存扣减或支付回调等并发场景。
- 不新增幂等键或防重复提交逻辑。
- 不新增缓存读写。
- 配置对象仍由 Spring 容器管理，启动后业务代码只读取配置，不引入新的共享可变状态写入路径。

## 10. 权限与安全
- 不新增权限入口或鉴权规则。
- 不使用 `@Data`，避免为包含 `secret` 的配置类生成 `toString()`，降低密钥被日志误输出的风险。
- `@Setter` 只用于配置绑定场景，不作为业务对象的默认建模方式。

## 11. 测试策略
- 编译验证：运行 Maven 测试，确认 Lombok 生成访问器后调用方仍能编译。
- 集成测试：优先运行 Auth/JWT 相关测试，确认配置绑定、token 签发和鉴权链路不受影响。
- 异常场景验证：依赖现有配置校验测试或启动期测试覆盖，确认 `@NotBlank`、`@Size`、`@NotNull` 注解仍保留。
- 手工验证：本次不改变接口契约，若需要可继续通过登录接口和带 token 的受保护接口验证。

## 12. 风险与替代方案
- 当前方案风险：
  - IDE 未启用 Lombok annotation processing 时可能出现编辑器误报，但 Maven 编译应能准确验证。
  - 类级 `@Setter` 会让全部字段生成 setter，需要通过注释说明这是配置绑定场景的例外。
- 备选方案 A：继续保留手写 getter/setter。
  - 优点是显式，缺点是与项目已引入 Lombok 的工程规范不一致，样板代码继续占据阅读空间。
- 备选方案 B：使用 `@Data`。
  - 没有采用，因为它会额外生成 `toString`、`equals`、`hashCode`，对包含 `secret` 的配置类不合适。
- 备选方案 C：改为构造器绑定的不可变配置对象。
  - 没有采用，因为本次目标是最小化收敛样板代码，不调整配置绑定模式，避免扩大改动范围。
