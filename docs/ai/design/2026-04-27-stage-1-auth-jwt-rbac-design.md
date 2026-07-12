# 阶段 1 认证、JWT 与 RBAC 设计

> 说明：本文记录阶段 1 初始认证实现的设计快照。当前 auth/security 包边界和最小 JWT 决策已在 `docs/ai/design/2026-04-29-auth-security-refactor-design.md` 中演进，阅读当前实现时以后者为准。

## 1. 背景
- 当前项目已完成阶段 0 后端基础工程，具备统一响应体、全局异常处理、请求追踪、OpenAPI、Flyway 与基础测试环境。
- 阶段 1 需要从“系统基础能力”进入第一个真实业务基础设施：用户身份识别与权限边界。
- 活动预约与票务平台后续会继续扩展活动管理、订单、支付、操作日志等能力，因此必须先建立稳定的用户、角色、登录态和管理员边界。

## 2. 目标
- 新增 `auth` 模块，完成用户注册、用户登录、JWT 签发与校验、获取当前用户、无状态登出入口。
- 建立 `User / Role / UserRole` 数据模型，并通过 Flyway 初始化 `USER`、`ADMIN` 角色与管理员种子账号。
- 接入 Spring Security，使用 Bearer Token 保护需要登录或管理员权限的接口。
- 新增最小管理接口，用于验证 `ADMIN` 与 `USER` 的 RBAC 边界。
- 成功标准：
  - 游客可以访问注册、登录、系统探活、Actuator 基础端点与 OpenAPI。
  - 登录用户可以访问 `/api/v1/me`。
  - 普通用户访问 `/api/v1/admin/**` 返回 403。
  - token 缺失、非法、过期返回 401。
  - 禁用用户不能登录，已签发 token 在下次请求时也会被拦截。

## 3. 非目标
- 不实现 OAuth2 授权服务器。
- 不实现短信验证码、邮件验证码、找回密码。
- 不实现 refresh token、Redis token 黑名单或多端登录管理。
- 不实现菜单、按钮、资源级复杂权限。
- 不引入多租户权限模型。
- 不把阶段 1 拆成独立认证服务，仍保持当前单体后端模块化结构。

## 4. 影响范围
- 后端依赖：
  - 新增 Spring Security 依赖。
  - 新增 JWT 库，用于生成和解析 access token。
- Java 包结构：
  - `modules/auth/controller`
  - `modules/auth/service`
  - `modules/auth/mapper`
  - `modules/auth/entity`
  - `modules/auth/dto/request`
  - `modules/auth/vo`
  - `modules/auth/enums`
  - `modules/auth/exception`
  - `modules/auth/security`
- 数据库：
  - 新增 `users`、`roles`、`user_roles` 表。
  - 新增角色与管理员种子数据。
- 配置：
  - 新增 JWT 密钥、有效期、issuer 等配置项。
- 测试：
  - 新增认证授权集成测试。
  - 调整既有 Web 测试，确保 Spring Security 接入后公开端点仍可访问。

## 5. 领域建模
- `User`
  - 表示平台账号。
  - 关键字段：`id`、`username`、`email`、`password_hash`、`status`、`created_at`、`updated_at`。
  - 当前状态：`ENABLED`、`DISABLED`。
- `Role`
  - 表示角色。
  - 当前内置角色：`USER`、`ADMIN`。
  - 后续可扩展为更细粒度权限，但阶段 1 只使用角色边界。
- `UserRole`
  - 表示用户与角色的多对多关系。
  - 当前注册用户默认绑定 `USER`，管理员种子账号绑定 `ADMIN` 与 `USER`。
- `AuthenticatedUser`
  - 安全上下文中的当前用户快照。
  - 包含用户 ID、用户名、邮箱、状态和角色集合。

关键状态：

```text
ENABLED
  | 管理员禁用
  v
DISABLED
  | 管理员启用
  v
ENABLED
```

约束：
- 只有 `ENABLED` 用户允许登录。
- JWT 解析成功后仍需查询数据库，确保用户存在且未被禁用。
- `DISABLED` 用户访问受保护接口返回 401。

