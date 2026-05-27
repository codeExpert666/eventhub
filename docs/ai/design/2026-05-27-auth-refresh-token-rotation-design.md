# Refresh Token 轮换与重放检测设计

## 1. 背景
- 登录接口已经返回 access token 与 opaque refresh token。
- refresh token 明文只返回给客户端，服务端仅在 `auth_sessions.refresh_token_hash` 保存哈希。
- 当前还缺少 refresh API，access token 过期后客户端无法在不重新输入密码的情况下续期。
- refresh token 属于长期凭证，续期时必须轮换：每次成功 refresh 后，旧 refresh token 立即失效，服务端只保存新 token 的哈希。

## 2. 目标
- 新增 `POST /api/v1/auth/refresh`。
- 成功 refresh 时签发新的 access token 与新的 refresh token，并替换 `auth_sessions.refresh_token_hash`。
- 使用乐观条件更新保证同一个旧 refresh token 并发提交时最多一个请求成功。
- 对过期、篡改、重放、会话已吊销、用户禁用等场景统一返回 `AUTH-401`。
- 不保存 refresh token 明文，不影响现有 access token 请求认证逻辑。

## 3. 非目标
- 不实现 logout denylist。
- 不实现设备会话列表。
- 不实现登录限流。
- 不新增 refresh token 历史表。
- 不在本次引入 Redis、分布式锁或 token 家族模型。
- 不把 refresh token 改成 JWT；继续使用当前 opaque random token。

## 4. 影响范围
- 涉及模块：
  - `modules.auth.controller.AuthController`
  - `modules.auth.service.AuthService`
  - `modules.auth.service.AuthSessionService`
  - `modules.auth.service.RefreshTokenHasher`
  - `modules.auth.service.RefreshTokenParser`
  - `modules.auth.mapper.AuthSessionMapper`
  - `infra.security.config.SecurityConfig`
  - auth 集成测试与会话并发测试
- 涉及表 / 缓存 / 外部接口：
  - 表：复用 `auth_sessions`。
  - 索引：当前 `session_id` 唯一约束、`refresh_token_hash` 唯一约束与 `version` 字段已经满足本次条件更新需求，暂不新增迁移。
  - 缓存：不涉及。
  - 外部接口：新增公开 refresh endpoint。

## 5. 领域建模
- 核心实体：
  - `AuthSessionEntity`：服务端认证会话，是 refresh token 哈希、状态、过期时间和乐观锁版本的权威记录。
  - `RefreshTokenRequest`：客户端提交 refresh token 明文。
  - `TokenPairResponse`：refresh 成功后返回新的 token pair、会话标识和用户摘要。
  - `RefreshTokenParser`：校验 opaque refresh token 的基础格式，不解析或信任任何业务身份信息。
- 实体关系：
  - 一个 `AuthSessionEntity` 关联一个用户和当前唯一有效 refresh token hash。
  - access token 的 `sid` claim 指向 `auth_sessions.session_id`。
  - refresh token 明文只短暂存在于请求与响应调用栈，落库前必须哈希。
- 关键状态：
  - `ACTIVE`：会话有效，且 `refresh_expires_at > now` 时才允许 refresh。
  - `REVOKED`：会话已吊销，refresh 必须失败。
  - 过期状态由 `refresh_expires_at` 派生，不新增 `EXPIRED` 状态。

## 6. API 设计
- 接口列表：
  - `POST /api/v1/auth/refresh`
- 请求参数：

```json
{
  "refreshToken": "..."
}
```

- 成功响应：

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
  - `authorizationScheme` 沿用登录响应字段命名，表示 HTTP `Authorization` 头授权方案。
  - 不使用 `tokenType`，避免与 JWT 内部 `typ=access` claim 混淆。
- 错误码 / 异常场景：
  - refresh token 过期：`AUTH-401`
  - refresh token 篡改：`AUTH-401`
  - refresh token 重放：`AUTH-401`
  - session 已撤销：`AUTH-401`
  - 用户禁用：`AUTH-401`
  - 请求体缺失或字段为空：`COMMON-400`

## 7. 数据设计
- 表结构调整：无。
- 索引设计：
  - `uk_auth_sessions_session_id`：支持按会话唯一定位并执行条件更新。
  - `uk_auth_sessions_refresh_token_hash`：支持用客户端提交的 refresh token 哈希定位当前会话，并保证一个 refresh token 只对应一个会话。
  - `version` 不单独建索引；条件更新会先通过唯一 `session_id` 命中单行，再校验 `version` 与旧哈希。
- 唯一约束：
  - `session_id` 唯一。
  - `refresh_token_hash` 唯一。
- 数据一致性考虑：
  - refresh 成功与服务端哈希替换必须在同一事务内完成。
  - 新 access token 在条件更新成功后签发，避免返回一个服务端未完成轮换的 token pair。

