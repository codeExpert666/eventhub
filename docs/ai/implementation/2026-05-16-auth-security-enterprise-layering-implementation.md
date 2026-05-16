# 安全认证企业级分层重构实现说明

## 1. 本次改动解决了什么问题
- 解决安全认证代码包边界不够清晰的问题：
  - `common.security` 不再保存安全上下文模型。
  - JWT 技术能力从 `infra.jwt` 迁移到 `infra.security.jwt`。
  - Spring Security 配置从 `SecurityConfiguration` 收敛为 `SecurityConfig`。
  - `AuthService` 不再直接依赖 JWT 技术组件签发 token。
- 让 auth 模块形成更清晰的企业级分层：
  - Controller 依赖 `AuthService` 接口。
  - 注册、登录、登出和用户管理逻辑在 `AuthServiceImpl`。
  - token 签发业务语义在 `TokenServiceImpl`。
  - JWT 底层生成、解析、验签和过期校验在 `JwtCodec`。
- 为后续微服务演进预留边界：
  - 当前登录身份统一为 `AuthenticatedPrincipal`。
  - 当前用户读取统一通过 `SecurityUtils`。
  - 认证主体加载集中在 `modules.auth.security.AuthenticatedPrincipalService`。

## 2. 改动内容
- 新增了什么
  - `infra.security.config.SecurityConfig`
  - `infra.security.jwt.JwtProperties`
  - `infra.security.jwt.JwtCodec`
  - `infra.security.jwt.JwtClaims`
  - `infra.security.jwt.JwtAuthenticationFilter`
  - `infra.security.principal.AuthenticatedPrincipal`
  - `infra.security.support.SecurityUtils`
  - `infra.security.support.PasswordEncoderConfig`
  - `modules.auth.security.AuthenticatedPrincipalService`
  - `modules.auth.service.TokenService`
  - `modules.auth.service.impl.AuthServiceImpl`
  - `modules.auth.service.impl.TokenServiceImpl`
- 修改了什么
  - `AuthService` 从具体类改为接口。
  - `AuthController` 的登出逻辑改为通过 `SecurityUtils` 读取当前认证主体，并调用 `authService.logout(...)`。
  - `UserController` 改为通过 `SecurityUtils` 读取当前认证主体。
  - `AuthIntegrationTest` 改为使用 `JwtCodec` 和 `JwtClaims` 构造过期 token。
- 删除了什么
  - `common.security.AuthenticatedSubject`
  - `common.security.AuthenticatedSubjectLoader`
  - `infra.jwt.JwtTokenProvider`
  - `infra.jwt.config.JwtProperties`
  - `infra.jwt.model.AccessTokenClaims`
  - `infra.security.config.SecurityConfiguration`
  - `infra.security.filter.JwtAuthenticationFilter`
  - `modules.auth.service.AuthenticatedSubjectService`

## 3. 为什么这样设计
- `JwtCodec` 只处理 JWT 技术能力，保持和 HTTP、Spring Security、用户表解耦。
- `TokenService` 表达 auth 模块的 token 签发业务语义，避免登录服务直接知道 JWT 具体 claims 组装细节。
- `AuthenticatedPrincipal` 位于 `infra.security.principal`，比放在 `common` 更贴合“安全上下文模型”的职责。
- `AuthenticatedPrincipalService` 位于 `modules.auth.security`，因为它需要读取用户表、角色表和用户状态，是 auth 业务模型与安全框架之间的交界层。
- `SecurityUtils` 统一封装 `SecurityContextHolder` 读取方式，避免 Controller 或业务代码散落类型转换。
- `PasswordEncoderConfig` 独立出来后，`SecurityConfig` 更聚焦过滤器链、认证授权规则和异常处理。
- 当前仍然每次请求查库加载用户状态和角色，优先保证禁用用户和角色变更及时生效。

## 4. 替代方案
- 方案 A：只移动包路径，不拆 `AuthService` 与 `TokenService`。
  - 没有采用，因为无法清晰表达“JWT 怎么签发”和“登录成功后给谁签发什么 token”的职责差异。
- 方案 B：让 JWT 携带用户名、邮箱、角色等完整信息。
  - 没有采用，因为会扩大 token 信息面，也会让权限变化和用户禁用无法及时反映。
- 方案 C：继续使用 `common.security` 保存当前用户模型。
  - 没有采用，因为本次目标明确要求 `common` 不再放 security，同时安全上下文模型本身也更适合放在 `infra.security`。
- 方案 D：直接引入 OAuth2 Resource Server。
  - 没有采用，因为当前仍是单体学习型项目，引入 OAuth2 会显著增加阶段复杂度，不利于最小可用闭环。

## 5. 测试与验证
- 执行过的测试：
  - `mvn test`
    - 结果：第一次失败，原因是删除旧类后未 clean，`target/classes` 中残留旧 `infra.security.filter.JwtAuthenticationFilter`，导致与新 `infra.security.jwt.JwtAuthenticationFilter` 发生同名 Bean 冲突。
  - `mvn clean test`
    - 结果：通过。
    - 测试数：33。
    - 失败数：0。
    - 错误数：0。
- 覆盖到的关键场景：
  - 注册成功、重复用户名、重复邮箱、并发注册唯一约束兜底。
  - 登录成功、密码错误、禁用用户不能登录。
  - 无 token 访问受保护接口返回 401。
  - 普通用户访问管理员接口返回 403。
  - 过期 token 和篡改 token 返回 401。
  - 禁用用户旧 token 返回 401。
  - 管理员种子账号访问管理接口成功。
  - system 公开接口和 OpenAPI/Actuator 公开访问规则保持可用。
- 结构检查：
  - 使用 `rg` 检查源码和测试中不再引用旧 `common.security`、`infra.jwt`、`JwtTokenProvider`、`AccessTokenClaims`、`SecurityConfiguration` 和旧 `infra.security.filter`。

## 6. 已知限制
- 登出仍是无状态 no-op，不会服务端吊销已签发 token。
- access token 仍无 refresh token 配套，过期后需要重新登录。
- 每次受保护请求都查询用户和角色，在高并发场景下会增加数据库压力。
- 当前权限仍是角色级 `USER` / `ADMIN`，尚未扩展到资源级、按钮级或细粒度 permission。
- `SecurityUtils` 已提供统一读取能力，但后续业务服务仍应避免过度依赖线程上下文，优先在 Controller 边界显式传入当前用户 ID。

## 7. 对后续版本的影响
- 对简历可用版的价值
  - 展示清晰的安全认证分层：配置、JWT 技术、当前主体、上下文工具、auth 业务服务各司其职。
  - 展示认证链路中 401、403、RBAC、禁用用户旧 token 失效等企业项目常见安全细节。
- 对微服务 / 云原生演进的影响
  - `JwtCodec` 后续可以替换为网关或资源服务使用的 JWT 校验组件。
  - `TokenService` 后续可以迁移到独立认证服务。
  - `AuthenticatedPrincipalService` 后续可以替换为用户中心 / 权限服务调用或带缓存的权限加载器。
  - `SecurityUtils` 让当前用户读取入口统一，后续接入 trace、审计或异步上下文传播时更容易治理。
