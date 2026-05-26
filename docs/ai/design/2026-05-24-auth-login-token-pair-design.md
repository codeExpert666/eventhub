# 登录签发双 Token 与认证会话设计

## 1. 背景
- 当前 `POST /api/v1/auth/login` 登录成功后只返回 access token、token 类型、过期秒数和用户摘要。
- 上一阶段已经新增 `auth_sessions` 表、`AuthSessionService` 和 `RefreshTokenHasher`，但登录流程尚未接入服务端认证会话。
- 后续要实现 refresh API、服务端 logout 吊销和设备会话管理，需要在登录成功时创建一条服务端会话，并把 access token 与该会话关联起来。
- 本次只改登录成功路径和 JWT claim，不改变注册、`/api/v1/me`、admin API 和现有 access token 鉴权过滤器的核心行为。

## 2. 目标
- 登录成功后创建一条 `ACTIVE` 的 `auth_sessions` 记录。
- 登录成功响应增加 `refreshToken`、`refreshExpiresIn` 和 `sessionId`。
- access token 增加 `jti`、`sid`、`typ=access` claim。
- refresh token 只在登录响应中明文返回一次，数据库只保存 `refresh_token_hash`。
- 账号不存在和密码错误继续统一返回认证失败。
- 禁用用户仍不能登录。
- 成功标准：
  - 原登录成功响应字段语义保持不变。
  - 新登录响应字段完整。
  - `sid` 能关联到 `auth_sessions.session_id`。
  - 登录失败不会创建认证会话。
  - JWT claim 可被测试解析和校验。

## 3. 非目标
- 不实现 refresh API。
- 不实现 logout 服务端吊销。
- 不实现 access token denylist 校验。
- 不新增数据库表或迁移脚本。
- 不把角色、权限、邮箱、用户名写入 access token。
- 不实现 refresh token 轮换、重放检测或设备会话列表。

## 4. 影响范围
- Auth 模块：
  - `AuthServiceImpl` 登录成功路径接入 `AuthSessionService`。
  - `LoginResponse` 增加双 token 响应字段。
  - `TokenService` / `TokenServiceImpl` 扩展 access token 签发和 refresh token 生成能力。
- JWT 基础设施：
  - `JwtClaims` 扩展 `jti`、`sid`、`typ`。
  - `JwtCodec` 签发和解析 access token 时写入、校验并返回这些 claim。
  - `AuthTokenProperties` 承载 access token TTL、access token 签名密钥和 opaque refresh token TTL。
- 数据库：
  - 复用已有 `auth_sessions` 表。
  - 不新增表结构、索引或迁移。
- 测试：
  - 更新认证集成测试。
  - 新增 JWT claim 单元测试。
- 文档：
  - 新增设计文档、实现说明。
  - 新增 access token claim 边界 ADR。

## 5. 领域建模
- `AuthSession`
  - 表示一次服务端认证会话，通常对应一个浏览器、移动端或设备登录态。
  - 登录成功时创建，初始状态为 `ACTIVE`。
  - 通过 `sessionId` 与 access token 的 `sid` claim 关联。
- `AccessToken`
  - 短期访问凭证，仍使用 JWT。
  - 保存最小认证声明：`sub`、`jti`、`sid`、`typ`、`iss`、`iat`、`exp`。
  - 不保存角色和权限，后续请求仍按 `sub` 回库加载最新用户状态和角色。
- `RefreshToken`
  - 长期续期凭证，使用 opaque token。
  - 客户端不需要解析其内容。
  - 服务端只保存哈希值，通过已有 `RefreshTokenHasher` 落库。

关键状态：

```text
ACTIVE
  | 后续 logout / 管理员踢下线 / 用户禁用 / 风控
  v
REVOKED
```

说明：
- 本次只创建 `ACTIVE` 会话。
- 过期仍由 `refresh_expires_at` 派生，不新增 `EXPIRED` 状态。
- 暂不使用 access token denylist，因此已签发 access token 在过期前仍由 JWT 自身有效期控制。

## 6. API 设计
- 接口：`POST /api/v1/auth/login`
- 请求参数保持不变：

```json
{
  "usernameOrEmail": "alice",
  "password": "Password123"
}
```

- 成功响应中 `data` 调整为：

```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "authorizationScheme": "Bearer",
  "expiresIn": 1800,
  "refreshExpiresIn": 2592000,
  "sessionId": "...",
  "user": {
    "id": 1,
    "username": "alice",
    "email": "alice@example.com",
    "status": "ENABLED",
    "roles": ["USER"]
  }
}
```

- 字段说明：
  - `accessToken`：JWT access token，后续访问受保护接口时放入 `Authorization: Bearer ...`。
  - `refreshToken`：opaque refresh token，仅登录响应明文返回一次。
  - `authorizationScheme`：HTTP Authorization 授权方案，保持 `Bearer`。
  - `expiresIn`：access token 有效秒数，保持原语义。
  - `refreshExpiresIn`：refresh token 有效秒数，默认 30 天。
  - `sessionId`：服务端认证会话标识，对应 `auth_sessions.session_id`。
  - `user`：当前登录用户摘要，保持原语义。

错误场景：
- 账号不存在：`401 AUTH-401`，消息为账号或密码错误。
- 密码错误：`401 AUTH-401`，消息为账号或密码错误。
- 用户禁用：`403 AUTH-403`，消息为用户已被禁用。
- 创建 session 失败：作为服务端异常处理，登录整体失败并回滚，不返回 refresh token。

