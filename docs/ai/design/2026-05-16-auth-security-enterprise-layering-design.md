# 安全认证企业级分层重构设计

## 1. 背景

- 当前安全认证代码已经具备注册、登录、JWT、RBAC 和统一 401/403 响应能力，但包结构仍残留几处边界不清的问题：
  - `common.security` 保存认证主体契约，不符合“common 不再放 security”的目标。
  - JWT 技术能力位于 `infra.jwt`，与目标的 `infra.security.jwt` 不一致。
  - `AuthService` 同时负责账号业务和 token 签发细节，业务语义与 JWT 技术能力仍可进一步拆分。
  - `PasswordEncoder` Bean 放在全局安全配置中，使安全过滤器链配置和密码哈希配置混在一起。
- 本项目后续会从单体模块化继续演进到微服务或云原生形态，因此需要提前把“安全基础设施能力”和“auth 模块业务语义”分清楚。

## 2. 目标

- 将 Spring Security 配置统一收敛到 `infra.security.config.SecurityConfig`。
- 将 JWT 技术能力统一收敛到 `infra.security.jwt`：
  - `JwtProperties`
  - `JwtCodec`
  - `JwtClaims`
  - `JwtAuthenticationFilter`
- 将当前登录用户模型迁移到 `infra.security.principal.AuthenticatedPrincipal`。
- 新增 `infra.security.support.SecurityUtils`，统一从 Spring Security 上下文读取当前用户 ID、用户名、主体和权限。
- 将 `PasswordEncoder` Bean 拆到 `infra.security.support.PasswordEncoderConfig`。
- 将认证主体加载服务迁移到 `modules.auth.security.AuthenticatedPrincipalService`，让它只负责用户状态和角色权限加载。
- 将 auth 应用服务拆成接口与实现：
  - `modules.auth.service.AuthService`
  - `modules.auth.service.TokenService`
  - `modules.auth.service.impl.AuthServiceImpl`
  - `modules.auth.service.impl.TokenServiceImpl`
- 保持现有 API 路径、响应格式、错误码、数据库表结构和核心行为不变。

## 3. 非目标

- 不新增 refresh token、token 黑名单、服务端登出吊销、Redis 会话或登录审计。
- 不调整 `users`、`roles`、`user_roles` 表结构和索引。
- 不重构 system、event、order、payment 等非安全认证模块。
- 不引入 OAuth2 Authorization Server、Spring OAuth2 Resource Server 或网关鉴权。
- 不改变当前 `USER` / `ADMIN` 角色模型。

## 4. 影响范围

- `infra.security.config`
  - 重命名并整理 Spring Security 全局配置。
- `infra.security.jwt`
  - 迁移并改名 JWT 配置、claims、编解码和认证过滤器。
- `infra.security.principal`
  - 新增当前认证主体模型。
- `infra.security.support`
  - 新增安全上下文读取工具和密码编码器配置。
- `modules.auth.security`
  - 新增认证主体加载服务，读取用户、状态和角色。
- `modules.auth.service`
  - 抽出 `AuthService` 与 `TokenService` 接口。
- `modules.auth.service.impl`
  - 放置注册、登录、登出、用户查询和 token 签发实现。
- `modules.auth.controller`
  - 更新当前用户模型与服务接口引用。
- 测试影响
  - 更新 JWT 测试辅助类引用，确保行为不变。
- 不涉及缓存、外部接口和数据库迁移变更。

## 5. 领域建模

- `AuthenticatedPrincipal`
  - 表示 Spring Security 上下文中的当前登录身份。
  - 字段：`userId`、`username`、`authorities`。
  - 不包含邮箱、手机号、头像、完整用户实体、用户响应 VO 或密码哈希。
- `JwtClaims`
  - 表示 access token 中的最小 claims。
  - 当前只保存 `subjectId`，对应 `users.id`。
  - 权限和用户状态不放入 token 作为最终授权依据。
- `AuthenticatedPrincipalService`
  - auth 模块与 Spring Security 之间的交界服务。
  - 负责按用户 ID 加载用户认证信息、校验用户状态、查询角色并构造 `AuthenticatedPrincipal`。
  - 不负责签发 token、处理登录流程、记录登出或复杂注册逻辑。
- `TokenService`
  - auth 模块内的 token 业务语义服务。
  - 决定登录成功后给哪个用户签发 access token，以及 token 中放哪些业务 claim。
- `JwtCodec`
  - JWT 技术能力组件。
  - 只负责生成、解析、验签、过期校验和 claims 提取。

## 6. API 设计

- 对外接口保持不变：
  - `POST /api/v1/auth/register`
  - `POST /api/v1/auth/login`
  - `POST /api/v1/auth/logout`
  - `GET /api/v1/me`
  - `GET /api/v1/admin/users`
  - `PATCH /api/v1/admin/users/{userId}/status`
- 响应结构保持不变：
  - 成功响应继续使用 `ApiResponse.success(...)`。
  - 登录响应继续返回 `accessToken`、`tokenType`、`expiresIn` 和 `user`。
  - 认证失败继续返回 `AUTH-401`。
  - 权限不足继续返回 `AUTH-403`。
- Security 配置继续保持：
  - 注册、登录公开。
  - 系统探活、echo、Actuator health/info、OpenAPI 公开。
  - 登出和 `/api/v1/me` 需要认证。
  - `/api/v1/admin/**` 需要 `ADMIN` 角色。
  - 其他接口默认需要认证。

