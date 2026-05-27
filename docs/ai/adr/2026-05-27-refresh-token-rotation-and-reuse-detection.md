# ADR：Refresh Token 使用乐观条件更新完成轮换与重放检测

## 标题
Refresh Token 使用乐观条件更新完成轮换与重放检测

## 状态
- accepted

## 背景
登录接口已经签发 access token 与 opaque refresh token，并在 `auth_sessions` 中保存 refresh token 哈希。refresh token 是长期凭证，如果每次 refresh 后旧 token 仍然可用，一旦旧 token 泄漏，攻击者就可以持续换取新的 access token。

因此 refresh API 必须满足三个约束：

- 每次成功 refresh 都签发新的 refresh token，并替换服务端哈希。
- 同一个旧 refresh token 被并发提交时，最多只能有一个请求成功。
- 旧 refresh token 再次使用时必须失败，不能继续形成多条有效 token 链。

当前范围不新增表，不实现 logout denylist，不实现设备会话列表，也不引入 Redis 锁或完整 token family 模型。

## 决策
本次选择使用 MySQL `auth_sessions` 作为 refresh token 轮换的权威状态，并通过数据库乐观条件更新完成并发安全控制。

refresh 正常流程为：

1. 对客户端提交的 refresh token 做基础格式校验。
2. 对 refresh token 明文计算哈希。
3. 根据 `auth_sessions.refresh_token_hash` 查询会话。
4. 校验会话为 `ACTIVE`，且 `refresh_expires_at > now`。
5. 校验用户存在且状态为 `ENABLED`。
6. 生成新的 refresh token，并计算新哈希。
7. 使用旧哈希和旧版本执行条件更新：

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

只有受影响行数为 1 时 refresh 成功。受影响行数为 0 时，说明旧 token、旧版本、状态或过期条件至少一个已经不满足，统一返回 `AUTH-401`。

对旧 refresh token 重放的处理策略：

- 如果旧 token 已经被成功轮换，旧哈希不再存在于 `auth_sessions.refresh_token_hash`，再次提交会返回 `AUTH-401`。
- 本次不自动吊销已轮换后的新会话。
- 原因是当前 refresh token 是 opaque random token，轮换后不保存历史哈希，服务端无法可靠从旧 token 反向定位当前 session。

## 备选方案
- 方案 1：仅根据 `session_id` 更新 refresh token，不校验旧 hash 和 version。
- 方案 2：使用数据库悲观锁，例如 `SELECT ... FOR UPDATE`。
- 方案 3：使用 Redis 分布式锁串行化同一 session refresh。
- 方案 4：新增 refresh token 历史表或 token family 表。
- 方案 5：改用 refresh token JWT，在 token 内携带 sessionId 和 tokenId。

## 决策理由
选择乐观条件更新，原因如下：

- `WHERE refresh_token_hash = oldHash AND version = oldVersion` 能直接表达“只有当前旧 token 和旧版本仍有效时才能轮换”。
- 两个并发请求即使都读到旧会话快照，也只有第一个提交者能改变哈希和版本，第二个请求会因条件不匹配返回 0 行。
- 该方案复用现有 `auth_sessions.version` 字段和唯一约束，不需要新增表或外部依赖。
- 数据库是 refresh token 当前权威状态，使用数据库条件更新比应用内锁更接近最终一致性边界。
- 相比悲观锁，乐观更新在正常低冲突路径下更简单，事务持有时间更短。
- 相比 Redis 锁，乐观更新不依赖额外基础设施，也不会因为锁丢失而破坏最终一致性。
- 相比 token family 表，当前方案更符合本次最小可用闭环范围。
- 相比 refresh token JWT，opaque token 不暴露业务身份结构，长期凭证泄漏后的可利用信息更少。

## 影响
- 好处
  - refresh 成功后旧 refresh token 立即失效。
  - 并发 refresh 最多一个成功，避免一个旧 token 派生多条有效链路。
  - 服务端仍然只保存 refresh token 哈希，不保存明文。
  - 不破坏原 access token 认证过滤链路。
  - 不需要新增表、Redis 锁或重量级认证框架。
- 代价
  - 旧 refresh token 重放只能返回 401，当前不能自动吊销已轮换的新会话。
  - refresh API 每次需要访问数据库，不能做成完全无状态。
  - 条件更新失败的原因不会对外区分，排查时需要结合服务端日志和会话表状态。
- 后续可能需要调整的地方
  - 增加 refresh token 历史表或 token family 表，支持旧 token 重放时吊销整个会话链。
  - 给 refresh token hash 升级为 HMAC-SHA256 或加入服务端 pepper。
  - 在 logout、用户禁用、改密或管理员踢下线时批量吊销相关 `auth_sessions`。
  - 引入 access token denylist，补齐服务端即时吊销 access token 的能力。
  - 增加过期 session 清理任务和设备会话列表。
