# 阶段 1 实现复盘

## 1. 已完成内容

阶段 1 已完成一条可运行、可测试、可用于面试讲述的认证授权闭环：

- 新增 `modules.auth` 业务模块，覆盖注册、登录、登出、当前用户和管理员用户管理。
- 新增 `infra.security` 基础设施，覆盖 Spring Security 配置、JWT Filter、JWT 编解码、当前主体和安全异常响应。
- 新增 `users`、`roles`、`user_roles` 表，并初始化 USER、ADMIN 角色和管理员种子账号。
- 注册时使用 BCrypt 保存密码哈希，并在同一事务内绑定默认 USER 角色。
- 登录成功后签发 JWT access token。
- 当前 JWT 只保存最小主体 ID，不把角色快照作为最终授权依据。
- 每次受保护请求都会重新加载用户状态和角色，禁用用户旧 token 会被拒绝。
- `/api/v1/me` 返回当前登录用户摘要。
- `/api/v1/admin/users` 和用户状态更新接口受 ADMIN 权限保护。
- 管理员用户列表支持分页、筛选、默认新注册用户优先排序，并通过批量角色查询避免 N+1。
- Spring Security 明确区分公开接口、登录接口、管理员接口和默认认证边界。
- 生产 profile 默认关闭 OpenAPI JSON 和 Swagger UI，收敛接口文档暴露面。

## 2. 关键实现映射

安全配置：

- `SecurityConfig` 关闭 CSRF，使用 `SessionCreationPolicy.STATELESS`。
- 注册、登录、系统基础接口、Actuator health/info 在指定 HTTP Method 下公开。
- `/api/v1/me` 和 `/api/v1/auth/logout` 需要认证。
- `/api/v1/admin/users`、`PATCH /api/v1/admin/users/*/status` 和 `/api/v1/admin/**` 需要 ADMIN。
- Swagger 和 OpenAPI 只在 springdoc 开关启用时公开，prod profile 下不注册公开白名单。

JWT：

- `JwtCodec` 负责签发、解析、验签、过期校验、issuer 校验和 subject 提取。
- `JwtClaims` 只保存 `subjectId`。
- `TokenServiceImpl` 决定登录成功后签发 access token，底层委托 `JwtCodec`。
- `JwtAuthenticationFilter` 从 Authorization Header 中读取 Bearer token，认证成功后写入 `SecurityContext`。

当前用户上下文：

- `AuthenticatedPrincipal` 只保存 `userId`、`username` 和 `authorities`。
- `AuthenticatedPrincipalService` 按 `userId` 查询最新用户状态和角色。
- `SecurityUtils` 封装 `SecurityContextHolder` 读取逻辑，Controller 通过它获取当前主体。

注册登录：

- `AuthServiceImpl.register` 归一化用户名和邮箱，检查重复，使用 BCrypt 哈希密码。
- 用户插入后校验影响行数和主键回填。
- 默认 USER 角色绑定影响行数必须为 1，否则抛异常并回滚。
- `DuplicateKeyException` 被转换为稳定的账号重复业务异常，用于兜底并发重复注册。
- `AuthServiceImpl.login` 不区分账号不存在和密码错误，降低账号枚举风险。

管理员用户管理：

- `AdminUserController` 类级别使用 `@PreAuthorize("hasRole('ADMIN')")`。
- `AdminUserQueryRequest` 统一承接分页、账号字段、状态和时间范围筛选。
- `PageRequest` 和 `PageResponse<T>` 沉淀为通用分页模型。
- `UserQueryCriteria` 作为 Mapper 查询条件，避免 MyBatis XML 直接依赖 Web DTO。
- `RoleMapper.findRoleCodesByUserIds` 用批量查询替代逐用户查询角色。

## 3. 代码组织结果

当前安全认证代码按以下职责分布：

```text
infra.security.config
  SecurityConfig

infra.security.jwt
  JwtProperties
  JwtCodec
  JwtClaims
  JwtAuthenticationFilter

infra.security.principal
  AuthenticatedPrincipal

infra.security.support
  SecurityUtils
  PasswordEncoderConfig

infra.security.handler
  RestAuthenticationEntryPoint
  RestAccessDeniedHandler
  SecurityErrorResponseWriter

modules.auth.security
  AuthenticatedPrincipalService

modules.auth.service
  AuthService
  TokenService

modules.auth.service.impl
  AuthServiceImpl
  TokenServiceImpl
```

这个结构的重点是让 Filter、JWT 技术能力、当前主体模型、密码编码器、业务服务和用户权限加载各自有明确位置。

## 4. 测试与验证

