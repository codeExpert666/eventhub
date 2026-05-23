# 阶段 1 决策与取舍

## 1. 单体内 auth 模块，而不是独立认证服务

决策：阶段 1 在当前 Spring Boot 单体中新增 `modules.auth`，不拆独立认证服务。

原因：

- 当前项目仍处于学习型、简历型单体阶段，优先建立业务闭环。
- 用户、活动、订单、支付尚未形成稳定跨服务边界，提前拆服务会把复杂度转移到部署、服务调用和一致性上。
- 通过 `modules.auth` 已经能保持较清晰的业务边界，后续仍可演进为多模块单体或独立 auth 服务。

代价：

- 认证、用户和权限仍与主应用同进程部署。
- 微服务阶段需要重新设计 token 校验、用户权限查询和上下文透传。

面试表达：

> 我没有一开始就拆认证服务，而是在单体里先做清晰模块边界。阶段 1 的重点是把注册、登录、JWT、RBAC 和当前用户上下文跑通，等活动、订单、支付边界稳定后再评估服务拆分。

来源：[阶段 1 认证、JWT 与 RBAC 设计](../../ai/design/2026-04-27-stage-1-auth-jwt-rbac-design.md)

## 2. 最小 JWT，不把角色作为最终授权依据

决策：access token 只保存 `sub` 对应的用户 ID，以及 `iss`、`iat`、`exp` 等标准 claims，不把用户名、邮箱、角色列表作为最终授权依据写入 token。

原因：

- 角色和用户状态会变化，token 内快照容易过期。
- 用户禁用后，应在下一次请求尽快失去访问能力。
- 最小 claims 降低 token 信息面，也减少“token roles 是否可信”的语义混乱。

代价：

- 每次受保护请求都需要查库加载用户状态和角色。
- 高并发场景下需要增加缓存、权限版本或其他优化。

面试表达：

> 我把 JWT 当作身份凭证，不把它当作最终权限数据库。token 里只放用户 ID，每次请求再查当前用户状态和角色，这样用户被禁用或角色变更后，不需要等 token 过期才生效。

来源：[拆分 auth 安全基础设施并采用最小 JWT ADR](../../ai/adr/2026-04-29-auth-security-boundary-refactor.md)

## 3. 自定义 JWT Filter，而不是直接上完整 OAuth2

决策：阶段 1 使用自定义 `JwtAuthenticationFilter` 接入 Spring Security。

原因：

- 自定义 Filter 更容易学习和展示请求如何从 Header token 变成 `SecurityContext`。
- 当前只需要平台自有账号登录和 USER / ADMIN 角色边界。
- OAuth2 Resource Server 或完整授权服务器更标准，但会引入更重的概念和配置。

代价：

- 需要自己维护 token 解析异常、401 响应、认证主体加载和测试。
- 后续如果接入第三方登录或多客户端授权，需要重新评估 OAuth2 / OIDC。

面试表达：

> 当前阶段我选择自定义 JWT Filter，是为了先把认证链路讲清楚：读 Header、验 token、查用户、构造 Authentication、进入授权规则。后续如果项目进入开放平台或多客户端授权，再切到 OAuth2 更合适。

来源：[阶段 1 使用自定义 JWT Filter 并在认证时校验用户状态 ADR](../../ai/adr/2026-04-27-stage-1-jwt-security-boundary.md)

## 4. 安全基础设施放在 infra.security，账号业务放在 modules.auth

决策：将 Spring Security 配置、JWT 编解码、Filter、当前主体和安全异常处理收敛到 `infra.security`，将注册、登录、用户状态和角色加载保留在 `modules.auth`。

原因：

- `infra.security` 是横切基础设施，不应被 auth 业务细节污染。
- `JwtCodec` 只处理 JWT 技术能力，`TokenService` 表达 auth 模块何时签发 token。
- `AuthenticatedPrincipalService` 放在 auth 模块内，因为它必须访问用户表和角色表。
- Controller 依赖 `AuthService` 接口，后续替换实现更容易。

代价：

- 文件和类的数量比初始实现更多。
- `JwtAuthenticationFilter` 当前仍依赖 auth 模块的 `AuthenticatedPrincipalService`，这是单体阶段的现实交界点。

面试表达：

> 我把“怎么验 JWT”和“登录成功后给谁签 token”拆开了。`JwtCodec` 是技术组件，`TokenService` 是业务语义；安全上下文只保存最小当前用户，真实用户资料仍由 auth 服务查询。

来源：[安全认证代码按 infra.security 与 modules.auth 分层 ADR](../../ai/adr/2026-05-16-auth-security-enterprise-layering.md)

## 5. 无状态 logout，暂不做 token 黑名单

决策：`POST /api/v1/auth/logout` 保留接口语义，但服务端不保存 access token，也不主动吊销已签发 token。

原因：

- 阶段 1 先实现 access token 最小闭环。
- 引入黑名单需要 Redis、过期清理、token id、登出幂等和并发刷新等设计。
- 当前 access token 有过期时间，登出接口先表达客户端删除本地 token 的协议语义。

代价：

- 用户登出后，旧 token 在过期前理论上仍可使用。
- 管理员无法单独吊销某个 token。

面试表达：

> 当前 logout 是无状态语义，成功响应代表客户端应删除 token。这个方案简单，但不能服务端主动吊销，所以我在文档中明确了后续要补 refresh token、黑名单或 token version。

来源：[阶段 1 认证、JWT 与 RBAC 实现说明](../../ai/implementation/2026-04-27-stage-1-auth-jwt-rbac-implementation.md)

## 6. RBAC 只做到角色级，不提前做细粒度权限

决策：阶段 1 只建模 USER / ADMIN 两个角色，不引入权限点表、菜单表、按钮权限或数据范围权限。

原因：

