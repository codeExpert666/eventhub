# Auth Security 边界重构设计

## 1. 背景
- 当前 `modules/auth/security` 同时承载 Spring Security 配置、JWT 签发解析、认证主体建模、401/403 响应写出和 auth 用户加载逻辑。
- 这些职责混在 auth 业务模块中，会让后续活动、订单、通知等模块复用当前登录主体时依赖 auth 内部实现，也会让安全基础设施反向感知用户实体、VO 或完整业务服务。
- 本次重构基于 `auth-security-refactor-plan.md`，目标是调整代码边界，而不是改变阶段 1 的注册、登录、JWT、RBAC 对外行为。

## 2. 目标
- 删除 `modules/auth/security` 包，避免 auth 模块承载全局安全基础设施。
- 将 JWT 签发、解析、验签和 claims 建模迁移到 `infra.jwt`。
- 将 Spring Security 配置、JWT Filter、401/403 处理器迁移到 `infra.security`。
- 将当前认证主体抽象为 `common.security.AuthenticatedSubject`，让后续模块只依赖稳定安全契约。
- 通过 `AuthenticatedSubjectLoader` 隔离 `infra.security` 与 auth 业务服务，保证 Filter 不直接依赖 `AuthService`。
- 保持现有 API 路径、响应结构、错误码和集成测试语义不变。

## 3. 非目标
- 本次不新增 refresh token、token 黑名单、登出吊销、登录审计或 Redis 缓存。
- 本次不调整 `users`、`roles`、`user_roles` 表结构、索引或初始化数据。
- 本次不引入网关、OAuth2 Server、分布式会话或额外安全框架。
- 本次不改变阶段 1 的角色模型，仍沿用 `USER` / `ADMIN` 并在 Spring Security 授权时转换为 `ROLE_USER` / `ROLE_ADMIN`。

## 4. 影响范围
- `common.security`
  - 新增跨模块认证主体与加载接口。
- `infra.jwt`
  - 新增 JWT 配置、claims 模型和 token provider。
- `infra.security`
  - 新增 Security 配置、Filter 和安全异常响应处理器。
- `modules.auth`
  - `AuthService` 聚焦注册、登录、当前用户资料查询和管理员用户管理。
  - 新增 `AuthenticatedSubjectService` 作为 `AuthenticatedSubjectLoader` 的 auth 实现。
- 测试影响
  - `AuthIntegrationTest` 的 JWT 测试依赖从旧 `JwtTokenService` 迁移到新 `JwtTokenProvider`。
- 不涉及数据库表、缓存组件或外部接口变更。

## 5. 领域建模
- `AuthenticatedSubject`
  - 表示系统内部当前认证主体。
  - 字段保持最小集合：`subjectId`、`principalName`、`authorities`。
  - `principalName` 当前来源于 `users.username`，但跨模块契约只表达“认证主体可读名称”，不承诺永远绑定用户表字段。
  - 不包含邮箱、头像、用户状态、用户实体或接口响应 VO，避免安全上下文膨胀。
- `AuthenticatedSubjectLoader`
  - 表示安全基础设施加载当前主体的窄接口。
  - `infra.security` 只认识该接口，不认识 auth 模块的实体、VO、枚举或完整服务。
- `AccessTokenClaims`
  - 表示 access token 内的最小身份快照。
  - 当前只保存 `subjectId`，权限和主体可读名称都以数据库实时查询结果为准。
- `UserInfo`
  - 仍是 auth 模块对外响应 VO，由 auth 服务根据用户表和角色表组装。

## 6. API 设计
- 对外 API 不变：
  - `POST /api/v1/auth/register`
  - `POST /api/v1/auth/login`
  - `POST /api/v1/auth/logout`
  - `GET /api/v1/me`
  - `GET /api/v1/admin/users`
  - `PATCH /api/v1/admin/users/{userId}/status`
- 响应结构不变：
  - 成功响应继续使用 `ApiResponse.success(...)`。
  - 认证失败继续返回 `AUTH-401`。
  - 权限不足继续返回 `AUTH-403`。
- `/api/v1/me` 的实现方式调整：
  - Controller 从 `@AuthenticationPrincipal` 获取 `AuthenticatedSubject`。
  - `AuthService` 根据 `subjectId` 查询最新用户资料并组装 `UserInfo`。

