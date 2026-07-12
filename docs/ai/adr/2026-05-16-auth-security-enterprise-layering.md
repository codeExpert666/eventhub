# ADR：安全认证代码按 infra.security 与 modules.auth 分层

## 标题
安全认证代码按 infra.security 与 modules.auth 分层

## 状态
- accepted

## 背景
当前项目已经具备注册、登录、JWT 和 RBAC 能力，但安全认证代码仍存在边界不够清晰的问题：

- 当前认证主体放在 `common.security`，而本次目标要求 `common` 不再承载 security。
- JWT 技术能力放在 `infra.jwt`，没有归入 Spring Security 相关基础设施命名空间。
- `AuthService` 直接依赖 JWT 组件，登录业务和 token 签发技术细节耦合。
- `PasswordEncoder` Bean 放在 Spring Security 过滤器链配置中，配置类职责偏宽。
- 认证主体加载服务命名和位置仍沿用 `AuthenticatedSubjectService`，和目标的 `AuthenticatedPrincipalService` 不一致。

为了让项目更贴近企业级分层，也为后续从单体演进到微服务做准备，需要重新划分安全基础设施与 auth 业务模块的边界。

## 决策
最终选择：

- 将 Spring Security 配置放入 `infra.security.config.SecurityConfig`。
- 将 JWT 技术能力放入 `infra.security.jwt`：
  - `JwtProperties`
  - `JwtCodec`
  - `JwtClaims`
  - `JwtAuthenticationFilter`
- 将当前登录主体模型放入 `infra.security.principal.AuthenticatedPrincipal`。
- 将安全上下文读取封装放入 `infra.security.support.SecurityUtils`。
- 将 `PasswordEncoder` Bean 放入 `infra.security.support.PasswordEncoderConfig`。
- 将认证主体加载服务放入 `modules.auth.security.AuthenticatedPrincipalService`。
- 将 auth 应用服务拆成接口与实现：
  - `AuthService`
  - `TokenService`
  - `AuthServiceImpl`
  - `TokenServiceImpl`
- access token 继续只保存最小主体 ID，不把完整用户资料或角色快照作为最终授权依据写入 JWT。

## 备选方案
- 方案 1：只移动包路径，不拆服务接口和实现。
- 方案 2：继续保留 `common.security` 作为跨模块安全契约。
- 方案 3：让 JWT 携带完整用户资料和角色权限，减少每次请求查库。
- 方案 4：直接接入 OAuth2 Resource Server 或独立认证服务。

## 决策理由
- `infra.security` 更适合承载 Spring Security、JWT、当前主体和上下文工具，避免 `common` 变成安全杂物包。
- `JwtCodec` 与 `TokenService` 分离后，技术能力和业务语义清晰：
  - `JwtCodec` 负责“怎么生成和解析 JWT”。
  - `TokenService` 负责“什么时候生成、给谁生成、生成什么内容”。
- `AuthenticatedPrincipalService` 放在 auth 模块内，可以自然访问用户表、角色表和用户状态，同时避免把这些查询逻辑塞进 Filter 或 JwtCodec。
- `AuthenticatedPrincipal` 只保存用户 ID、用户名和 authorities，能降低安全上下文的信息面。
- 每次请求实时加载用户状态和角色，可以让禁用用户和权限变更在下一次请求生效，安全性优先于早期性能优化。
- 当前项目仍处于单体学习型阶段，直接引入 OAuth2 或独立认证服务会过早增加复杂度。

## 影响
- 好处
  - 安全认证目录结构更贴近企业项目分层。
  - auth 业务服务和 JWT 技术组件职责更清楚。
  - Controller 依赖服务接口，后续替换实现成本更低。
  - 当前用户模型更轻量，避免安全上下文持有完整业务对象。
  - 后续接入 token 黑名单、refresh token、权限缓存或独立认证服务时，有明确扩展点。
- 代价
  - 文件迁移和类名变化较多，需要依赖编译和集成测试兜底。
  - `JwtAuthenticationFilter` 当前仍依赖 auth 模块的 `AuthenticatedPrincipalService`，这是单体阶段的现实交界点；微服务阶段需要替换为远程权限加载或资源服务本地校验策略。
  - 每次请求实时查库会带来额外开销，后续高并发场景需要缓存和失效策略。
- 后续可能需要调整的地方
  - 引入 refresh token 和服务端登录会话表。
  - 引入 token 黑名单或 token version，实现主动吊销。
  - 在 `AuthenticatedPrincipalService` 内增加短 TTL 缓存，并处理用户禁用和角色变更后的缓存失效。
  - 微服务阶段将 `TokenService` 独立到认证服务，将 `JwtAuthenticationFilter` 调整为资源服务侧校验器。