历史实现文档和当前测试代码共同覆盖了阶段 1 的关键路径。

代表性测试命令包括：

- `mvn -q -Dtest=AuthIntegrationTest test`
- `mvn -q -Dtest=SystemControllerTest,AuthIntegrationTest test`
- `mvn clean test`
- `mvn -q test`
- `git diff --check`

当前测试代码中的代表性覆盖：

- `AuthIntegrationTest` 覆盖 23 个认证授权场景：
  - 注册成功并绑定 USER 角色。
  - 重复用户名、重复邮箱。
  - 并发注册只成功一个账号。
  - 登录成功返回 access token。
  - 密码错误登录失败。
  - 禁用用户不能登录。
  - 未携带 token 访问受保护接口返回 401。
  - 登出接口要求登录。
  - USER 访问管理员接口返回 403。
  - 过期 token、篡改 token 返回 401。
  - 禁用用户旧 token 返回 401。
  - `/api/v1/me` 返回当前用户。
  - 管理员种子账号访问管理员用户列表。
  - 管理员分页、筛选、时间范围和非法参数。
  - 用户状态枚举拒绝非法值、小写值、数字 ordinal 和 null。
- `SystemControllerTest` 覆盖公开系统接口、Actuator、OpenAPI 和未公开 system 方法的安全边界。
- `OpenApiProductionSecurityTest` 覆盖 prod profile 下 `/v3/api-docs`、`/swagger-ui.html` 未认证访问返回 401，且 `/actuator/health` 仍公开。
- `PageRequestTest` 和 `PageResponseTest` 覆盖分页默认值、offset、非法参数、总页数和翻页标识。

这些验证说明阶段 1 不是只写了接口，而是覆盖了认证失败、授权失败、token 异常、禁用状态、并发注册和管理端查询边界。

## 5. 已知限制

Token 与会话：

- 当前只有 access token，没有 refresh token。
- `/api/v1/auth/logout` 是无状态 no-op，服务端不会立即吊销已签发 token。
- token 过期后需要重新登录。

权限模型：

- 当前只有 USER 和 ADMIN 两个角色。
- 没有 permissions 表、角色权限表、资源权限和数据范围权限。
- 管理员能力目前集中在用户列表和状态更新，尚未覆盖活动、票种和操作日志管理。

性能与缓存：

- 每次受保护请求都会查询用户和角色，低复杂度阶段可以接受，高并发下需要缓存或权限版本优化。
- 管理员列表使用 `LIKE '%xxx%'`，大数据量下普通 BTree 索引利用有限。
- 管理员列表 `created_at DESC, id DESC` 当前没有专门组合索引。
- `COUNT(*)` 在用户量很大时可能成为成本点。

生产治理：

- 管理员种子账号只适合本地开发和演示，生产环境应替换为受控初始化流程。
- JWT secret 在 prod 下需要外部注入高强度密钥。
- 生产默认关闭 OpenAPI，但暂未设计受控的内部文档入口。

## 6. 后续演进

短期可演进：

- 引入 refresh token 与登录会话表，支持续期和服务端主动吊销。
- 增加 token version 或 password version，让密码修改、角色变更、管理员强制下线能让旧 token 失效。
- 在 `AuthenticatedPrincipalService` 内加入短 TTL 权限缓存，并设计禁用和角色变更的失效策略。
- 给管理员用户列表补充索引评估，例如 `(created_at, id)` 或 `(status, created_at, id)`。
- 增加登录审计、失败次数限制和简单限流。

中长期可演进：

- 将角色级 RBAC 扩展为角色加权限点模型。
- 将认证能力抽成独立 auth 服务或接入 OAuth2 / OIDC。
- 在 API 网关层统一做 JWT 校验，下游服务接收可信用户上下文。
- 将当前用户、订单、支付、通知和操作日志串联为完整审计链路。

## 7. 面试复盘角度

阶段 1 最适合讲三个能力：

- 安全主链路：注册、登录、JWT、Filter、SecurityContext、RBAC、401/403。
- 工程分层：`infra.security` 处理安全基础设施，`modules.auth` 处理账号业务，JWT 技术和 token 业务语义分离。
- 风险意识：最小 JWT、实时查库、数据库唯一约束兜底、无状态 logout 限制、生产文档关闭和后续缓存失效策略。

一句话表达：

> 我用 Spring Security 和最小 JWT 实现了注册登录与 RBAC，token 只保存用户 ID，请求期实时加载用户状态和角色，保证禁用用户和角色变化能在后续请求中生效，同时通过 URL 级和方法级规则保护管理员接口。