## 7. 数据设计
- 本次不改表结构。
- `users.username`、`users.email` 的唯一约束仍是注册并发的最终防线。
- `user_roles` 仍是权限实时读取来源，JWT 内不再作为最终权限依据保存角色快照。
- JWT 只保存 `sub`、`iat`、`exp`、`iss`，不保存 `username`、`principalName` 或 `roles`。
- 如果后续引入缓存，应优先放在 `AuthenticatedSubjectLoader` 实现内部，并明确缓存失效策略。

## 8. 关键流程
- 登录流程：
  - `AuthController.login`
  - `AuthService.login` 校验账号、密码、用户状态
  - `JwtTokenProvider.generateAccessToken` 签发最小 JWT
  - 返回 access token 和 `UserInfo`
- 受保护接口访问流程：
  - `JwtAuthenticationFilter` 从 `Authorization` 读取 Bearer token
  - `JwtTokenProvider.parseAccessToken` 校验签名、issuer、过期时间并解析 claims
  - `AuthenticatedSubjectLoader.loadBySubjectId` 查询最新用户状态和权限
  - Filter 将 `AuthenticatedSubject` 转换为 `UsernamePasswordAuthenticationToken`
  - Spring Security 完成后续接口与方法级授权
- 异常流程：
  - token 缺失：进入 Spring Security 认证入口，返回 401。
  - token 过期、签名错误、issuer 不匹配、用户不存在、用户被禁用：Filter 清理上下文并返回 401。
  - 用户已认证但角色不足：`RestAccessDeniedHandler` 返回 403。

## 9. 并发 / 幂等 / 缓存
- 本次重构不新增写入流程，因此不引入新的幂等键或库存类并发风险。
- 注册并发策略不变：服务层预检查提供友好错误，数据库唯一约束兜底。
- JWT 仍是无状态 access token，不做服务端存储。
- 每次请求实时加载用户状态和角色，牺牲一次查询成本，换取禁用用户和角色变化更快生效。
- 当前不引入缓存，避免早期缓存一致性复杂度；后续如有性能压力，可在 `AuthenticatedSubjectService` 内部加入短 TTL 缓存或 Redis 缓存。

## 10. 权限与安全
- `infra.security` 不依赖 auth 实体、VO、枚举或 `AuthService`。
- `JwtTokenProvider` 不依赖 Spring Security，也不依赖 auth 模块。
- `AuthenticatedSubject` 不引用 `UserEntity`、`UserInfo`、`UserStatus` 或 `RoleEntity`。
- `AuthenticatedSubject.principalName()` 当前由 auth 模块的用户名填充，仅用于日志、调试和后续审计扩展，不参与授权判断。
- 权限字符串在 `AuthenticatedSubject.authorities()` 中统一转为 `ROLE_` 前缀，匹配 `hasRole("ADMIN")` 的 Spring Security 约定。
- JWT 只保存最小 claims，最终用户状态和权限以数据库最新数据为准。

## 11. 测试策略
- 编译检查：
  - 运行 `mvn -q -DskipTests compile`，验证包迁移、Bean 装配、配置属性加载和 import 修正。
- 集成测试：
  - 运行 `mvn -q -Dtest=AuthIntegrationTest test`，覆盖注册、登录、401、403、过期 token、篡改 token、禁用旧 token。
- 卫生检查：
  - 运行 `git diff --check`，避免文档和代码中出现尾随空白。
- 结构检查：
  - 使用 `rg` 确认旧 `modules.auth.security` 包无残留引用。

## 12. 风险与替代方案
- 风险：包名和类名迁移后可能出现 Bean 找不到、配置属性未启用或 import 漏改。
  - 应对：优先运行编译和 auth 集成测试。
- 风险：每次请求都查询用户与角色，后续在高并发下可能产生额外数据库压力。
  - 应对：当前阶段保持一致性优先，后续在 `AuthenticatedSubjectService` 内部增加缓存。
- 替代方案 A：保留 `modules/auth/security`，只做类名清理。
  - 不采用原因：无法解决 auth 模块承载全局基础设施的问题。
- 替代方案 B：让 JWT 携带完整权限并直接作为授权依据。
  - 不采用原因：用户禁用和角色变更不能及时生效，不符合当前安全验收目标。
- 替代方案 C：直接引入 OAuth2 Resource Server。
  - 不采用原因：阶段 1 仍是单体学习型项目，会显著增加概念和配置复杂度。
