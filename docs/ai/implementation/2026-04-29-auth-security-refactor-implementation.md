# Auth Security 边界重构实现说明

## 1. 本次改动解决了什么问题

本次改动解决了 `modules/auth/security` 包职责过重的问题。

重构前，该包同时包含 Spring Security 配置、JWT 签发解析、认证主体模型、认证失败与授权失败响应处理，以及用户加载逻辑。这样会导致 auth 业务模块承担全局基础设施职责，也会让安全过滤器直接依赖 `AuthService`。

重构后，安全相关代码按职责拆分为：

- `common.security`：跨模块认证主体契约。
- `infra.jwt`：JWT 配置、claims 模型、签发与解析。
- `infra.security`：Spring Security 配置、JWT Filter、401/403 响应写出。
- `modules.auth`：注册、登录、当前用户资料查询、用户状态和权限加载。

## 2. 改动内容
- 新增了什么
  - 新增 `AuthenticatedSubject`，表示最小认证主体。
  - 新增 `AuthenticatedSubjectLoader`，作为 `infra.security` 加载当前主体的窄接口。
  - 新增 `AccessTokenClaims`，表示 access token 中的最小身份声明。
  - 新增 `JwtTokenProvider`，负责 access token 签发、解析、验签、issuer 和过期时间校验。
  - 新增 `AuthenticatedSubjectService`，在 auth 模块内实现用户状态校验和最新权限加载。
  - 新增本次设计文档与 ADR。
- 修改了什么
  - `AuthService` 改为依赖 `JwtTokenProvider` 签发 token，不再向 Filter 提供完整认证用户加载方法。
  - `AccessTokenClaims` 收缩为只保存 `subjectId`，access token 不再写入用户名快照。
  - `AuthenticatedSubject.username` 调整为 `principalName`，表达跨模块认证主体的可读名称，而不是绑定用户表字段。
  - `/api/v1/me` 改为根据 `AuthenticatedSubject.subjectId()` 回到 auth 服务查询最新 `UserInfo`。
  - `AuthIntegrationTest` 改为使用 `JwtTokenProvider` 和 `AccessTokenClaims` 构造过期 token。
  - `AuthController` 和 `UserController` 的 `@AuthenticationPrincipal` 类型改为 `AuthenticatedSubject`。
- 删除了什么
  - 删除 `modules/auth/security` 包中的旧安全类：
    - `AuthenticatedUser`
    - `JsonAccessDeniedHandler`
    - `JsonAuthenticationEntryPoint`
    - `JwtAuthenticationFilter`
    - `JwtPrincipalClaims`
    - `JwtProperties`
    - `JwtTokenService`
    - `SecurityConfig`
    - `SecurityExceptionResponseWriter`

## 3. 为什么这样设计
- 关键设计原因
  - `infra.security` 只依赖 `AuthenticatedSubjectLoader`，不依赖 `AuthService`、`UserEntity`、`UserInfo` 或 `UserStatus`，安全基础设施和 auth 业务实现边界更清晰。
  - `JwtTokenProvider` 只处理 token 本身，不处理 HTTP、SecurityContext 或权限判断，方便后续在网关、微服务或其他入口复用。
  - `AuthenticatedSubject` 只保存 `subjectId`、`principalName`、`authorities`，避免把邮箱、状态、角色实体等业务数据放进安全上下文。
  - JWT 中不再保存用户名或角色快照作为授权依据，每次请求通过 `AuthenticatedSubjectService` 查询最新状态、主体名称和权限，用户禁用后旧 token 仍会立即失效。
- 与项目当前阶段的匹配点
  - 阶段 1 仍是单体后端，使用窄接口解耦已经足够，不需要提前引入复杂认证中心。
  - 项目目标是学习型/简历型，清晰的包边界比一次性堆叠更多安全功能更有价值。
  - 保留原有 API 行为和集成测试语义，降低重构对演示闭环的影响。

## 4. 替代方案
- 方案 A：只移动类包名，保留旧的 `AuthenticatedUser` 和 `AuthService.loadAuthenticatedUser`
  - 没有采用，因为 Filter 仍会直接依赖完整 auth 服务，无法真正隔离基础设施层和业务层。
- 方案 B：JWT 继续携带 roles，并让 Filter 直接使用 token roles 授权
  - 没有采用，因为角色变更和用户禁用无法及时影响旧 token，不符合本次验收标准。
- 方案 C：引入 OAuth2 Resource Server 或完整授权服务器
  - 没有采用，因为当前阶段仍以单体认证授权闭环为主，引入完整 OAuth2 会明显增加学习和配置成本。
- 方案 D：立即为 `AuthenticatedSubjectService` 增加 Redis 缓存
  - 没有采用，因为当前访问规模和阶段目标还不需要缓存，过早加入缓存会引入失效策略和一致性问题。

## 5. 测试与验证
- 已执行：
  - `mvn -q -DskipTests compile`
  - `mvn -q -Dtest=AuthIntegrationTest test`
  - `git diff --check`
  - `mvn -q test`
  - `rg` 检查源码中是否仍残留 `modules.auth.security` 引用，结果无残留。
  - `rg` 检查 `infra.security`、`infra.jwt`、`common.security` 是否反向 import `modules.auth`，结果无反向依赖。
- 重点验证场景：
  - 注册、登录仍可用。
  - 未登录访问 `/api/v1/me` 返回 401。
  - 普通用户访问 `/api/v1/admin/users` 返回 403。
  - 过期 token、篡改 token 返回 401。
  - 用户禁用后，旧 token 访问受保护接口返回 401。

## 6. 已知限制
- 当前 access token 仍是无状态 token，`/logout` 仍表达客户端删除 token，服务端暂不支持主动吊销已签发 token。
- 每次受保护请求都会查询用户和角色，后续高并发下可能需要在 `AuthenticatedSubjectService` 内部增加短 TTL 缓存。
- 当前权限仍是角色级别的 `ROLE_USER` / `ROLE_ADMIN`，尚未细化到权限点或资源级授权。

## 7. 对后续版本的影响
- 对简历可用版的价值
  - 包边界更接近企业项目：业务模块、基础设施、安全契约分离清楚。
  - 可以清楚讲述 JWT、Spring Security、当前用户上下文、实时权限加载之间的关系。
- 对微服务 / 云原生演进的影响
  - `common.security` 可以作为未来模块间共享的安全上下文契约雏形。
  - `infra.jwt` 与 Spring Security 解耦后，更容易迁移到网关校验、资源服务校验或独立认证服务。
  - `AuthenticatedSubjectLoader` 为后续缓存、远程用户中心或权限服务调用留下了替换点。