## 7. 数据设计
- 本次不新增迁移。
- 复用已有 `auth_sessions` 表：
  - `session_id`：写入登录响应和 access token `sid`。
  - `user_id`：登录用户 ID。
  - `refresh_token_hash`：refresh token 哈希值，不保存明文。
  - `status`：创建时为 `ACTIVE`。
  - `issued_at`：登录签发时间。
  - `refresh_expires_at`：refresh token 过期时间。
  - `last_seen_at`：创建时使用 `issued_at`。
  - `version`：创建时为 0，后续 refresh token 轮换时使用。
- 约束与索引沿用已有设计：
  - `uk_auth_sessions_session_id`
  - `uk_auth_sessions_refresh_token_hash`
  - `idx_auth_sessions_user_id`
  - `idx_auth_sessions_status`
  - `idx_auth_sessions_refresh_expires_at`

一致性考虑：
- 登录成功路径加事务，确保用户校验、access token 生成、refresh token hash 落库和响应构造在业务语义上形成一个闭环。
- 如果 session 插入失败，不返回 token pair，避免客户端拿到无法续期的 refresh token。
- refresh token 明文只在调用栈和 HTTP 响应中短暂存在。

## 8. 关键流程
- 正常流程：
  1. 归一化用户名或邮箱。
  2. 查询用户，不存在则抛统一认证失败。
  3. 校验密码，错误则抛统一认证失败。
  4. 校验用户状态，禁用则拒绝登录。
  5. 构造 `UserInfo`。
  6. 生成 `sessionId`、refresh token 明文和 access token `jti`。
  7. 签发带 `jti`、`sid`、`typ=access` 的 access token。
  8. 调用 `AuthSessionService.createActiveSession(...)` 保存 `ACTIVE` session 和 refresh token hash。
  9. 响应返回 access token、refresh token、过期秒数、sessionId 和用户摘要。
- 异常流程：
  - 用户不存在或密码错误：登录失败，不创建 session。
  - 用户禁用：登录失败，不创建 session。
  - session 创建失败：事务回滚，不返回 token pair。
- 状态流转：
  - 本次只创建 `ACTIVE`。
  - `REVOKED` 会在后续 logout 或管理能力中接入。

## 9. 并发 / 幂等 / 缓存
- 并发：
  - 同一用户多次并发登录可以创建多条不同的 `ACTIVE` session，符合多设备登录语义。
  - `session_id` 和 `refresh_token_hash` 唯一约束作为极低概率碰撞的最终防线。
- 幂等：
  - 登录接口不是幂等接口，每次成功登录都会产生新的 token pair 和 session。
  - 登录失败不产生 session，避免失败重试污染会话表。
- 缓存：
  - 本次不引入 Redis。
  - 后续 Redis 适合放 access token denylist、会话状态短缓存和 refresh 重放快速拦截。
  - MySQL 仍作为认证会话权威记录。

## 10. 权限与安全
- `POST /api/v1/auth/login` 继续匿名可访问。
- access token 仍不写角色/权限：
  - 当前 `JwtAuthenticationFilter` 会解析 `sub` 后回库加载最新用户状态和角色。
  - 这样禁用用户、角色变更可以在下一次请求时生效，而不是等待 JWT 过期。
  - 角色和权限写入 JWT 会让权限变成短期快照，提升吊销和变更复杂度。
- refresh token 使用 opaque token：
  - 客户端不需要读取 refresh token 内部信息。
  - 服务端可以只保存 hash，降低数据库泄漏后的凭证风险。
  - 后续轮换、吊销、重放检测都围绕服务端 session 进行，不受 token claim 结构约束。
- access token `typ=access`：
  - 为后续 refresh token 或其他 token 类型预留边界。
  - 解析 access token 时要求 `typ` 必须为 `access`，避免 token 类型混用。
- 本次不校验 denylist：
  - 符合明确范围。
  - logout 服务端吊销和 denylist 后续单独设计。

## 11. 测试策略
- 单元测试：
  - `JwtCodecTest`
  - 验证签发的 access token 包含 `jti`、`sid`、`typ=access`。
  - 验证缺失或错误 `typ` 的 token 被拒绝。
  - 验证缺失 `sid` 或 `jti` 的 token 被拒绝。
- 集成测试：
  - `AuthIntegrationTest`
  - 登录成功返回 access token 和 refresh token。
  - 登录成功创建一条 `ACTIVE auth_sessions`。
  - refresh token 明文不落库。
  - access token 包含 `jti`、`sid`、`typ`。
  - `sid` 能关联到 auth session。
  - 禁用用户登录失败。
  - 错误密码登录失败。
  - 不存在账号登录失败。
  - 登录失败不创建 auth session。
  - 注册、`/api/v1/me`、admin API 行为保持不变。
- 接口手工验证：
  - 使用 Swagger 或 curl 调用登录接口，确认响应字段完整。
  - 查询数据库确认 `auth_sessions.refresh_token_hash` 不等于响应中的 `refreshToken`。

## 12. 风险与替代方案
- 当前方案风险：
  - access token 仍未接入 denylist，logout 后未过期 token 不能立即失效。
  - refresh token TTL 较长，必须依赖高熵随机值和哈希落库降低泄漏风险。
  - 每次登录都会创建 session，后续需要过期清理任务控制表增长。
- 备选方案 A：refresh token 也使用 JWT。
  - 没有采用，因为客户端不需要解析 refresh token；JWT refresh token 会暴露更多结构和长期 claim，吊销仍要依赖服务端状态。
- 备选方案 B：access token 写入角色和权限。
  - 没有采用，因为当前项目要求用户禁用和角色变更尽快生效，回库加载最新权限更适合现阶段。
- 备选方案 C：不创建 `auth_sessions`，只返回 refresh token。
  - 没有采用，因为后续无法可靠吊销、审计和管理某个设备会话。
- 备选方案 D：本次顺手实现 refresh API 和 logout 吊销。
  - 没有采用，因为会扩大 API、状态机、并发轮换和 denylist 范围，不符合本次最小闭环。
