# ADR：认证令牌配置不再归入 JWT 专属配置

## 标题
认证令牌配置不再归入 JWT 专属配置

## 状态
- accepted

## 背景
登录成功路径已经签发 access token 和 refresh token，并创建服务端认证会话。access token 当前使用 JWT，refresh token 使用 opaque token 且服务端只保存哈希。

原配置对象 `JwtProperties` 与配置前缀 `eventhub.security.jwt` 只适合单 access token 阶段。新增 refresh token TTL 后，如果继续放在 JWT 配置下，会让后续开发误以为 refresh token 也是 JWT，或误把 refresh token 的生命周期放进 JWT 编解码组件中。

## 决策
本次选择：

- 将 `JwtProperties` 重命名为 `AuthTokenProperties`。
- 将类移动到 `com.eventhub.infra.security.config`。
- 将配置前缀调整为 `eventhub.security.auth-token`。
- 将配置结构拆成：
  - `issuer`
  - `access-token.ttl`
  - `access-token.signing-secret`
  - `refresh-token.ttl`
- `JwtCodec` 继续留在 `infra.security.jwt`，只负责 access JWT 签发、解析和验签，不对外提供 TTL 查询方法。
- `TokenServiceImpl` 从 `AuthTokenProperties` 直接读取 access token TTL 和 refresh token TTL。
- 不保留旧 `eventhub.security.jwt` 前缀兼容。

## 备选方案
- 方案 1：继续使用 `JwtProperties` 和 `eventhub.security.jwt`。
- 方案 2：新增 `RefreshTokenProperties`，保留 `JwtProperties`。
- 方案 3：改为 `TokenProperties` 和 `eventhub.security.token`。
- 方案 4：改为 `AuthTokenProperties` 和 `eventhub.security.auth-token`。

## 决策理由
选择方案 4，原因如下：

- `AuthTokenProperties` 能表达“认证令牌对”的边界，而不是某一种具体编码格式。
- `auth-token` 比 `jwt` 更准确，也比宽泛的 `token` 更不容易与其他业务 token 混淆。
- access token 与 refresh token 以子配置区分，能直观看出签名密钥只属于 JWT access token。
- 配置类移动到 `infra.security.config` 后，`infra.security.jwt` 包可以继续专注 JWT 编解码技术能力。
- 当前项目没有生产兼容包袱，不保留旧前缀可以避免同一配置存在两套来源。

## 影响
- 好处：
  - 代码命名、包路径和配置前缀与当前 token pair 模型一致。
  - refresh token opaque 设计在配置层也被明确表达。
  - 后续 refresh API、logout 吊销和 token 轮换接入时，职责边界更清晰。
- 代价：
  - 部署环境需要将 `EVENTHUB_JWT_SECRET` 调整为 `EVENTHUB_ACCESS_TOKEN_SIGNING_SECRET`。
  - 历史文档中关于旧配置名的描述需要通过当前 ADR 理解为阶段性演进记录。
- 后续可能需要调整的地方：
  - 如果未来出现邮箱验证码 token、幂等 token 等更多 token 类型，可以继续在配置层拆出更明确的业务前缀，而不是把所有 token 聚合到一个超大配置类。
  - 如果项目进入真实生产兼容阶段，再评估是否需要配置迁移指南或短期别名兼容。
