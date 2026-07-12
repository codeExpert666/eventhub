# 阶段 1 设计概览

## 1. 阶段目标

阶段 1 负责为活动预约与票务平台建立最小可用的认证与授权闭环。它解决的问题不是“能不能保存用户”，而是后续所有业务如何可靠地回答三个问题：

- 当前请求是谁发起的。
- 这个用户当前是否仍然有效。
- 这个用户是否有权限访问目标资源。

核心目标：

- 实现用户注册、登录、登出语义和当前用户查询。
- 使用 BCrypt 保存密码哈希。
- 使用 JWT access token 承载无状态登录凭证。
- 接入 Spring Security Filter Chain，统一处理 401 和 403。
- 建立 `users / roles / user_roles` 三张表，支撑 USER / ADMIN RBAC。
- 提供管理员用户接口，用于验证管理端权限边界。
- 为后续活动、订单、支付和审计模块提供当前用户上下文。

## 2. 范围与非范围

阶段 1 的范围：

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/logout`
- `GET /api/v1/me`
- `GET /api/v1/admin/users`
- `PATCH /api/v1/admin/users/{userId}/status`
- JWT 签发、解析、验签、过期校验和 issuer 校验。
- 用户状态校验、角色加载和 Spring Security 授权。
- 管理员种子账号和内置 USER / ADMIN 角色。

阶段 1 的非范围：

- OAuth2 授权服务器或 OIDC。
- refresh token、token 黑名单、服务端会话表和多端登录管理。
- 短信验证码、邮件验证码、找回密码。
- 复杂权限点、菜单权限、按钮权限和资源级授权。
- 独立认证服务、API 网关鉴权和多租户权限模型。

## 3. 模块分层

当前实现把安全基础设施和 auth 业务模块分开：

```text
com.eventhub
├── infra.security
│   ├── config
│   ├── handler
│   ├── jwt
│   ├── principal
│   └── support
└── modules.auth
    ├── controller
    ├── dto/request
    ├── dto/response
    ├── entity
    ├── enums
    ├── exception
    ├── mapper
    ├── mapper/param
    ├── mapper/result
    ├── security
    ├── service
    └── service/impl
```

关键边界：

- `infra.security.config.SecurityConfig` 负责 Security Filter Chain、公开接口、登录接口、管理员接口和默认认证边界。
- `infra.security.jwt.JwtCodec` 只负责 JWT 技术能力，不查询用户表，不操作 `SecurityContext`。
- `infra.security.jwt.JwtAuthenticationFilter` 负责从 HTTP Header 读取 Bearer token，并把认证结果写入 Spring Security 上下文。
- `infra.security.principal.AuthenticatedPrincipal` 是最小当前用户模型，只保存 `userId`、`username` 和 `authorities`。
- `modules.auth.security.AuthenticatedPrincipalService` 负责按用户 ID 查询最新用户状态和角色。
- `modules.auth.service.AuthService` 表达注册、登录、登出、当前用户和管理员用户管理。
- `modules.auth.service.TokenService` 表达 token 签发业务语义，底层委托 `JwtCodec`。

这个分层让 auth 模块负责账号业务，`infra.security` 负责安全基础设施，避免把登录、注册、JWT、Filter 和用户查询全部塞进一个服务。

## 4. 领域对象与数据模型

阶段 1 的核心表：

- `users`：平台账号。
- `roles`：平台角色。
- `user_roles`：用户与角色的多对多关系。

`users` 关键字段：

- `id`：用户主键，也是 JWT `sub` 对应的主体 ID。
- `username`：用户名，唯一。
- `email`：邮箱，唯一，也支持登录。
- `password_hash`：BCrypt 密码哈希。
- `status`：用户状态，当前为 `ENABLED` 或 `DISABLED`。
- `created_at` / `updated_at`：账号创建与更新时间。

`roles` 关键字段：

- `id`：角色主键。
- `code`：稳定角色编码，当前为 `USER` 和 `ADMIN`。
- `name` / `description`：角色展示和说明字段。

`user_roles` 关键约束：

- `uk_user_roles_user_role`：避免一个用户重复绑定同一个角色。
- `fk_user_roles_user` / `fk_user_roles_role`：维护用户和角色关系完整性。
- `idx_user_roles_role_id`：预留按角色反查用户的查询能力。

用户状态规则：

```text
ENABLED
  管理员禁用
  v
DISABLED
  管理员启用
  v
ENABLED
```

约束：

- 只有 `ENABLED` 用户可以登录。
- JWT 合法不等于用户一定可用，请求期仍会查库确认用户存在且未禁用。
- 被禁用用户即使持有未过期旧 token，也会在下一次请求时被拒绝。

## 5. API 设计

公开接口：

```http
POST /api/v1/auth/register
POST /api/v1/auth/login
GET  /api/v1/system/ping
POST /api/v1/system/echo
GET  /actuator/health
HEAD /actuator/health
GET  /actuator/info
HEAD /actuator/info
```

登录后接口：

```http
POST /api/v1/auth/logout
GET  /api/v1/me
```

管理员接口：

```http
GET   /api/v1/admin/users
PATCH /api/v1/admin/users/{userId}/status
```

登录响应的核心结构：

```json
{
  "accessToken": "jwt-token",
  "authorizationScheme": "Bearer",
  "expiresIn": 1800,
  "user": {
    "id": 1,
    "username": "alice",
    "email": "alice@example.com",
    "status": "ENABLED",
    "roles": ["USER"]
  }
}
```

管理员用户列表当前返回分页结构，并支持：

- `page` / `size`
- `username` 包含匹配
- `email` 包含匹配
- `status` 精确匹配
- `createdAtFrom` / `createdAtTo`
- `updatedAtFrom` / `updatedAtTo`

常见错误：

- 注册用户名或邮箱重复：409。
- 登录账号或密码错误：401。
- 禁用用户登录：403。
- 未携带 token 访问受保护接口：401。
- token 过期、篡改、issuer 不匹配或格式错误：401。
- USER 访问 ADMIN 接口：403。
- 管理员查询参数非法：400。

## 6. 关键业务流程

注册流程：

```text
校验 RegisterRequest
  -> 归一化 username / email
  -> 检查 username / email 是否已存在
  -> BCrypt 加密密码
  -> 插入 users 并校验影响行数和主键回填
  -> 查询默认 USER 角色
  -> 插入 user_roles 并校验影响行数为 1
  -> 查询并返回 UserResponse