## 6. API 设计
### 6.1 用户注册

```text
POST /api/v1/auth/register
```

请求：

```json
{
  "username": "alice",
  "email": "alice@example.com",
  "password": "Password123"
}
```

响应：

```json
{
  "code": "COMMON-000",
  "message": "成功",
  "data": {
    "id": 1,
    "username": "alice",
    "email": "alice@example.com",
    "status": "ENABLED",
    "roles": ["USER"]
  }
}
```

错误：
- 用户名重复：`AUTH-409`
- 邮箱重复：`AUTH-409`
- 密码格式不合法：`COMMON-400`

### 6.2 用户登录

```text
POST /api/v1/auth/login
```

请求：

```json
{
  "usernameOrEmail": "alice",
  "password": "Password123"
}
```

响应：

```json
{
  "code": "COMMON-000",
  "message": "成功",
  "data": {
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
}
```

错误：
- 账号或密码错误：`AUTH-401`
- 用户被禁用：`AUTH-403`

### 6.3 获取当前用户

```text
GET /api/v1/me
Authorization: Bearer <access_token>
```

响应：同用户摘要结构。

错误：
- token 缺失：`AUTH-401`
- token 过期：`AUTH-401`
- token 签名非法：`AUTH-401`
- 用户被禁用：`AUTH-401`

### 6.4 登出

```text
POST /api/v1/auth/logout
Authorization: Bearer <access_token>
```

响应：

```json
{
  "code": "COMMON-000",
  "message": "成功",
  "data": null
}
```

说明：
- 阶段 1 使用无状态 access token，不保存服务端会话。
- 登出接口只表达“客户端应删除本地 token”的协议语义。

### 6.5 管理员用户列表

```text
GET /api/v1/admin/users
Authorization: Bearer <admin_access_token>
```

响应：用户摘要列表。

权限：
- `ADMIN` 可访问。
- `USER` 访问返回 403。

### 6.6 更新用户状态

```text
PATCH /api/v1/admin/users/{userId}/status
Authorization: Bearer <admin_access_token>
```

请求：

```json
{
  "status": "DISABLED"
}
```

响应：更新后的用户摘要。

## 7. 数据设计
### 7.1 `users`

```sql
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(32) NOT NULL,
    email VARCHAR(128) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
);
```

索引与约束：
- `uk_users_username`：防止重复用户名，并支持登录查询。
- `uk_users_email`：防止重复邮箱，并支持邮箱登录查询。

### 7.2 `roles`

```sql
CREATE TABLE roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_roles_code UNIQUE (code)
);
```

索引与约束：
- `uk_roles_code`：角色编码是权限判断的稳定业务标识。

### 7.3 `user_roles`

```sql
CREATE TABLE user_roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_roles_user_role UNIQUE (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);
```

索引与约束：
- `uk_user_roles_user_role`：避免同一用户重复绑定同一角色。
- `idx_user_roles_role_id`：后续从角色反查用户时预留查询能力。

数据一致性：
- 注册用户与角色绑定放在同一个事务内。
- 用户状态更新只改变 `users.status`，不删除角色绑定，便于重新启用后恢复权限。

## 8. 关键流程
### 8.1 注册流程

```text
校验请求体
  -> 检查 username / email 是否已存在
  -> BCrypt 加密密码
  -> 插入 users
  -> 查询 USER 角色
  -> 插入 user_roles
  -> 返回用户摘要
```

异常流程：
- 预检查发现重复用户名或邮箱，直接抛业务异常。
- 并发请求绕过预检查时，由唯一约束兜底，再转换成业务异常。

### 8.2 登录流程

```text
按 usernameOrEmail 查询用户
  -> 不存在则返回账号或密码错误
  -> BCrypt 校验密码
  -> 用户 DISABLED 则拒绝
  -> 查询角色
  -> 生成 JWT
  -> 返回 token 与用户摘要
```

### 8.3 JWT 认证流程

