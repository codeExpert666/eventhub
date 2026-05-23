# 服务端认证会话模型实现说明

## 1. 本次改动解决了什么问题

- 为 EventHub 新增服务端认证会话模型，补齐后续 refresh token、logout 吊销和设备会话管理所需的数据库基础。
- 解决当前只有无状态 access token 时，服务端无法记录、吊销、审计某个设备登录态的问题。
- 明确 refresh token 不保存明文，只保存哈希，降低数据库泄漏时的凭证风险。
- 本次只建设模型、Mapper、Service 骨架和测试，不改变现有登录响应、logout no-op 和 JWT Filter 行为。

## 2. 改动内容
- 新增了什么
  - 新增 Flyway 迁移：`backend/src/main/resources/db/migration/V3__create_auth_sessions.sql`。
  - 新增认证会话实体：`AuthSessionEntity`。
  - 新增会话状态枚举：`AuthSessionStatus`。
  - 新增 MyBatis Mapper：`AuthSessionMapper`。
  - 新增 Mapper XML：`resources/mapper/auth/AuthSessionMapper.xml`。
  - 新增 refresh token 哈希组件：`RefreshTokenHasher`。
  - 新增认证会话服务骨架：`AuthSessionService` / `AuthSessionServiceImpl`。
  - 新增测试：`AuthSessionMapperTest`、`RefreshTokenHasherTest`。
  - 新增设计文档、实现说明和 ADR。
- 修改了什么
  - 仅新增认证会话相关代码和文档。
  - 未修改现有 `AuthServiceImpl`、`LoginResponse`、`JwtAuthenticationFilter` 或 Controller。
- 删除了什么
  - 无。

## 3. 为什么这样设计
- 关键设计原因
  - `auth_sessions` 表作为 MySQL 权威记录，支撑 refresh token 轮换、logout 吊销和设备会话审计。
  - refresh token 只保存 hash，避免数据库泄漏后 refresh token 明文被直接冒用。
  - `session_id` 作为稳定会话标识，方便后续客户端、refresh token claim、设备会话列表和单设备吊销统一定位会话。
  - `version` 预留给 refresh token 轮换的乐观锁，解决同一个旧 refresh token 并发刷新时的竞争问题。
  - `refresh_expires_at` 表达过期事实，暂不增加 `EXPIRED` 状态，避免状态和时间字段重复表达。
  - Redis 暂不接入，后续作为缓存和 denylist 使用，不替代 MySQL 的权威记录。
- 与项目当前阶段的匹配点
  - 保持当前 MyBatis XML 风格和 `entity / enum / mapper / service` 分层。
  - 不引入重量级依赖，`RefreshTokenHasher` 使用 JDK 标准库。
  - 不改变现有认证流程，把风险限制在新增表和新增组件内。
  - 文档先沉淀设计边界，便于后续按小步继续实现 refresh API 和 logout 吊销。

## 4. 替代方案
- 方案 A：refresh token 明文落库
  - 优点：实现简单，查询和排查直观。
  - 未采用原因：数据库一旦泄漏，攻击者可以直接使用 refresh token 长期换取 access token，安全风险不可接受。
- 方案 B：refresh token 只放 Redis
  - 优点：读取快，天然支持 TTL。
  - 未采用原因：Redis 更适合作为缓存和 denylist；作为唯一存储时，持久化、审计、恢复和跨环境排查能力不足。
- 方案 C：立即把 login/logout 全链路改造成双 token
  - 优点：一次交付完整功能。
  - 未采用原因：会同时改 API 契约、登录响应、客户端行为和安全过滤链，范围过大；本次目标是先建立可验证的数据基础。
- 方案 D：引入 Spring Authorization Server
  - 优点：标准 OAuth2/OIDC 能力完整。
  - 未采用原因：当前项目还是单体学习型阶段，直接引入完整授权服务器会显著提高复杂度，不符合最小可用闭环。
- 方案 E：新增 `EXPIRED` 状态
  - 优点：查询过期会话时直观。
  - 未采用原因：过期状态可由 `refresh_expires_at` 派生，提前落状态会带来定时任务和状态同步成本。

## 5. 测试与验证
- 跑了哪些测试
  - `mvn test -Dtest=AuthSessionMapperTest,RefreshTokenHasherTest`
  - `mvn test`
- 手工验证了哪些场景
  - 本次未新增 HTTP API，因此未做 Swagger 或浏览器手工验证。
  - 通过 SpringBootTest 启动测试上下文，间接验证 Flyway V3 可以在测试库执行。
- 结果如何
  - 针对性测试通过：9 个用例，Failures: 0，Errors: 0，Skipped: 0。
  - 全量测试通过：63 个用例，Failures: 0，Errors: 0，Skipped: 0。
  - Flyway 成功执行 3 个迁移，schema 到达 version 3。
  - 覆盖 refresh token hash 不等于原 token、相同 token hash 稳定、不同 token hash 不同。
  - 覆盖 session 插入、按 `session_id` 查询、按 `refresh_token_hash` 查询、唯一约束、ACTIVE/REVOKED 更新和并发创建不同会话。

## 6. 已知限制
- 当前会话服务尚未接入登录流程，因此真实登录不会创建 `auth_sessions` 记录。
- 当前 logout 仍是 no-op，尚不会吊销服务端会话。
- 当前 access token 仍不可服务端即时吊销；后续需要结合短 TTL 和 Redis denylist。
- 当前 `RefreshTokenHasher` 使用 SHA-256，后续如果要抵御更强离线猜测风险，可以演进为 HMAC-SHA256 或增加服务端 pepper。
- 当前设备识别只预留 `client_ip_hash`、`user_agent_hash` 和 `user_agent_summary` 字段，尚未实现摘要生成策略。
- 当前迁移已在 H2 MySQL 模式通过，生产前仍建议在真实 MySQL 环境跑一次迁移验证。

## 7. 对后续版本的影响
- 对简历可用版的价值
  - 认证模块从纯无状态 JWT 演进到可管理会话的基础模型，更接近真实业务系统。
  - 可以清晰讲解 refresh token 为什么不能明文落库、logout 为什么需要服务端状态、设备会话如何建模。
  - 测试覆盖并发、唯一约束和状态更新，能体现后端一致性意识。
- 对微服务 / 云原生演进的影响
  - `auth_sessions` 可作为未来认证服务的核心表。
  - MySQL 权威记录 + Redis denylist/cache 的边界有利于后续拆分认证中心。
  - `session_id` 和 `version` 为跨服务 refresh token 轮换、风控审计和会话事件发布预留扩展点。
