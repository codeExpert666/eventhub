# ADR：阶段 1 使用自定义 JWT Filter 并在认证时校验用户状态

## 标题
阶段 1 使用自定义 JWT Filter 并在认证时校验用户状态

## 状态
- superseded

该决策已被 `docs/ai/adr/2026-04-29-auth-security-boundary-refactor.md` 取代。本文保留阶段 1 初始实现的历史取舍，当前实现以 2026-04-29 ADR 中的最小 JWT 和 auth/security 边界重构为准。

## 背景
阶段 1 需要完成注册登录、JWT 与 RBAC。项目当前仍处于单体学习型后端阶段，目标是用最小可用闭环建立认证和权限边界，同时让后续活动、订单、支付等模块能复用统一的当前用户上下文。

JWT 的典型优势是无状态，但完全信任 token claims 会带来一个明显问题：如果用户被管理员禁用，旧 token 在过期前仍可能继续访问受保护接口。对于票务平台来说，禁用用户应尽快失去访问能力，因此需要在性能、复杂度和安全即时性之间做取舍。

## 决策
阶段 1 选择：

- 使用 Spring Security Filter Chain 接入自定义 JWT 认证过滤器。
- JWT 中保留 `sub`、`username`、`roles`、`iat`、`exp`、`iss`。
- 每次受保护请求解析 token 后，根据 `sub` 查询数据库，确认用户存在且状态为 `ENABLED`。
- 暂不引入 refresh token、Redis token 黑名单或 OAuth2 授权服务器。

## 备选方案
- 方案 1：完全信任 JWT claims，不在请求期查库。
- 方案 2：引入 refresh token，并用 Redis 维护 refresh token 与 access token 黑名单。
- 方案 3：直接接入 Spring OAuth2 Resource Server 或完整授权服务器模型。

## 决策理由
- 当前项目阶段优先学习和演示认证授权主链路，自定义 Filter 更容易看清请求如何进入安全上下文。
- 数据库即时校验能保证禁用用户下一次请求立即失效，避免无状态 JWT 带来的权限滞后。
- 阶段 1 的访问规模较小，每次请求一次用户查询的成本可接受。
- Redis 黑名单和 refresh token 会引入更多状态管理、过期清理和并发边界，不适合作为最小闭环第一步。
- 完整 OAuth2 授权服务器更标准，但明显超出当前路线图阶段目标。

## 影响
- 好处：
  - 禁用用户即时失效。
  - 认证链路清晰，便于学习、测试和面试表达。
  - 与当前单体模块化结构匹配，不提前拆分认证服务。
- 代价：
  - 每次受保护请求会增加一次数据库读取。
  - logout 只能表达客户端删除 token，不能服务端立即吊销已签发 access token。
  - 角色变更后，旧 token 内的 roles claims 可能和数据库最新角色有短期差异；当前阶段主要以用户禁用即时生效作为安全兜底。
- 后续可能需要调整的地方：
  - 引入 refresh token 和 Redis 黑名单，实现服务端 token 撤销。
  - 为用户权限增加短 TTL 缓存，降低数据库读取压力。
  - 在微服务阶段改为统一认证中心或 OAuth2/OIDC 方案。