- 当前业务还没有活动、订单、票种等资源对象，过早设计细粒度权限容易空转。
- USER / ADMIN 足以验证 Spring Security、JWT、当前用户和管理端边界。
- `roles` 和 `user_roles` 的多对多结构为后续扩展角色和权限关系预留了空间。

代价：

- 不能表达“某管理员只能管理某类活动”这类数据权限。
- 后续权限复杂后需要引入 permissions、role_permissions 或资源级授权策略。

面试表达：

> 我先做角色级 RBAC，是为了支撑当前最重要的普通用户和管理端边界。权限点和数据范围要等活动、订单这些资源模型稳定后再设计，否则容易为了权限系统而权限系统。

来源：[阶段 1 认证、JWT 与 RBAC 设计](../../ai/design/2026-04-27-stage-1-auth-jwt-rbac-design.md)

## 7. 注册并发用数据库唯一约束兜底

决策：注册时先检查用户名和邮箱是否存在，再依赖数据库唯一约束兜底并发重复。

原因：

- 服务层预检查可以返回更友好的业务错误。
- 并发请求可能同时通过预检查，最终必须由 `uk_users_username` 和 `uk_users_email` 保证正确性。
- 捕获 `DuplicateKeyException` 后转成稳定业务异常，避免底层数据库错误泄露给客户端。

代价：

- 预检查和插入之间仍有竞态窗口。
- 并发重复注册时，失败响应可能是统一账号重复，而不是精确区分用户名或邮箱。

面试表达：

> 注册接口不能只靠服务层 exists 判断，因为并发下两个请求可能同时通过。我的做法是 exists 用于友好提示，唯一索引用于最终正确性，插入冲突再统一转换成业务错误。

来源：[UserEntity 持久化模型收敛设计](../../ai/design/2026-05-14-user-entity-persistence-model-design.md)

## 8. 默认角色绑定必须校验影响行数

决策：注册事务内写入 `user_roles` 时，`RoleMapper.addRoleToUser` 返回 `int`，服务层校验影响行数必须为 1。

原因：

- 默认 USER 角色是注册闭环的关键不变量。
- 如果用户已创建但角色关系未写入，会导致后续授权行为异常。
- MyBatis `<insert>` 天然可以返回影响行数，增加校验成本很低。

代价：

- 如果未来改成 `INSERT IGNORE` 或 upsert，影响行数为 0 的语义需要重新定义。

面试表达：

> 我没有让角色绑定方法返回 void，而是校验 SQL 影响行数。因为用户创建成功但没有默认角色是半完成状态，必须在同一个事务内失败回滚。

来源：[用户角色绑定影响行数校验设计](../../ai/design/2026-05-15-user-role-binding-affected-rows-design.md)

## 9. Security matcher 显式声明 HTTP Method

决策：公开接口使用 `HttpMethod + path` 显式 matcher，管理员接口保留 `/api/v1/admin/**` 兜底保护。

原因：

- Spring Security 中不指定 HTTP Method 的 matcher 会匹配所有方法。
- 公开 `/api/v1/system/**` 这种路径通配容易让未来新增接口意外裸奔。
- 管理员命名空间需要默认强保护，避免未来新增管理接口时忘记补规则。

代价：

- 某些错误方法请求可能先被安全层返回 401，而不是进入 MVC 返回 405。
- 安全配置更长，需要测试保证 OpenAPI、Actuator、系统接口仍可访问。

面试表达：

> 我把公开接口从路径通配改成了方法级 matcher。公开白名单越精确，未来新增接口越不容易被历史配置意外放行；管理员命名空间则相反，要有兜底保护。

来源：[Security Matcher 显式化设计](../../ai/design/2026-05-11-explicit-security-matchers-design.md)

## 10. 管理员用户列表从 findAll 演进为分页筛选

决策：`GET /api/v1/admin/users` 返回 `PageResponse<UserInfo>`，支持分页、筛选、新注册用户优先排序和角色批量查询。

原因：

- 管理端列表不能无限制 `findAll`。
- 用户列表天然需要分页、筛选和稳定排序。
- 逐用户查询角色会形成 N+1，当前页角色可以一次批量查回。
- `PageRequest` / `PageResponse` 可以复用到后续活动、订单、票种列表。

代价：

- `COUNT(*)`、`LIKE '%xxx%'` 和缺少组合索引在大数据量下会有成本。
- 分页总数、当前页数据和角色批量查询之间不是同一个事务快照。

面试表达：

> 管理员用户列表我没有停留在 findAll，而是做了分页、筛选、排序和 N+1 优化。这个接口能体现列表查询从能用到可维护的演进。

来源：[管理员用户分页查询设计](../../ai/design/2026-05-18-admin-user-pagination-design.md)

## 11. 生产环境默认关闭 OpenAPI 与 Swagger UI

决策：dev/test 保留 OpenAPI 和 Swagger UI，prod profile 默认关闭文档端点，并且 Security 不再公开放行文档路径。

原因：

- OpenAPI JSON 会暴露接口路径、请求响应模型、枚举值和管理端入口。
- 生产环境的接口文档不是业务能力，默认关闭比登录后开放更符合最小暴露原则。
- 保留 dev/test 文档能力，兼顾学习、联调和自动化测试。

代价：

- 生产临时排障不能直接依赖 Swagger UI。
- 如果未来需要内部文档入口，要单独设计网关、内网、VPN 或管理端口策略。

面试表达：

> 我把 OpenAPI 按环境处理，开发测试开放，生产默认关闭，并让 Security 白名单跟 springdoc 开关联动。这样既保留联调效率，也避免生产暴露接口契约。

来源：[生产环境默认关闭 OpenAPI 与 Swagger UI ADR](../../ai/adr/2026-05-23-prod-openapi-hardening.md)
