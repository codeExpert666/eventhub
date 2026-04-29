# 阶段 1：注册登录、JWT 与 RBAC

返回主路线图：[活动预约与票务平台建议路线图](./event-booking-roadmap.md)

## 目标

完成用户注册、登录、获取当前用户、基于角色的权限控制。

## 范围

- modules 中新增 auth 模块 
- User / Role / UserRole 数据模型。
- 用户注册。
- 用户登录。
- BCrypt 密码加密。
- JWT 签发与校验。
- 获取当前用户。
- 管理员种子数据。
- Spring Security 基础接入。
- 基础权限注解。
- 认证异常和授权异常处理。

## 非范围

- OAuth2 完整授权服务器。
- 短信验证码。
- 邮件验证码。
- 找回密码。
- 多租户权限系统。
- 复杂菜单权限。

## 接口清单

```text
POST /api/v1/auth/register
POST /api/v1/auth/login
GET  /api/v1/me
POST /api/v1/auth/logout
```

可选管理接口：

```text
GET   /api/v1/admin/users
PATCH /api/v1/admin/users/{userId}/status
```

## Token 策略

简历版建议先实现 access token：

```text
access_token 有效期：30 分钟
JWT claims：
  sub: subjectId（当前对应 users.id）
  iat
  exp
  iss
```

`username` / `principalName` / 角色权限不写入 access token 作为最终授权依据。请求进入 JWT Filter 后，
通过 `AuthenticatedSubjectLoader` 查询最新用户状态与角色权限，避免用户禁用或角色变更后旧 token 继续沿用过期权限快照。

进阶可补 refresh token：

```text
refresh_token 存 Redis
key: auth:refresh:{userId}:{tokenId}
ttl: 7d
```

## 权限边界

```text
游客：注册、登录、查看公开活动列表与详情
USER：创建订单、查看自己的订单、模拟支付
ADMIN：管理活动、场次、票种、查看操作日志
```

## 关键异常场景

- 用户名重复。
- 邮箱重复。
- 密码格式不合法。
- 登录密码错误。
- 用户被禁用。
- token 缺失。
- token 过期。
- token 签名非法。
- USER 访问 ADMIN 接口。

## 交付物

```text
users / roles / user_roles 表
AuthController
UserController
common.security.AuthenticatedSubject / AuthenticatedSubjectLoader
infra.jwt.JwtTokenProvider
infra.security.SecurityConfiguration / JwtAuthenticationFilter
PasswordEncoder 配置
管理员种子数据 migration
相关设计、实现、ADR文档
```

以下是每个模块内部的标准分层，对于 auth 模块来说，不涉及的可直接舍弃：

```text
modules/auth
├── controller
├── service
│   └── impl（简单 service 可不写接口）
├── domain（service 逻辑复杂时才考虑此分层）
├── mapper
├── entity
├── dto
│   ├── request
│   └── query
├── vo
├── converter
├── enums
└── exception
```

## 测试点

- 注册成功。
- 重复用户名注册失败。
- 登录成功返回 token。
- 密码错误登录失败。
- 禁用用户不能登录。
- 无 token 访问受保护接口返回 401。
- USER 访问 ADMIN 接口返回 403。
- token 过期后返回 401。

## 面试表达

> 使用 Spring Security 与最小 JWT 实现无状态认证，通过认证主体加载接口实时查询用户状态与角色权限，实现 USER / ADMIN 权限边界，并统一处理 401、403 等认证授权异常。
