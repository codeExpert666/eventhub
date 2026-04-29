# 阶段 1 认证、JWT 与 RBAC 实现说明

> 说明：本文记录阶段 1 初始认证实现的交付快照。当前 auth/security 包边界和最小 JWT 决策已在 `docs/ai/implementation/2026-04-29-auth-security-refactor-implementation.md` 中演进，阅读当前实现时以后者为准。

## 1. 本次改动解决了什么问题

本次完成阶段 1 的最小可用认证授权闭环：

- 用户可以注册普通账号。
- 用户可以用用户名或邮箱登录。
- 登录成功后签发 JWT access token。
- 受保护接口通过 Bearer token 建立当前用户上下文。
- `/api/v1/me` 可以返回当前登录用户。
- `/api/v1/admin/**` 只允许 `ADMIN` 角色访问。
- 管理员种子账号可用于本地演示和 RBAC 验证。
- 认证失败返回 401，授权失败返回 403，并保持统一 `ApiResponse` 响应结构。

## 2. 改动内容
- 新增了什么
  - 新增 `spring-boot-starter-security`、`jjwt-api`、`jjwt-impl`、`jjwt-jackson` 依赖。
  - 新增 `V2__stage_1_auth_jwt_rbac.sql`，创建 `users`、`roles`、`user_roles` 表，并初始化 `USER`、`ADMIN` 角色与管理员账号。
  - 新增 `modules/auth` 模块，包含 controller / service / mapper / entity / dto / vo / enums / exception / security 分层。
  - 新增 JWT 配置项：issuer、access token 有效期、签名密钥。
  - 新增 `AuthIntegrationTest`，覆盖注册、登录、禁用、401、403、过期 token 等场景。
- 修改了什么
  - 扩展 `ErrorCode`，新增 `AUTH-401`、`AUTH-403`、`AUTH-409`。
  - 接入 Spring Security Filter Chain，配置公开端点、受保护端点和管理员端点。
  - 调整设计文档，明确 `/api/v1/system/**` 仍作为基础工程演示入口公开。
  - Review 后调整 JWT 密钥配置边界：公共配置只保留 issuer 与 ttl，dev/test 提供本地默认密钥，prod 强制通过 `EVENTHUB_JWT_SECRET` 注入。
  - Review 后同步 `docker-compose.yml` 与 `README.md`，避免本地 Compose 启动缺少 JWT 密钥，也避免文档遗漏生产必填项。
  - Review 后扩展认证集成测试，补齐重复邮箱、篡改 token、禁用后旧 token 三类边界。
- 删除了什么
  - 无删除。

## 3. 为什么这样设计
- 使用单体内的 `auth` 模块，而不是独立认证服务，符合当前阶段学习型/简历型项目的复杂度边界。
- 使用 `JdbcTemplate` 明确表达 SQL 与索引约束，延续阶段 0 的轻量数据访问风格。
- 注册时先做用户名/邮箱存在性检查，再依赖数据库唯一约束兜底，兼顾友好错误提示和并发正确性。
- 使用 BCrypt 保存密码哈希，避免明文密码落库。
- JWT 中写入 `sub`、`username`、`roles`、`iat`、`exp`、`iss`，满足当前登录态识别和角色判断需要。
- 每次 JWT 认证后仍查库确认用户存在且未禁用，牺牲一次读库成本，换取禁用用户即时失效。
- Spring Security 的认证入口和授权拒绝处理器直接写出 `ApiResponse`，因为这些异常发生在 Controller 之前，不能依赖普通全局异常处理器兜底。

## 4. 替代方案
- 方案 A：完全信任 JWT claims，不查数据库。
  - 没有采用：禁用用户或删除用户后，旧 token 在过期前仍可继续访问，安全边界不够清晰。
- 方案 B：引入 refresh token、Redis 黑名单和服务端登出吊销。
  - 没有采用：这会引入 token 存储、过期清理、并发刷新、防重放等额外复杂度，超出阶段 1 最小闭环。
- 方案 C：直接使用 OAuth2 Resource Server 或完整授权服务器模型。
  - 没有采用：更标准但配置和概念更重，不利于当前阶段先理解注册登录、JWT 和 RBAC 主链路。
