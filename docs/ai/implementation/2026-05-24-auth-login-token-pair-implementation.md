# 登录签发双 Token 与认证会话实现说明

## 1. 本次改动解决了什么问题

- 将登录成功路径从“只返回 access token”升级为“返回 access token + refresh token，并创建服务端认证会话”。
- 让登录响应具备后续 refresh API 所需的最小契约：`refreshToken`、`refreshExpiresIn`、`sessionId`。
- 让 access token 通过 `sid` 与 `auth_sessions` 关联，并通过 `jti` 为后续 denylist、审计和排查预留唯一 token 标识。
- 继续保持 refresh token 明文不落库，只在登录响应中返回一次。
- 保持注册、`/api/v1/me`、admin API 和当前 logout no-op 行为不变。

## 2. 改动内容
- 新增了什么
  - 新增 `JwtCodecTest`，覆盖 access token 必要 claim。
  - 新增设计文档：`docs/ai/design/2026-05-24-auth-login-token-pair-design.md`。
  - 新增 ADR：`docs/ai/adr/2026-05-24-access-token-claim-boundary.md`。
  - 新增本实现说明文档。
- 修改了什么
  - `LoginResponse` 增加 `refreshToken`、`refreshExpiresIn`、`sessionId`。
  - `AuthServiceImpl.login` 登录成功后生成 sessionId、opaque refresh token，创建 ACTIVE `auth_sessions`，再返回 token pair。
  - `TokenService` / `TokenServiceImpl` 支持带 sessionId 签发 access token，并生成高熵 opaque refresh token。
  - `JwtClaims` 增加 `tokenId`、`sessionId`、`tokenType`。
  - `JwtCodec` 签发 access token 时写入 `jti`、`sid`、`typ`，解析时校验这些 claim。
  - `AuthTokenProperties` 承载 access token TTL、access token 签名密钥和 refresh token TTL，默认 refresh token TTL 为 30 天。
  - `AuthIntegrationTest` 更新登录成功契约，并补充登录失败不创建 session 的断言。
  - 更新部分注释，避免继续把认证模型描述成完全无服务端会话。
- 删除了什么
  - 无。

## 3. 为什么这样设计
- 关键设计原因
  - 登录成功立即创建 `auth_sessions`，让 refresh token 从一开始就有服务端权威记录。
  - access token 通过 `sid` 关联 session，但认证过滤器仍只用 `sub` 回库加载最新用户状态和角色，避免权限快照滞后。
  - `jti` 为后续 access token denylist、审计日志和问题排查提供稳定标识。
  - `typ=access` 明确 token 类型边界，后续 refresh token 或其他 token 类型不会误入 access token 解析链路。
  - refresh token 使用 opaque token，客户端不依赖结构，服务端只保存哈希，更适合吊销、轮换和重放检测。
  - login 方法加事务，确保 session 创建失败时不会返回无法续期的 token pair。
- 与项目当前阶段的匹配点
  - 复用上一阶段已有 `auth_sessions` 表、`AuthSessionService` 和 `RefreshTokenHasher`。
  - 不新增表，不引入重量级依赖。
  - 保持 controller / service / mapper / domain 分层清晰。
  - 先完成登录最小闭环，把 refresh API、logout 吊销和 denylist 留到后续小步演进。

## 4. 替代方案
- 方案 A：refresh token 也使用 JWT
  - 优点：可以自带过期时间和部分上下文。
  - 未采用原因：客户端不需要解析 refresh token；长期 JWT 会暴露更多结构，吊销和轮换仍然要依赖服务端状态。
- 方案 B：access token 写入角色和权限
  - 优点：资源接口可少一次回库查询。
  - 未采用原因：用户禁用、角色调整会在 token 过期前滞后生效；当前项目更重视权限实时性和学习阶段的安全边界。
- 方案 C：只返回 refresh token，不创建 `auth_sessions`
  - 优点：改动更少。
  - 未采用原因：后续无法可靠实现 refresh token 吊销、设备会话管理和审计。
- 方案 D：本次一并实现 refresh API 和 logout 吊销
  - 优点：用户态闭环更完整。
  - 未采用原因：会引入 token 轮换、并发重放检测、状态机和 denylist 设计，超出本次明确范围。
- 方案 E：access token 加入 `ver`
  - 优点：后续可支持用户级全端失效。
  - 未采用原因：当前没有 `users.token_version` 字段；`auth_sessions.version` 是 refresh token 轮换乐观锁版本，不等价于用户级 token version。

## 5. 测试与验证
- 跑了哪些测试
  - `mvn -Dtest=JwtCodecTest,AuthIntegrationTest test`
  - `mvn test`
- 自动化验证结果
  - 目标测试通过：29 个用例，Failures: 0，Errors: 0，Skipped: 0。
  - 全量测试通过：69 个用例，Failures: 0，Errors: 0，Skipped: 0。
- 覆盖场景
  - 登录成功返回 `accessToken` 和 `refreshToken`。
  - 登录成功创建一条 `ACTIVE auth_sessions`。
  - refresh token 明文不落库，数据库保存的是 `RefreshTokenHasher` 生成的 hash。
  - access token 解析后包含 `jti`、`sid`、`typ=access`。
  - access token 的 `sid` 能关联到 `auth_sessions.session_id`。
  - 禁用用户登录失败。
  - 错误密码登录失败。
  - 不存在账号登录失败。
  - 登录失败不创建 auth session。
  - 注册、`/api/v1/me`、admin API、生产 OpenAPI 加固测试继续通过。
- API 手工验证示例

```bash
curl -s -X POST 'http://localhost:8080/api/v1/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{
    "usernameOrEmail": "admin",
    "password": "Admin123456"
  }'
```

期望响应中的 `data` 包含：

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

可用下面的 SQL 验证 session 已创建且 refresh token 明文不落库：

```sql
SELECT session_id, user_id, refresh_token_hash, status, refresh_expires_at
FROM auth_sessions
WHERE session_id = '<响应中的 sessionId>';
```

验证点：
- `status = 'ACTIVE'`。
- `refresh_token_hash` 不等于响应中的 `refreshToken`。
- access token 解码后 payload 中包含 `jti`、`sid`、`typ: "access"`。

## 6. 已知限制
- 本次没有实现 refresh API，因此 refresh token 还不能用于续期。
- 本次没有实现 logout 服务端吊销，`POST /api/v1/auth/logout` 仍保持原 no-op 语义。
- 本次没有接入 access token denylist，未过期 access token 仍不能被服务端即时吊销。
- 每次成功登录都会创建一条 session，后续需要过期清理任务或设备会话管理能力控制数据增长。
- 当前 refresh token hash 使用已有 SHA-256 方案，依赖 refresh token 本身具备足够高熵；后续可演进为 HMAC-SHA256 或增加服务端 pepper。

## 7. 对后续版本的影响
- 对简历可用版的价值
  - 认证模块从单 access token 演进到真实业务常见的 token pair + server-side session 模型。
  - 可以清晰讲解 refresh token 为什么使用 opaque token、为什么不明文落库、为什么 access token 不写权限。
  - 测试覆盖登录成功、失败、session 关联、JWT claim 和明文不落库，能体现安全与一致性意识。
- 对微服务 / 云原生演进的影响
  - `auth_sessions` 可以成为未来独立认证服务的核心会话表。
  - `sid` 和 `jti` 为网关鉴权、资源服务审计、Redis denylist 和安全事件追踪预留统一标识。
  - 后续可在 refresh API 中基于 `auth_sessions.version` 做乐观锁轮换，逐步演进到更完整的认证中心能力。
