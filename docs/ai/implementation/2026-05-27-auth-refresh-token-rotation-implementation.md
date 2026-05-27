# Refresh Token 轮换与重放检测实现说明

## 1. 本次改动解决了什么问题

- 新增 `POST /api/v1/auth/refresh`，让客户端可以用未过期 refresh token 换取新的 access token 和 refresh token。
- 每次 refresh 成功都会轮换 refresh token，并替换 `auth_sessions.refresh_token_hash`，旧 refresh token 立即失效。
- 使用 `refresh_token_hash + version` 条件更新保证并发 refresh 时最多一个请求成功。
- 对过期、篡改、旧 token 重放、session 已吊销、用户禁用统一返回 `AUTH-401`。
- 继续保持 refresh token 明文不落库，服务端只保存哈希。
- refresh 成功响应使用 `authorizationScheme`，与登录响应字段命名约定保持一致，不使用 `tokenType`。

## 2. 改动内容
- 新增了什么
  - 新增 `RefreshTokenRequest`，接收 refresh token 请求体。
  - 新增 `TokenPairResponse`，返回新的 token pair、`authorizationScheme`、会话标识和用户摘要。
  - 新增 `RefreshTokenParser`，校验当前 opaque refresh token 的基础格式。
  - 新增 `AuthSessionConcurrencyTest`，验证服务层并发轮换只有一个成功。
  - 新增 `RefreshTokenParserTest`，覆盖 refresh token 格式校验。
  - 新增设计文档、实现说明和 ADR。
- 修改了什么
  - `AuthController` 增加 `POST /api/v1/auth/refresh`。
  - `AuthService` / `AuthServiceImpl` 增加 refresh 业务流程。
  - `AuthSessionService` / `AuthSessionServiceImpl` 增加按 refresh token 查会话与条件轮换能力。
  - `AuthSessionMapper` / `AuthSessionMapper.xml` 增加 `rotateRefreshToken` 条件更新 SQL。
  - `SecurityConfig` 将 refresh endpoint 加入公开访问路径。
  - `AuthException` 增加通用 refresh token 认证失败工厂方法。
  - `AuthIntegrationTest` 覆盖 refresh 成功、连续刷新、旧 token 重放、并发刷新、过期、禁用用户、篡改和已吊销 session。
- 删除了什么
  - 无。
- 数据库迁移
  - 本次没有新增 Flyway migration。
  - 现有 `auth_sessions.session_id` 唯一约束、`refresh_token_hash` 唯一约束和 `version` 字段已经满足本次条件更新需求。

## 3. 为什么这样设计
- 关键设计原因
  - refresh token 继续使用 opaque random token，客户端不能从 token 中解析身份，服务端以数据库哈希匹配为准。
  - refresh token 明文只短暂存在于请求和响应调用栈，落库前统一通过 `RefreshTokenHasher` 转成哈希。
  - 并发安全交给数据库条件更新做最终判断，而不是依赖应用内锁。
  - refresh 失败统一返回 `AUTH-401`，避免暴露 token 是否曾经有效、会话是否存在或用户状态。
  - 成功响应沿用 `authorizationScheme`，避免和 JWT 内部 `typ=access` 的 `tokenType` 概念混淆。
- 与项目当前阶段的匹配点
  - 复用已有 `auth_sessions` 表和双 token 登录模型。
  - 不新增表、不引入 Redis 锁、不引入 OAuth2/OIDC 重依赖。
  - 保持 controller / service / mapper / domain 分层清晰，便于学习和后续演进。
  - 在当前单体项目中先把强一致 refresh 闭环做扎实。

## 4. 替代方案
- 方案 A：refresh token 使用 JWT
  - 优点：token 内可携带 sessionId、过期时间或 tokenId。
  - 未采用原因：长期凭证不需要客户端解析；JWT refresh token 仍需要服务端状态才能安全吊销和轮换。
- 方案 B：新增 refresh token 历史表或 token family 表
  - 优点：可以在旧 token 重放时定位原 session，并吊销整条 token 家族。
  - 未采用原因：本次范围明确不新增表；当前最小闭环先保证轮换和并发安全。
- 方案 C：使用 Redis 分布式锁串行化 refresh
  - 优点：可以减少数据库条件更新失败带来的并发冲突分支。
  - 未采用原因：锁不能替代数据库最终一致性防线；当前项目还没有把 Redis 作为认证强一致组件。
- 方案 D：旧 refresh token 重放时自动吊销当前 session
  - 优点：安全响应更激进。
  - 未采用原因：当前 opaque token 轮换后不保存历史哈希，旧 token 已无法可靠反向定位轮换后的 session；若在 token 中额外暴露 sessionId，又会引入被伪造或被滥用导致 DoS 的风险。

