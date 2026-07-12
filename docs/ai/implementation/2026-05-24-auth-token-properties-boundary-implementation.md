# 认证令牌配置边界调整实现说明

## 1. 本次改动解决了什么问题

- 解决 `JwtProperties` 名称与职责不匹配的问题：该类已经承载 refresh token TTL，而 refresh token 是 opaque token，不是 JWT。
- 解决 `eventhub.security.jwt` 配置前缀过窄的问题：当前配置已经描述 access token + refresh token 的登录令牌对。
- 解决配置类包路径误导的问题：配置对象不再放在 `infra.security.jwt` 目录下，`jwt` 包继续只放 JWT 编解码相关组件。
- 保持登录 API、JWT claim、auth session 创建和现有测试行为不变。

## 2. 改动内容
- 新增了什么
  - 新增 `AuthTokenProperties`，配置前缀为 `eventhub.security.auth-token`。
  - 新增配置子结构：
    - `access-token.ttl`
    - `access-token.signing-secret`
    - `refresh-token.ttl`
  - 新增设计文档：`docs/ai/design/2026-05-24-auth-token-properties-boundary-design.md`。
  - 新增 ADR：`docs/ai/adr/2026-05-24-auth-token-configuration-boundary.md`。
  - 新增本实现说明文档。
- 修改了什么
  - `JwtProperties` 重命名并移动为 `infra.security.config.AuthTokenProperties`。
  - `SecurityConfig` 改为启用 `AuthTokenProperties`。
  - `JwtCodec` 改为从 `AuthTokenProperties.accessToken` 读取 JWT access token 默认签发 TTL 和签名密钥，但不再对外暴露 TTL 查询方法。
  - `TokenServiceImpl` 改为直接从 `AuthTokenProperties` 读取 access token 和 refresh token TTL。
  - `application.yml`、`application-dev.yml`、`application-prod.yml`、`application-test.yml` 改用新配置路径。
  - `OpenApiProductionSecurityTest` 与 `JwtCodecTest` 更新为新配置对象和新配置项。
  - 更新登录 token pair 设计文档和实现说明中关于配置类的描述。
- 删除了什么
  - 删除旧 `JwtProperties` 类名与旧 `eventhub.security.jwt` 代码配置引用。
  - 不再使用旧生产环境变量 `EVENTHUB_JWT_SECRET`，改为 `EVENTHUB_ACCESS_TOKEN_SIGNING_SECRET`。

## 3. 为什么这样设计
- 关键设计原因
  - `AuthTokenProperties` 表达的是认证令牌对配置，而不是某一种 token 编码格式。
  - `eventhub.security.auth-token` 能清楚说明这些配置属于认证域下的 token pair。
  - `access-token.signing-secret` 明确签名密钥只服务 JWT access token，不属于 opaque refresh token。
  - access token 与 refresh token 的响应过期秒数都由 `TokenServiceImpl` 直接读取配置，避免 `JwtCodec` 变成配置查询门面。
  - `refresh-token.ttl` 独立于 JWT 组件，避免 refresh token 生命周期继续泄漏到 `JwtCodec`。
  - 配置类移动到 `infra.security.config` 后，`infra.security.jwt` 可以继续保持技术组件边界。
- 与项目当前阶段的匹配点
  - 当前项目仍在学习和演进阶段，没有真实生产兼容包袱，可以直接把配置契约调整到更准确的形态。
  - 不新增依赖，不改数据库，不扩大到 refresh API 或 logout 吊销。
  - 通过测试覆盖配置绑定、JWT 编解码和登录集成路径，保持最小可验证闭环。

## 4. 替代方案
- 方案 A：继续使用 `JwtProperties`
  - 优点：改动最少。
  - 未采用原因：refresh token TTL 放在 JWT 配置里会持续误导边界，后续实现 refresh API 时容易继续污染 `JwtCodec`。
- 方案 B：拆成 `JwtProperties` + `RefreshTokenProperties`
  - 优点：access token 和 refresh token 分开管理。
  - 未采用原因：登录签发的是一个认证令牌对，拆成两个顶层配置类会让共同的 issuer、配置启用和文档说明更分散。
- 方案 C：使用 `TokenProperties` 和 `eventhub.security.token`
  - 优点：名称短。
  - 未采用原因：`token` 范围过宽，未来验证码 token、幂等 token、支付回调 token 都可能被误归入该配置。
- 方案 D：保留旧 `eventhub.security.jwt` 作为兼容前缀
  - 优点：降低真实部署迁移成本。
  - 未采用原因：当前项目没有生产兼容要求；双前缀会带来配置优先级、排障和教学解释成本。
- 方案 E：保留 `JwtCodec.accessTokenTtlSeconds()`
  - 优点：调用方少改一行。
  - 未采用原因：该方法只是配置查询，不属于 JWT 生成或解析能力；由 `TokenServiceImpl` 直接读取 `AuthTokenProperties` 与 refresh token TTL 获取方式更一致。

## 5. 测试与验证
- 跑了哪些测试
  - `mvn -Dtest=JwtCodecTest,AuthIntegrationTest,OpenApiProductionSecurityTest test`
  - `mvn test`
- 自动化验证结果
  - 目标测试通过：33 个用例，Failures: 0，Errors: 0，Skipped: 0。
  - 全量测试通过：69 个用例，Failures: 0，Errors: 0，Skipped: 0。
- 验证场景
  - `JwtCodecTest` 使用 `AuthTokenProperties` 生成和解析 access token。
  - `AuthIntegrationTest` 验证登录 token pair、auth session 创建和失败路径不受配置重命名影响。
  - `OpenApiProductionSecurityTest` 在 prod profile 下使用新配置项注入签名密钥，确认生产 profile 测试上下文可以启动。
  - 全量测试确认注册、`/api/v1/me`、admin API、系统接口和 mapper 测试未被误伤。
- 手工验证建议
  - 本地启动 dev profile 时无需额外配置，会使用 `application-dev.yml` 中的学习演示密钥。
  - 生产 profile 启动前需要设置：

```bash
export EVENTHUB_ACCESS_TOKEN_SIGNING_SECRET='replace-with-at-least-32-chars-secret'
```

## 6. 已知限制
- 旧配置前缀 `eventhub.security.jwt` 不再兼容；真实部署环境需要同步调整配置和环境变量。
- 历史文档中仍会保留旧命名作为阶段演进记录；当前配置边界以本实现说明和 ADR 为准。
- `issuer` 当前只用于 JWT access token，后续如果 refresh token 仍保持 opaque，该字段不应扩展到 refresh token 语义。
- 还没有实现 refresh API、logout 吊销或 denylist，配置边界调整只是为后续这些能力铺路。

## 7. 对后续版本的影响
- 对简历可用版的价值
  - 可以清楚解释为什么 refresh token 不属于 JWT 配置，以及为什么 opaque refresh token 的 TTL 应在认证令牌配置下表达。
  - 配置层、代码包边界和文档术语保持一致，更利于面试时讲清认证模块演进过程。
- 对微服务 / 云原生演进的影响
  - 如果未来拆出认证服务，`eventhub.security.auth-token` 可以自然迁移为认证服务内部配置。
  - access token 签名密钥独立命名后，更容易演进到 KMS、密钥轮换或非对称签名配置。
  - refresh token TTL 独立配置后，后续可以按环境、客户端类型或风险等级扩展策略。