```

登录流程：

```text
按 usernameOrEmail 查询用户
  -> 不存在或密码错误统一返回 bad credentials
  -> 校验用户状态必须是 ENABLED
  -> 查询用户角色并组装 UserResponse
  -> TokenService 签发 access token
  -> 返回 token 与用户摘要
```

JWT 认证流程：

```text
请求进入 Security Filter Chain
  -> JwtAuthenticationFilter 读取 Authorization: Bearer <token>
  -> JwtCodec 校验签名、issuer、过期时间并解析 subjectId
  -> AuthenticatedPrincipalService 查询最新用户状态和角色
  -> 构造 AuthenticatedPrincipal 和 GrantedAuthority
  -> 写入 SecurityContext
  -> SecurityConfig 和 @PreAuthorize 执行授权判断
```

当前用户流程：

```text
GET /api/v1/me
  -> SecurityUtils 读取 AuthenticatedPrincipal
  -> AuthService 根据 userId 回查最新用户资料
  -> 返回 UserResponse，不暴露 passwordHash
```

管理员用户列表流程：

```text
GET /api/v1/admin/users
  -> URL 级 hasRole("ADMIN")
  -> Controller 级 @PreAuthorize("hasRole('ADMIN')")
  -> 绑定 AdminUserQueryRequest
  -> 转换 PageRequest 和 UserQueryCriteria
  -> COUNT 查询总数
  -> LIMIT/OFFSET 查询当前页用户
  -> 批量查询当前页用户角色，避免 N+1
  -> 返回 PageResponse<UserResponse>
```

## 7. JWT 与 RBAC 设计

当前 JWT 使用最小 claims：

```text
iss: issuer
sub: users.id
iat: issued at
exp: expiration
```

不把用户名、邮箱、角色列表写入 JWT 作为最终授权依据。原因是这些字段可能变化，如果直接信任 token 内快照，会导致用户禁用或角色变更后旧 token 在过期前继续携带旧权限。

RBAC 当前只做角色级边界：

```text
游客：注册、登录、访问公开系统接口和基础探活
USER：访问当前用户接口，后续创建订单、查看自己的订单、模拟支付
ADMIN：访问管理员用户接口，后续管理活动、场次、票种和操作日志
```

Spring Security 使用 `ROLE_` 前缀 authority，例如：

```text
USER  -> ROLE_USER
ADMIN -> ROLE_ADMIN
```

管理员接口采用两层防护：

- URL 级 `hasRole("ADMIN")` 在进入 Controller 前拦截。
- Controller 级 `@PreAuthorize("hasRole('ADMIN')")` 作为方法级防线。

## 8. 并发、一致性、缓存与权限风险

并发：

- 注册接口先做存在性预检查，提升错误提示友好性。
- 数据库唯一约束 `uk_users_username` 和 `uk_users_email` 是并发重复注册的最终防线。
- 用户创建和默认角色绑定处于同一事务，默认角色绑定影响行数必须为 1，否则回滚。

一致性：

- JWT 是无状态 access token，服务端不保存 token。
- 每次受保护请求实时加载用户状态和角色，优先保证用户禁用和角色变更能更快生效。
- 管理员分页列表的总数查询、当前页查询和角色批量查询之间不加锁，接受管理端读视图的轻微偏差。

缓存：

- 当前不缓存用户权限。
- 不引入 Redis token 黑名单或权限缓存，避免过早增加失效策略复杂度。
- 后续高并发时，可在 `AuthenticatedPrincipalService` 内加入短 TTL 权限缓存，但必须设计用户禁用和角色变更后的失效机制。

权限风险：

- 无状态 logout 不能主动吊销已签发 token。
- 只有 access token，没有 refresh token，过期后需要重新登录。
- 当前角色模型只有 `USER` / `ADMIN`，不能表达资源级或操作级权限。
- 管理员种子账号适合本地演示，生产应替换为受控初始化流程。
- 生产环境 OpenAPI / Swagger UI 默认关闭，降低接口契约暴露面。

## 9. 设计价值

阶段 1 的价值在于它把后续业务都依赖的安全上下文建立起来：

- 活动管理可以判断当前用户是否为 `ADMIN`。
- 订单创建可以绑定当前 `userId`。
- 支付回调和订单状态流转可以审计操作者。
- 操作日志可以记录 `AuthenticatedPrincipal` 中的用户 ID 和用户名。
- 后续微服务拆分时，可以把 `TokenService`、`JwtCodec`、`AuthenticatedPrincipalService` 作为认证服务、资源服务和权限加载器的雏形继续演进。
