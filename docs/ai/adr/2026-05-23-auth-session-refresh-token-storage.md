# ADR：认证会话使用 MySQL 权威记录并只保存 refresh token 哈希

## 标题
认证会话使用 MySQL 权威记录并只保存 refresh token 哈希

## 状态
- accepted

## 背景
EventHub 当前只有无状态 access token，logout 只是客户端删除 token 的协议入口，服务端无法主动吊销已签发 token。后续要实现 access token + refresh token 双 token 模型、refresh token 轮换、服务端 logout、单设备踢下线和全端失效。

refresh token 是长期凭证，一旦泄漏，攻击者可以持续换取新的 access token。服务端必须能记录、查询、吊销和审计 refresh token 所属会话，同时又不能把 refresh token 明文长期保存到数据库。

项目当前已使用 MySQL、Flyway、MyBatis 和 Redis。需要明确认证会话的权威记录放在哪里，以及 Redis 在后续方案中的边界。

## 决策
本次选择：

- 新增 `auth_sessions` 表，以 MySQL 作为认证会话权威记录。
- 服务端只保存 `refresh_token_hash`，不保存 refresh token 明文。
- `session_id` 唯一，用于稳定定位某个设备会话。
- `refresh_token_hash` 唯一，用于后续 refresh token 查询和防重复落库。
- 使用 `status` 表达 ACTIVE / REVOKED，会话过期由 `refresh_expires_at` 派生。
- 预留 `version` 字段，后续 refresh token 轮换时使用乐观锁。
- Redis 后续只作为缓存、短 TTL denylist 和快速拦截层，不作为认证会话权威存储。

## 备选方案
- 方案 1：refresh token 明文保存在 MySQL。
- 方案 2：refresh token 只保存在 Redis。
- 方案 3：MySQL 保存 refresh token hash，Redis 作为缓存和 denylist。
- 方案 4：直接接入完整 OAuth2/OIDC 授权服务器。

## 决策理由
选择方案 3，原因如下：

- refresh token 不保存明文，可以降低数据库泄漏后的直接凭证冒用风险。
- MySQL 具备事务、唯一约束、外键、持久化和审计能力，适合作为会话权威记录。
- logout、单设备踢下线、用户禁用后的全端失效，都需要可审计且可恢复的服务端状态。
- Redis 性能更适合短期访问优化，例如 access token denylist、会话状态缓存、refresh 重放快速拦截。
- 如果 Redis 数据丢失，系统最多损失缓存或短期 denylist 命中率，不能让已吊销 refresh token 恢复有效。
- 完整 OAuth2/OIDC 更标准，但会显著扩大当前学习型单体项目复杂度，暂不作为本阶段实现目标。

## 影响
- 好处：
  - refresh token 泄漏面收窄，数据库不保存可直接使用的长期凭证明文。
  - 服务端 logout 和设备会话管理有了可靠数据基础。
  - MySQL 唯一约束和 `version` 字段为后续 refresh token 轮换提供一致性基础。
  - Redis 与 MySQL 的职责边界清楚，后续可逐步引入缓存和 denylist。
- 代价：
  - refresh 时需要一次数据库查询或缓存查询后回源，比纯无状态 JWT 复杂。
  - refresh token hash 如果使用普通 SHA-256，仍依赖 refresh token 本身必须足够高熵。
  - 会话表需要过期清理策略，否则长期运行后数据会持续增长。
- 后续可能需要调整的地方：
  - 引入 HMAC-SHA256 或 pepper，让 refresh token hash 即使在数据库泄漏时也更难离线验证。
  - 登录时创建 `auth_sessions`，并在响应中返回 refresh token。
  - refresh API 中用 `version` 完成 refresh token 轮换和重放检测。
  - logout 时吊销当前 session，并将未过期 access token 加入 Redis denylist。
  - 用户禁用、改密或管理员操作时批量吊销该用户所有 ACTIVE session。
