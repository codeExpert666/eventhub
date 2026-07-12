# ADR：拆分 auth 安全基础设施并采用最小 JWT

## 标题
拆分 auth 安全基础设施并采用最小 JWT

## 状态
- accepted

## 背景
阶段 1 已完成注册、登录、JWT 与 RBAC，但实现集中在 `modules/auth/security` 包中。该包既包含 Spring Security 配置和 Filter，也包含 JWT 签发解析、当前认证主体模型和安全异常响应写出。

这种结构在功能上可以运行，但边界不够清晰：

- auth 业务模块承载了全局安全基础设施。
- JWT 与 Spring Security 的职责耦合较深。
- Filter 直接依赖 `AuthService`，后续扩展时容易把登录、注册、用户资料查询等业务能力暴露给基础设施层。
- 旧 token 中保存 roles 快照，容易让读代码的人误以为 token roles 是最终授权依据。

因此需要在不改变对外 API 行为的前提下，重新划分安全相关代码的包边界和依赖方向。

## 决策
最终选择：

- 删除 `modules/auth/security` 包。
- 新增 `common.security.AuthenticatedSubject` 和 `AuthenticatedSubjectLoader` 作为跨模块安全契约。
- 将 JWT 配置、claims 模型和签发解析迁移到 `infra.jwt`。
- 将 Spring Security 配置、JWT Filter、认证失败和授权失败处理器迁移到 `infra.security`。
- 在 auth 模块内新增 `AuthenticatedSubjectService` 实现 `AuthenticatedSubjectLoader`，负责查询最新用户状态和权限。
- access token 只保存 `subjectId`、`iat`、`exp`、`iss`，不再保存 `username` 或 roles 作为快照。
- `AuthenticatedSubject` 使用 `principalName` 表示认证主体可读名称，当前由 auth 模块用户名填充，但跨模块契约不绑定用户表字段。
- 最终授权以 `AuthenticatedSubjectService` 每次加载到的最新权限为准。

## 备选方案
- 方案 1：继续保留 `modules/auth/security`，只做类名优化。
- 方案 2：只移动包路径，但让 `JwtAuthenticationFilter` 继续依赖 `AuthService`。
- 方案 3：JWT 继续携带 roles，并由 Filter 直接使用 token roles 授权。
- 方案 4：引入 OAuth2 Resource Server 或完整授权服务器。

## 决策理由
- 当前项目仍是单体学习型后端，使用 `AuthenticatedSubjectLoader` 这种窄接口足以解耦安全基础设施与 auth 业务模块。
- 最小 JWT 可以减少 token payload，也能避免 token 内用户名、roles 快照和数据库最新数据之间产生语义混淆。
- 每次请求重新加载用户状态和权限，可以保证用户禁用、角色变化在后续请求中更快生效。
- 自定义 `JwtTokenProvider` 保持了 JWT 与 Spring Security 的解耦，后续迁移到网关、资源服务或独立认证服务时更容易替换。
- OAuth2 Resource Server 更标准，但当前阶段会引入过多额外概念，不利于最小可用闭环和学习复盘。

## 影响
- 好处
  - auth 模块回归注册、登录、用户状态和权限查询等业务职责。
  - `infra.security` 不再直接依赖 auth 实体、VO、枚举或完整业务服务。
  - `JwtTokenProvider` 不依赖 Spring Security，职责更单一。
  - 安全上下文只保留最小认证主体，更适合作为跨模块契约。
  - 用户禁用和角色变化后，后续请求以数据库最新数据为准。
- 代价
  - 每次受保护请求仍需要查询用户和角色，访问量上来后可能带来额外数据库压力。
  - `AuthenticatedSubjectService` 需要维护角色编码到 `ROLE_` authority 的转换规则。
  - 与 2026-04-27 阶段 1 ADR 相比，`username` 和 roles 不再作为 JWT payload 的一部分，需要读者注意这是一次后续演进。
- 后续可能需要调整的地方
  - 在 `AuthenticatedSubjectService` 内加入短 TTL 缓存或 Redis 缓存，并设计角色变更后的失效策略。
  - 引入 refresh token、token 黑名单或登录会话表，实现服务端主动吊销。
  - 微服务阶段将 `AuthenticatedSubjectLoader` 的实现替换为用户中心或权限服务调用。
