# 认证令牌配置边界调整设计

## 1. 背景
- 登录流程已经从单一 JWT access token 演进为 access token + opaque refresh token。
- 原 `JwtProperties` 位于 `infra.security.jwt` 包下，配置前缀为 `eventhub.security.jwt`。
- 在新增 `refreshTokenTtl` 后，`JwtProperties` 实际承载了 refresh token 生命周期配置，但 refresh token 并不是 JWT。
- 如果继续沿用 `JwtProperties` 和 `eventhub.security.jwt`，会让代码包边界和配置语义都误导后续维护者。

## 2. 目标
- 将配置对象从 JWT 专属命名调整为认证令牌配置命名。
- 将配置前缀从 `eventhub.security.jwt` 调整为能表达令牌对的前缀。
- 将 access token 与 refresh token 配置拆成清晰子结构。
- 将配置类移动到更合适的包下，避免放在 `jwt` 技术组件目录中。
- 保持登录 API 契约、JWT claim、auth session 创建逻辑不变。
- 成功标准：
  - Spring Boot 能绑定新的配置路径。
  - `JwtCodec` 仍只负责 access JWT 的签发和解析。
  - `TokenServiceImpl` 从认证令牌配置读取 refresh token TTL。
  - 相关测试继续通过。

## 3. 非目标
- 不实现 refresh API。
- 不实现 logout 吊销或 access token denylist。
- 不改数据库结构。
- 不改变登录响应字段。
- 不为旧 `eventhub.security.jwt` 配置前缀做兼容别名。

## 4. 影响范围
- 配置属性类：
  - `JwtProperties` 重命名为 `AuthTokenProperties`。
  - 包路径从 `com.eventhub.infra.security.jwt` 移到 `com.eventhub.infra.security.config`。
- 应用配置：
  - `eventhub.security.jwt` 调整为 `eventhub.security.auth-token`。
  - `access-token-ttl` 调整为 `access-token.ttl`。
  - `refresh-token-ttl` 调整为 `refresh-token.ttl`。
  - JWT 签名密钥从 `secret` 调整为 `access-token.signing-secret`。
- 依赖组件：
  - `JwtCodec`
  - `TokenServiceImpl`
  - `SecurityConfig`
- 测试配置：
  - `application-test.yml`
  - 生产 profile 相关测试属性。
- 文档：
  - 新增本设计文档。
  - 更新登录 token pair 设计与实现说明中的配置类描述。
  - 新增 ADR 记录配置边界决策。

## 5. 领域建模
- `AuthTokenProperties`
  - 表示认证令牌对的配置入口，不是业务领域实体。
  - 顶层字段：
    - `issuer`：当前用于 JWT access token 签发方。
    - `accessToken`：access token 配置。
    - `refreshToken`：refresh token 配置。
- `AccessToken`
  - `ttl`：access token 有效期。
  - `signingSecret`：JWT access token 签名密钥。
- `RefreshToken`
  - `ttl`：opaque refresh token 有效期。

关键关系：
- `JwtCodec` 在签发默认有效期 access token 时读取 `issuer`、`accessToken.ttl` 和 `accessToken.signingSecret`，但不对外提供 TTL 查询方法。
- `TokenServiceImpl` 直接读取 `accessToken.ttl` 和 `refreshToken.ttl`，用于组织登录响应中的过期秒数。

## 6. API 设计
- 本次不调整外部 HTTP API。
- `POST /api/v1/auth/login` 响应结构保持上一阶段 token pair 契约：

```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "tokenType": "Bearer",
  "expiresIn": 1800,
  "refreshExpiresIn": 2592000,
  "sessionId": "...",
  "user": {}
}
```

- 内部配置契约调整为：

```yaml
eventhub:
  security:
    auth-token:
      issuer: eventhub-backend
      access-token:
        ttl: 30m
        signing-secret: ${EVENTHUB_ACCESS_TOKEN_SIGNING_SECRET}
      refresh-token:
        ttl: 30d
```

## 7. 数据设计
- 本次不新增表结构、索引或迁移脚本。
- `auth_sessions.refresh_expires_at` 仍由 refresh token TTL 计算得到。
- 配置项重命名不影响已有业务数据。

## 8. 关键流程
- 应用启动：
  1. Spring Boot 绑定 `eventhub.security.auth-token`。
  2. Bean Validation 校验 issuer、access token 签名密钥和 TTL。
  3. `SecurityConfig` 注册 `AuthTokenProperties`。
- 签发 access token：
  1. `TokenServiceImpl` 调用 `JwtCodec`。
  2. `JwtCodec` 从 `AuthTokenProperties.accessToken` 读取默认签发 TTL 和签名密钥。
  3. JWT claim 签发行为不变。
- 登录创建 refresh token：
  1. `TokenServiceImpl` 生成 opaque refresh token。
  2. access token 和 refresh token 的响应过期秒数均由 `TokenServiceImpl` 直接从 `AuthTokenProperties` 读取。
  3. `AuthServiceImpl` 按 TTL 计算 `refresh_expires_at`。

## 9. 并发 / 幂等 / 缓存
- 本次仅调整配置绑定和代码边界，不改变并发语义。
- 登录接口仍不是幂等接口。
- 不引入缓存。
- 配置读取在应用启动后由 Spring Bean 持有，运行期不做动态刷新。

## 10. 权限与安全
- 配置命名区分 access token 与 refresh token，可以降低后续误把 refresh token 当 JWT 处理的风险。
- 签名密钥移动到 `access-token.signing-secret`，明确该密钥只服务 JWT access token。
- 生产环境环境变量改为 `EVENTHUB_ACCESS_TOKEN_SIGNING_SECRET`，表达更准确。
- 不提供旧配置前缀兼容，是为了避免同一含义存在两套配置来源造成优先级和排障复杂度。

## 11. 测试策略
- 单元测试：
  - 更新 `JwtCodecTest`，使用 `AuthTokenProperties` 构造测试配置。
- 集成测试：
  - 更新 `application-test.yml` 新配置路径。
  - 运行 `AuthIntegrationTest`，确认登录 token pair 和 session 创建不受影响。
- 生产 profile 回归：
  - 更新 `OpenApiProductionSecurityTest` 中的测试属性，确认 prod profile 启动仍能绑定签名密钥。
- 全量回归：
  - 运行 `mvn test`。

## 12. 风险与替代方案
- 当前方案风险：
  - 配置前缀和生产环境变量名发生破坏性变化，真实部署环境需要同步调整。
  - 历史文档中仍可能出现旧命名，需要通过新增 ADR 和当前实现说明解释演进过程。
- 备选方案 A：保留 `JwtProperties`，只把 refresh TTL 挪到单独 `RefreshTokenProperties`。
  - 没有采用，因为 access/refresh token 是登录令牌对，两个配置分散后反而降低整体可读性。
- 备选方案 B：类名改为 `TokenProperties`，前缀使用 `eventhub.security.token`。
  - 没有采用，因为 `token` 范围过宽，容易与一次性邮箱 token、支付 token、幂等 token 等未来概念混淆。
- 备选方案 C：提供旧 `eventhub.security.jwt` 前缀兼容。
  - 没有采用，因为项目仍处学习和演进阶段，当前没有生产兼容包袱；保留两套配置会增加配置优先级和排障成本。