```text
请求进入 Security Filter Chain
  -> 公开端点直接放行
  -> 受保护端点读取 Authorization Bearer token
  -> 校验 token 签名、过期时间、issuer
  -> 读取 sub / username / roles
  -> 查询数据库确认用户存在且 ENABLED
  -> 写入 SecurityContext
  -> 后续授权规则判断角色
```

### 8.4 用户状态流转

```text
管理员调用状态接口
  -> 校验目标用户存在
  -> 更新 status
  -> 返回最新用户摘要
```

## 9. 并发 / 幂等 / 缓存
- 并发：
  - 注册重复依赖数据库唯一约束做最终保护。
  - 状态切换当前不引入乐观锁，因为阶段 1 没有复杂并发写冲突；最后一次写入生效。
- 幂等：
  - 注册接口非幂等，同一用户名或邮箱重复提交返回错误。
  - logout 在无状态 token 方案下接近幂等，重复调用不会修改服务端资源。
  - 用户状态接口以目标状态为输入，多次设置同一状态结果一致。
- 缓存：
  - 阶段 1 不缓存用户权限。
  - JWT 中保留 roles claims，用于表达签发时的角色快照；请求认证时仍查库确认用户未禁用。
  - 后续若加入 Redis，可把用户权限短 TTL 缓存或 refresh token 黑名单放入 Redis。

## 10. 权限与安全
- 密码：
  - 使用 BCrypt 存储哈希值，不保存明文密码。
  - 登录时使用 `PasswordEncoder.matches` 校验。
- JWT：
  - access token 有效期 30 分钟。
  - claims 包含 `sub`、`username`、`roles`、`iat`、`exp`、`iss`。
  - 密钥通过配置注入，生产环境必须使用 `EVENTHUB_JWT_SECRET` 外部注入，不在公共配置中提供默认值。
- 访问控制：
  - `POST /api/v1/auth/register`、`POST /api/v1/auth/login` 公开。
  - `/api/v1/system/**`、Actuator `health/info`、OpenAPI 与 Swagger UI 公开；系统模块仍作为基础工程演示入口，不纳入阶段 1 业务权限边界。
  - `GET /api/v1/me`、`POST /api/v1/auth/logout` 需要登录。
  - `/api/v1/admin/**` 需要 `ADMIN`。
- 异常处理：
  - 认证失败统一返回 401。
  - 授权失败统一返回 403。
  - 业务校验失败沿用统一响应体。

## 11. 测试策略
- 单元测试：
  - JWT 签发与解析。
  - 过期 token 判断。
- 集成测试：
  - 注册成功。
  - 重复用户名注册失败。
  - 登录成功返回 token。
  - 密码错误登录失败。
  - 禁用用户不能登录。
  - 无 token 访问受保护接口返回 401。
  - USER 访问 ADMIN 接口返回 403。
  - 过期 token 返回 401。
- 回归测试：
  - `SystemControllerTest` 确认系统探活、Actuator、OpenAPI 仍可访问。
  - `GlobalExceptionHandlerTest` 确认已有统一异常契约不被破坏。
- 手工验证：
  - 使用 Swagger UI 或 curl 完成注册、登录、携带 token 访问 `/api/v1/me`。
  - 使用普通用户 token 访问管理员接口，确认返回 403。

## 12. 风险与替代方案
- 风险：
  - 每次 JWT 认证都查询数据库，会增加一次读库成本。
  - 无状态 logout 不会让已签发 token 立即失效。
  - roles claims 是签发时快照，角色变更后旧 token 内的 roles 仍可能滞后。
- 替代方案：
  - 完全信任 JWT claims，不查库：性能更好，但禁用用户无法立即失效。
  - 引入 refresh token 与 Redis 黑名单：能力更完整，但阶段 1 复杂度明显上升。
  - 使用 Spring OAuth2 Resource Server：标准化程度更高，但学习门槛与配置复杂度高于当前阶段需要。
- 当前选择：
  - 使用自定义 JWT Filter 与数据库即时校验，优先保证权限边界清晰、禁用即时生效和实现可读性。
