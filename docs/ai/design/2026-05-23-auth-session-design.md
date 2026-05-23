# 服务端认证会话模型设计

## 1. 背景
- 当前 EventHub 认证链路只有无状态 access token。
- `POST /api/v1/auth/logout` 目前是 no-op，服务端无法主动吊销已签发 token。
- 后续需要演进到 access token + refresh token 双 token 模型，并支持 refresh token 轮换、server-side logout、单设备踢下线和全端失效。
- 这些能力都需要一份服务端权威会话记录，否则 refresh token 无法可靠吊销，设备会话也无法被管理和审计。

## 2. 目标
- 新增 `auth_sessions` 表，保存服务端认证会话和 refresh token 哈希。
- 新增会话持久化模型、状态枚举、MyBatis Mapper 和应用服务骨架。
- 新增 refresh token 哈希工具，确保数据库不保存 refresh token 明文。
- 新增测试覆盖迁移、插入、查询、唯一约束、状态更新、哈希稳定性和并发创建不同会话。
- 成功标准：
  - Flyway 可以从空库迁移到 V3。
  - 现有注册、登录、JWT 鉴权、logout no-op 行为不变。
  - 服务端会话数据模型能支撑后续 refresh/logout 演进。

## 3. 非目标
- 不修改 `LoginResponse`。
- 不在登录接口返回 refresh token。
- 不新增 refresh API。
- 不修改 `JwtAuthenticationFilter` 行为。
- 不修改当前 logout no-op 语义。
- 不引入 Redis 会话缓存或 access token denylist。
- 不实现设备会话列表、管理员踢下线或全端失效 API。

## 4. 影响范围
- 数据库：
  - 新增 `auth_sessions` 表。
  - 新增会话标识唯一约束、refresh token 哈希唯一约束、用户/状态/过期时间索引。
- Auth 模块：
  - 新增 `AuthSessionEntity`。
  - 新增 `AuthSessionStatus`。
  - 新增 `AuthSessionMapper` 与 XML SQL 映射。
  - 新增 `RefreshTokenHasher`。
  - 新增 `AuthSessionService` / `AuthSessionServiceImpl` 骨架。
- 测试：
  - 新增 Mapper 集成测试。
  - 新增 refresh token hash 单元测试。
- 不涉及 Controller、HTTP API、Security Filter、Redis 或外部接口。

## 5. 领域建模
- `AuthSession`
  - 表示一次服务端认证会话，通常对应一个浏览器、移动端或设备登录态。
  - 核心字段：`sessionId`、`userId`、`refreshTokenHash`、`status`、`issuedAt`、`refreshExpiresAt`、`version`。
- `AuthSessionStatus`
  - `ACTIVE`：会话有效，后续 refresh token 校验必须同时满足未过期。
  - `REVOKED`：会话已吊销，不允许继续 refresh。
- `RefreshTokenHasher`
  - 负责把 refresh token 明文转换为不可逆哈希。
  - 明文 token 只短暂存在于调用栈和响应链路中，不进入数据库。

关键状态：

```text
ACTIVE
  | logout / 管理员踢下线 / 用户禁用 / 安全风控
  v
REVOKED
```

说明：
- 不新增 `EXPIRED` 状态。
- refresh token 是否过期由 `refresh_expires_at < now` 派生，避免状态字段和时间字段表达同一事实。

## 6. API 设计
- 本次不新增 HTTP API。
- 现有接口保持不变：
  - `POST /api/v1/auth/register`
  - `POST /api/v1/auth/login`
  - `POST /api/v1/auth/logout`
  - `GET /api/v1/me`
- 内部服务契约：
  - `AuthSessionService.createActiveSession(...)`
    - 创建 ACTIVE 会话。
    - 入参中的 refresh token 明文只用于哈希，不落库。
  - `AuthSessionService.findBySessionId(...)`
    - 根据会话标识查询服务端会话。
  - `AuthSessionService.revokeSession(...)`
    - 将 ACTIVE 会话更新为 REVOKED。
- 内部 Mapper 契约：
  - `insert`
  - `findBySessionId`
  - `findByRefreshTokenHash`
  - `updateLastSeenAt`
  - `revokeBySessionId`
  - `updateStatus`

## 7. 数据设计
新增表：`auth_sessions`。

核心字段：
- `id BIGINT AUTO_INCREMENT PRIMARY KEY`
- `session_id VARCHAR(64) NOT NULL`
- `user_id BIGINT NOT NULL`
- `refresh_token_hash VARCHAR(128) NOT NULL`
- `status VARCHAR(16) NOT NULL`
- `issued_at TIMESTAMP NOT NULL`
- `refresh_expires_at TIMESTAMP NOT NULL`
- `last_refreshed_at TIMESTAMP NULL`
- `last_seen_at TIMESTAMP NULL`
- `revoked_at TIMESTAMP NULL`
- `revoke_reason VARCHAR(64) NULL`
- `client_ip_hash VARCHAR(128) NULL`
- `user_agent_hash VARCHAR(128) NULL`
- `user_agent_summary VARCHAR(255) NULL`
- `version INT NOT NULL DEFAULT 0`
- `created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`

约束：
- `uk_auth_sessions_session_id UNIQUE (session_id)`
- `uk_auth_sessions_refresh_token_hash UNIQUE (refresh_token_hash)`
- `fk_auth_sessions_user FOREIGN KEY (user_id) REFERENCES users(id)`