## 7. 数据设计

- 本次不调整表结构。
- `users.username` 和 `users.email` 唯一约束继续作为注册并发冲突的最终防线。
- `roles.code` 继续保存业务角色编码，例如 `USER`、`ADMIN`。
- `AuthenticatedPrincipalService` 将角色编码转换为 Spring Security 识别的 `ROLE_USER`、`ROLE_ADMIN`。
- JWT 继续只写入 `iss`、`sub`、`iat`、`exp` 和签名，不写入完整用户资料或权限快照。

## 8. 关键流程

- 注册流程：
  - `AuthController.register`
  - `AuthServiceImpl.register`
  - 归一化用户名和邮箱
  - BCrypt 哈希密码
  - 写入用户并绑定默认 `USER` 角色
  - 返回 `UserInfo`
- 登录流程：
  - `AuthController.login`
  - `AuthServiceImpl.login`
  - 查询账号、校验密码、校验用户状态
  - 组装 `UserInfo`
  - 调用 `TokenService.issueAccessToken`
  - `TokenServiceImpl` 构造 `JwtClaims`
  - `JwtCodec` 签发 JWT
  - 返回 `LoginResponse`
- 受保护接口认证流程：
  - `JwtAuthenticationFilter` 读取 `Authorization: Bearer <token>`
  - 调用 `JwtCodec.parseAccessToken`
  - 调用 `AuthenticatedPrincipalService.loadByUserId`
  - 构造 `UsernamePasswordAuthenticationToken`
  - 写入 `SecurityContextHolder`
  - 后续授权规则判断是否可访问目标接口
- 登出流程：
  - `AuthController.logout`
  - 通过 `SecurityUtils` 读取当前 `AuthenticatedPrincipal`
  - 调用 `AuthService.logout`
  - 当前保持无状态 no-op，语义是客户端删除本地 token

## 9. 并发 / 幂等 / 缓存

- 本次重构不新增库存、订单或支付写入流程，因此不引入超卖风险。
- 注册并发策略不变：
  - 服务层预检查提供友好错误。
  - 数据库唯一约束作为最终一致性防线。
- JWT access token 仍是无状态凭证，服务端不存储 token。
- 每次请求实时加载用户状态和角色，不引入缓存：
  - 好处：用户禁用和角色变更能在下一次请求生效。
  - 代价：受保护接口会产生用户与角色查询开销。
- 后续如有性能压力，可以在 `AuthenticatedPrincipalService` 内增加短 TTL 缓存，并明确角色变更和用户禁用后的失效策略。

## 10. 权限与安全

- `AuthenticatedPrincipal` 只保存认证鉴权最小信息，避免把完整用户资料放入 `SecurityContext`。
- `JwtCodec` 不依赖 Spring Security、HTTP 请求或 auth 业务服务。
- `TokenService` 不操作 `SecurityContext`，只表达 auth 模块何时签发和如何组织 token claims。
- `JwtAuthenticationFilter` 负责 HTTP Header、Bearer 判断、JWT 解析、认证对象构造和安全上下文写入。
- `AuthenticatedPrincipalService` 只负责查库加载用户状态与权限，不处理登录、注册、登出和 token 签发。
- `SecurityUtils` 统一封装 `SecurityContextHolder` 读取逻辑，避免业务模块散落直接读取 ThreadLocal 上下文的代码。

## 11. 测试策略

- 编译验证：
  - 运行 `mvn test` 或至少 `mvn -Dtest=AuthIntegrationTest,SystemControllerTest test`，验证包路径迁移和 Bean 装配。
- 集成测试：
  - 注册成功、用户名重复、邮箱重复、并发注册冲突。
  - 登录成功、密码错误、禁用用户不能登录。
  - 无 token 访问受保护接口返回 401。
  - 普通用户访问管理员接口返回 403。
  - 过期 token、篡改 token、禁用用户旧 token 返回 401。
  - 管理员种子账号可访问管理接口。
- 结构检查：
  - 使用 `rg` 确认 `common.security`、`infra.jwt`、旧 `SecurityConfiguration`、旧 `JwtTokenProvider` 无代码引用。
- 文档验证：
  - 同步补充实现说明和 ADR，记录这次分层取舍。

## 12. 风险与替代方案

- 风险：迁移包路径后 import 或 Bean 装配遗漏。
  - 应对：运行编译和认证集成测试。
- 风险：保留每次请求查库会增加数据库压力。
  - 应对：当前阶段以权限即时生效为优先；后续再通过缓存优化。
- 风险：`SecurityUtils` 若被滥用可能让业务代码隐式依赖线程上下文。
  - 应对：优先在 Controller 边界读取当前用户，再显式传入 Service；工具类只封装读取方式，不替代业务参数设计。
- 替代方案 A：只改包名，不拆 `AuthService` 与 `TokenService`。
  - 不采用原因：无法清楚表达“JWT 技术能力”和“auth token 签发业务语义”的区别。
- 替代方案 B：让 JWT 放入用户名、邮箱、角色等完整信息。
  - 不采用原因：会扩大 token 敏感信息面，也会让用户状态和角色变更无法及时反映。
- 替代方案 C：直接引入 OAuth2 Resource Server。
  - 不采用原因：当前仍是单体学习型项目，完整 OAuth2 会显著增加阶段复杂度，不利于最小闭环。