## 5. 测试与验证
- 跑了哪些测试
  - `mvn test -Dtest=RefreshTokenParserTest,RefreshTokenHasherTest,AuthSessionConcurrencyTest,AuthIntegrationTest`
  - `mvn test`
- 自动化验证结果
  - 目标测试通过：40 个用例，Failures: 0，Errors: 0，Skipped: 0。
  - 全量测试通过：81 个用例，Failures: 0，Errors: 0，Skipped: 0。
- 覆盖场景
  - refresh 成功返回新的 access token 和 refresh token。
  - refresh 响应包含 `authorizationScheme=Bearer`，不包含 `tokenType`。
  - 新 refresh token 可以再次刷新。
  - 旧 refresh token 使用失败并返回 `AUTH-401`。
  - 两个并发 refresh 请求只有一个成功。
  - 旧 refresh token 重放至少返回 401，当前已轮换 session 保持 ACTIVE。
  - refresh token 过期返回 `AUTH-401`。
  - 用户禁用后 refresh 返回 `AUTH-401`。
  - 篡改 refresh token 返回 `AUTH-401`。
  - session 已 `REVOKED` 时 refresh 返回 `AUTH-401`。
  - access token 原认证流程继续通过现有集成测试。
- API 手工验证示例

先登录并取出 refresh token：

```bash
LOGIN_RESPONSE=$(curl -s -X POST 'http://localhost:8080/api/v1/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{
    "usernameOrEmail": "admin",
    "password": "Admin123456"
  }')

REFRESH_TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.data.refreshToken')
```

执行 refresh：

```bash
REFRESH_RESPONSE=$(curl -s -X POST 'http://localhost:8080/api/v1/auth/refresh' \
  -H 'Content-Type: application/json' \
  -d "{
    \"refreshToken\": \"$REFRESH_TOKEN\"
  }")

echo "$REFRESH_RESPONSE" | jq '.data'
```

期望 `data` 结构：

```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "authorizationScheme": "Bearer",
  "expiresIn": 1800,
  "refreshExpiresIn": 2592000,
  "sessionId": "...",
  "user": {}
}
```

再次使用旧 refresh token 验证重放失败：

```bash
curl -i -X POST 'http://localhost:8080/api/v1/auth/refresh' \
  -H 'Content-Type: application/json' \
  -d "{
    \"refreshToken\": \"$REFRESH_TOKEN\"
  }"
```

期望 HTTP 401，响应体 `code` 为 `AUTH-401`。

使用新 refresh token 验证可以继续刷新：

```bash
NEW_REFRESH_TOKEN=$(echo "$REFRESH_RESPONSE" | jq -r '.data.refreshToken')

curl -s -X POST 'http://localhost:8080/api/v1/auth/refresh' \
  -H 'Content-Type: application/json' \
  -d "{
    \"refreshToken\": \"$NEW_REFRESH_TOKEN\"
  }" | jq '.data.authorizationScheme'
```

期望输出 `"Bearer"`。

注意：refresh endpoint 是公开 endpoint，手工验证时不要携带已过期的 `Authorization` header，避免 JWT 过滤器先处理过期 access token 并返回 401。

## 6. 已知限制
- 旧 refresh token 重放当前只返回 401，不自动吊销已轮换的新会话。
- 没有 refresh token 历史表，无法追踪 token family。
- 没有实现 logout denylist，未过期 access token 仍按原逻辑工作。
- 没有设备会话列表和过期 session 清理任务。
- `RefreshTokenParser` 绑定当前 32 字节随机数的 Base64 URL-safe 无 padding 外形，未来如果调整 token 生成策略，需要同步调整解析规则。
- 当前 refresh token hash 仍使用 SHA-256，依赖 refresh token 本身的高熵；后续可演进为 HMAC-SHA256 或增加服务端 pepper。

## 7. 对后续版本的影响
- 对简历可用版的价值
  - 认证模块具备真实项目常见的 access token + refresh token + server-side session 轮换闭环。
  - 可以清楚展示“不保存明文 refresh token”“乐观锁并发安全”“旧 token 重放失败”这些安全设计点。
  - 测试覆盖正常、失败、并发和重放场景，说明认证链路不是只做 happy path。
- 对微服务 / 云原生演进的影响
  - `auth_sessions` 可以继续作为未来独立认证服务的会话权威表。
  - `session_id`、`refresh_token_hash` 和 `version` 为后续 token family、设备管理、风控审计提供基础。
  - 未来可以把 access token 校验下沉到网关，把 refresh 和会话管理保留在 auth 服务内。