- 方案 D：为每个 Service 都创建接口和 impl。
  - 没有采用：当前 `AuthService` 只有单一实现，过早抽接口会增加样板代码，暂时没有替换收益。

## 5. 测试与验证
- 跑了哪些测试
  - `mvn -q -DskipTests compile`
  - `mvn -q -Dtest=AuthIntegrationTest test`
  - `mvn -q test`
  - Review 修复后重新执行 `mvn -q -Dtest=AuthIntegrationTest test`
- 手工验证了哪些场景
  - 本次主要通过 MockMvc 集成测试验证 HTTP 链路，未额外启动本地服务做 curl 手工验证。
- 结果如何
  - 编译通过。
  - 阶段 1 集成测试通过。
  - 后端全量测试通过。
  - Review 修复后的认证集成测试通过。

## 6. 已知限制
- 当前只实现 access token，没有 refresh token。
- `POST /api/v1/auth/logout` 是无状态登出入口，服务端不会吊销已签发 access token。
- JWT roles 是签发时的角色快照；当前通过用户禁用即时校验兜底，尚未实现角色变更后的 token 即时失效。
- 管理员用户列表暂未分页，后续用户量增大后需要补分页、筛选与排序。
- 管理员种子账号用于本地开发和演示，生产环境应替换为受控初始化流程。
- JWT 密钥已按环境拆分：dev/test/本地 Compose 可使用演示密钥，prod 不提供默认值，必须通过 `EVENTHUB_JWT_SECRET` 注入高强度密钥。

## 7. 对后续版本的影响
- 对简历可用版的价值
  - 形成可演示的注册、登录、当前用户、管理员权限边界。
  - 后续活动、订单、支付、操作日志都可以复用当前用户上下文和 RBAC 基础。
  - 面试表达可以聚焦“Spring Security + JWT + RBAC + 统一 401/403 响应 + 禁用即时失效”。
- 对微服务 / 云原生演进的影响
  - 后续可将当前 `auth` 模块拆为独立认证服务或统一身份中心。
  - 可引入 Redis refresh token、token 黑名单、短 TTL 权限缓存。
  - 可在网关层统一校验 JWT，并将用户上下文透传给下游服务。

## 8. Review 复盘记录

本小节是基于模板额外补充的复盘记录，用于沉淀阶段 1 实现后的严格审查结论。

| 复盘项 | 问题等级 | 发现的问题 | 修复情况 |
| --- | --- | --- | --- |
| 生产安全配置 | P1 | JWT secret 原本在公共 `application.yml` 中带本地默认值；如果生产环境遗漏 `EVENTHUB_JWT_SECRET`，应用仍可能用仓库内置密钥启动。 | 已修复：公共配置不再提供 secret 默认值；`application-prod.yml` 强制读取 `EVENTHUB_JWT_SECRET`；dev/test/Compose 单独提供本地演示密钥。 |
| 边界测试覆盖 | P2 | 测试已覆盖重复用户名、密码错误、过期 token 等场景，但缺少重复邮箱、签名被篡改 token、禁用用户旧 token 失效这三类阶段 1 关键异常场景。 | 已修复：`AuthIntegrationTest` 新增对应集成测试，并已通过验证。 |
| 缓存一致性 | 无问题 | 阶段 1 未引入权限缓存或 token 黑名单缓存，当前不存在缓存一致性失效点。 | 记录为后续 refresh token / Redis 黑名单引入时的重点复盘项。 |
| 数据库约束 | 无问题 | `users.username`、`users.email`、`roles.code`、`user_roles(user_id, role_id)` 唯一约束合理；`user_roles(user_id, role_id)` 可支撑按用户查角色，额外 `idx_user_roles_role_id` 可支撑后续按角色反查用户。 | 暂不调整。 |
| 并发与幂等 | 可接受风险 | 注册接口已用唯一约束兜底并发重复账号；状态更新是同值幂等的 `PATCH`。当前没有 refresh token 或支付类幂等场景。 | 暂不增加复杂机制，后续在订单/支付阶段重点加强。 |