## 8. 关键流程
- 正常流程：
  1. Controller 接收 `RefreshTokenRequest`。
  2. `RefreshTokenParser` 校验 refresh token 基础格式。
  3. `RefreshTokenHasher` 对明文 refresh token 计算哈希。
  4. 根据旧哈希查询 `auth_sessions`。
  5. 校验会话存在、状态为 `ACTIVE`、未超过 `refresh_expires_at`。
  6. 查询用户并确认用户仍为 `ENABLED`。
  7. 生成新的 refresh token，并计算新哈希与新过期时间。
  8. 使用 `session_id + oldHash + oldVersion + ACTIVE + 未过期` 做条件更新。
  9. 更新成功后签发新的 access token，返回新的 token pair。
- 异常流程：
  - 格式非法、哈希查不到、状态不是 `ACTIVE`、已过期、用户不存在或禁用、条件更新失败，均转换为通用 refresh 认证失败。
- 状态流转：
  - 成功 refresh：`ACTIVE -> ACTIVE`，`version + 1`，`refresh_token_hash` 替换为新哈希。
  - 已吊销会话：`REVOKED` 保持不变。
  - 本次不新增自动过期状态流转。

## 9. 并发 / 幂等 / 缓存
- 是否有超卖风险：不涉及库存。
- 如何防重复提交：
  - 使用数据库条件更新作为最终防线：

```sql
UPDATE auth_sessions
SET refresh_token_hash = :newHash,
    refresh_expires_at = :newRefreshExpiresAt,
    last_refreshed_at = :now,
    last_seen_at = :now,
    version = version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE session_id = :sessionId
  AND refresh_token_hash = :oldHash
  AND version = :oldVersion
  AND status = 'ACTIVE'
  AND refresh_expires_at > :now
```

  - 两个并发请求即使都读到同一个旧会话，也只有一个能成功更新；另一个因受影响行数为 0 返回 `AUTH-401`。
- 缓存放在哪里，为什么：
  - 不引入缓存。refresh token 是强一致认证凭证，当前阶段以 MySQL 作为权威记录更容易保证正确性。

## 10. 权限与安全
- 哪些角色能访问：
  - `POST /api/v1/auth/refresh` 对匿名请求放行，因为 access token 可能已经过期。
- 鉴权与鉴别约束：
  - refresh 身份来源只来自 refresh token 哈希匹配到的服务端会话。
  - 不从 refresh token 中解析用户 ID、角色或 session ID。
  - 用户禁用时返回 `AUTH-401`，避免 refresh 接口暴露账号状态细节。
  - 篡改和重放统一返回 `AUTH-401`，降低可枚举性。
  - 当前 opaque token 轮换后不保存历史哈希；旧 refresh token 重放无法可靠反向定位原 session，因此本次至少返回 401，不自动吊销当前新会话。

## 11. 测试策略
- 单元测试：
  - `RefreshTokenParser` 校验合法 opaque token 与非法格式。
- 集成测试：
  - refresh 成功返回新 token pair，且响应字段为 `authorizationScheme`。
  - 新 refresh token 可以再次刷新。
  - 旧 refresh token 使用失败。
  - 过期 refresh token 返回 401。
  - 禁用用户 refresh 返回 401。
  - 篡改 refresh token 返回 401。
  - session 已 `REVOKED` 时 refresh 返回 401。
  - access token 原认证流程保持可用。
- 并发验证：
  - 两个并发 refresh 请求使用同一个旧 refresh token，只能一个成功。
  - 服务层/Mapper 条件更新测试验证旧哈希与旧版本防线。
- 接口验证：
  - 在实现说明中写入登录、refresh、旧 token 重放的 curl 示例。

## 12. 风险与替代方案
- 当前方案的风险：
  - 不保存历史哈希时，旧 refresh token 重放只能识别为无效凭证，不能定位并吊销已经轮换后的会话。
  - 公开 refresh endpoint 如果携带过期 access token，JWT 过滤器仍可能先返回 401；手工验证应不带 `Authorization` 头调用 refresh。
- 备选方案：
  - 使用 refresh token JWT，在 token 内写入 sessionId 和 tokenId。
  - 新增 refresh token 历史表或 token family 表，记录已使用 token 哈希并在重放时吊销整个会话链。
  - 使用 Redis 分布式锁串行化同一 session refresh。
- 为什么不选备选方案：
  - refresh token JWT 会让长期凭证携带可解析元数据，和当前 opaque token 设计方向不一致。
  - 历史表/token family 更适合后续安全增强阶段，本次范围明确不新增表。
  - Redis 锁不能替代数据库最终一致性防线，并且当前项目尚未把 Redis 作为认证强一致组件。