索引：
- `idx_auth_sessions_user_id (user_id)`
  - 支持按用户查看设备会话、全端失效和安全排查。
- `idx_auth_sessions_status (status)`
  - 支持筛选 ACTIVE / REVOKED 会话。
- `idx_auth_sessions_refresh_expires_at (refresh_expires_at)`
  - 支持 refresh 校验、过期清理和后台扫描。

一致性考虑：
- MySQL 是认证会话权威记录。
- `session_id` 唯一约束防止重复会话标识。
- `refresh_token_hash` 唯一约束防止重复 refresh token 或极低概率哈希碰撞静默落库。
- `version` 预留给后续 refresh token 轮换的乐观锁控制。

## 8. 关键流程
本次落地的是模型和骨架，下面是后续演进流程。

正常登录流程：
1. 用户名密码校验通过。
2. 生成 access token、refresh token 和 `session_id`。
3. 使用 `RefreshTokenHasher` 对 refresh token 明文做哈希。
4. 插入 `auth_sessions`，状态为 `ACTIVE`。
5. 响应返回 access token 和 refresh token。

refresh 流程：
1. 客户端提交 refresh token。
2. 服务端对明文 token 做同样哈希。
3. 通过 `refresh_token_hash` 查询会话。
4. 校验会话为 `ACTIVE` 且 `refresh_expires_at` 未过期。
5. 使用 `version` 做乐观锁，轮换 refresh token hash 并更新 `last_refreshed_at`。
6. 返回新的 access token 和 refresh token。

logout 流程：
1. 根据当前认证上下文定位 `session_id`。
2. 将对应 ACTIVE 会话更新为 `REVOKED`。
3. 写入 `revoked_at` 和 `revoke_reason`。
4. 客户端删除本地 token。
5. 后续可将未过期 access token 加入 Redis denylist。

异常流程：
- refresh token 哈希查不到会话：返回认证失败。
- 会话已 `REVOKED`：拒绝 refresh，必要时触发安全告警。
- refresh token 已过期：拒绝 refresh，引导重新登录。
- 同一旧 refresh token 被并发使用：后续通过 `version` 和 refresh token 轮换检测重放。

## 9. 并发 / 幂等 / 缓存
- 并发：
  - 创建不同 `session_id` 的会话互不冲突。
  - 创建相同 `session_id` 由唯一约束兜底失败。
  - 后续 refresh token 轮换使用 `version` 做乐观锁，避免并发双刷都成功。
- 幂等：
  - 本次不新增外部 API，因此不新增请求幂等键。
  - 后续 logout 可以设计为幂等：已 REVOKED 会话再次 logout 仍返回成功。
- 缓存：
  - 本次不接入 Redis。
  - 后续 Redis 适合保存短 TTL access token denylist、会话状态缓存和 refresh 重放快速拦截。
  - Redis 不作为会话权威记录，避免缓存丢失导致已吊销会话恢复有效。

## 10. 权限与安全
- 不新增接口，因此不新增角色访问规则。
- refresh token 不保存明文，只保存哈希。
- `client_ip_hash`、`user_agent_hash` 不保存敏感原文，降低长期审计表中的隐私暴露。
- `user_agent_summary` 只保存可展示摘要，服务后续设备列表。
- `AuthSessionEntity` 只在持久化层和服务层内部使用，不作为 HTTP 响应对象。
- 当前 JWT Filter 仍按原逻辑解析 access token 并查询最新用户状态。

## 11. 测试策略
- 单元测试：
  - `RefreshTokenHasherTest`
  - 验证 hash 不等于原 token。
  - 验证相同 token hash 稳定。
  - 验证不同 token hash 不同。
  - 验证空 token 被拒绝。
- 集成测试：
  - `AuthSessionMapperTest`
  - 通过 `@SpringBootTest` 和 test profile 执行 Flyway 迁移。
  - 验证 session 插入、按 `session_id` 查询、按 `refresh_token_hash` 查询。
  - 验证 `session_id` 唯一约束。
  - 验证 ACTIVE 到 REVOKED 更新。
  - 验证 Mapper 能绑定 ACTIVE 和 REVOKED 枚举状态。
  - 验证并发创建不同 session 不冲突。
- 回归测试：
  - 运行全量 `mvn test`，确认现有认证流程不受影响。

## 12. 风险与替代方案
- 当前方案风险：
  - SHA-256 是不可逆哈希，但如果 refresh token 生成强度不足，仍可能被离线猜测；后续必须使用高熵随机 token。
  - 当前未接入登录/登出流程，会话表暂时不会产生真实业务数据。
  - H2 MySQL 模式能验证迁移基本语法，但不能完全替代真实 MySQL 验证。
- 备选方案 A：refresh token 明文落库。
  - 没有采用，因为数据库泄漏时攻击者可直接冒用所有 refresh token。
- 备选方案 B：只用 Redis 保存 refresh token。
  - 没有采用，因为 Redis 更适合缓存和 denylist，不适合作为可审计、可恢复的权威会话记录。
- 备选方案 C：立即引入完整 OAuth2 授权服务器。
  - 没有采用，因为当前项目处于单体学习型阶段，本次目标是为后续双 token 打基础，不宜一次引入过重体系。
- 备选方案 D：新增 `EXPIRED` 状态。
  - 没有采用，因为过期可由 `refresh_expires_at` 派生，新增状态会带来同步维护成本。
