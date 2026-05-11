# Security Matcher 显式化设计

## 1. 背景
- 当前 `SecurityConfiguration` 中部分 `requestMatchers` 只声明路径，没有声明 HTTP Method。
- 在 Spring Security 中，未声明 `HttpMethod` 的 matcher 会匹配所有请求方法，而不是默认只匹配 `GET`。
- 这会让 `/api/v1/system/**`、Actuator、Swagger、favicon 等公开规则的暴露范围比代码表面看起来更宽。
- 本次调整的业务上下文是阶段 1 的 JWT + RBAC 安全链路，不新增认证能力，只把已有接口访问规则表达得更精确。

## 2. 目标
- 梳理当前项目中已经存在的业务接口、基础接口和开发辅助接口。
- 对真实存在且需要公开的接口使用 `HttpMethod + path` 的显式 matcher。
- 对管理员接口列出当前已存在的 GET/PATCH 规则，并保留管理员命名空间兜底保护。
- 保持注册、登录、系统示例、健康检查和 Swagger 文档的现有可访问性。
- 保持其他接口默认需要登录，管理员接口需要 `ADMIN` 角色。

## 3. 非目标
- 本次不新增、删除或改名业务 API。
- 本次不调整 Controller 的 `@GetMapping`、`@PostMapping`、`@PatchMapping`。
- 本次不引入新的角色、权限点、OAuth2、网关或资源服务器。
- 本次不改变 JWT 签发、解析、用户加载、401/403 响应结构。
- 本次不处理 CORS 预检放行策略；当前项目尚未配置跨域访问闭环。

## 4. 影响范围
- `infra.security`
  - 调整 `SecurityConfiguration` 中 URL 级授权规则。
- `modules.system`
  - 现有 `GET /api/v1/system/ping`、`POST /api/v1/system/echo` 保持公开。
  - 其他 `/api/v1/system/**` 方法和路径不再被公开通配规则放行。
- `modules.auth`
  - `POST /api/v1/auth/register`、`POST /api/v1/auth/login` 保持公开。
  - `POST /api/v1/auth/logout` 明确要求登录。
  - `GET /api/v1/me` 明确要求登录。
  - `GET /api/v1/admin/users`、`PATCH /api/v1/admin/users/*/status` 明确要求 `ADMIN`。
- Actuator / OpenAPI
  - `GET /actuator/health`、`GET /actuator/info` 保持公开。
  - `HEAD /actuator/health`、`HEAD /actuator/info` 保持公开，兼容基础探活场景。
  - `GET /v3/api-docs`、`GET /v3/api-docs/**`、`GET /swagger-ui.html`、`GET /swagger-ui/**` 保持公开。
- 不涉及数据库表、缓存、消息队列或外部服务。

## 5. 领域建模
- 本次不新增领域实体。
- 现有安全相关对象继续保持原职责：
  - `AuthenticatedSubject`：当前认证主体摘要。
  - `Authentication`：Spring Security 保存到 `SecurityContext` 的认证结果。
  - `GrantedAuthority`：参与 `hasRole("ADMIN")` 判断的权限集合。
- 访问规则可视为安全领域中的接口资源策略：
  - 公开资源：无需登录即可访问。
  - 登录资源：必须有合法 JWT。
  - 管理资源：必须有合法 JWT 且包含 `ROLE_ADMIN`。

## 6. API 设计
- 公开接口：
  - `POST /api/v1/auth/register`
  - `POST /api/v1/auth/login`
  - `GET /api/v1/system/ping`
  - `POST /api/v1/system/echo`
  - `GET /actuator/health`
  - `HEAD /actuator/health`
  - `GET /actuator/info`
  - `HEAD /actuator/info`
  - `GET /v3/api-docs`
  - `GET /v3/api-docs/**`
  - `GET /swagger-ui.html`
  - `GET /swagger-ui/**`
  - `GET /favicon.ico`
- 登录接口：
  - `POST /api/v1/auth/logout`
  - `GET /api/v1/me`
- 管理员接口：
  - `GET /api/v1/admin/users`
  - `PATCH /api/v1/admin/users/*/status`
  - `/api/v1/admin/**` 保留 `ADMIN` 兜底，覆盖未来新增管理接口。
- 错误场景：
  - 未登录访问登录资源或管理资源，返回 401。
  - 普通用户访问管理资源，返回 403。
  - 未显式公开的请求方法会继续向后匹配，通常落到 `anyRequest().authenticated()`。

## 7. 数据设计
- 本次不调整表结构。
- 本次不新增索引、唯一约束或迁移脚本。
- 角色数据仍通过现有用户、角色、用户角色关系加载，并在认证过滤器中转换为 `ROLE_` 前缀权限。
- 数据一致性策略不变：每次受保护请求仍加载最新用户状态和角色。

## 8. 关键流程
- 公开接口访问流程：
  - 请求进入 Spring Security Filter Chain。
  - JWT Filter 如果没有 token 会直接放行到后续过滤器。
  - 授权规则命中对应 `HttpMethod + path` 的 `permitAll()`。
  - 请求进入 Controller 或 Actuator / Swagger 处理链路。
- 登录接口访问流程：
  - JWT Filter 成功解析 Bearer token 并写入 `SecurityContext`。
  - 授权规则命中 `authenticated()` 或兜底 `anyRequest().authenticated()`。
  - Controller 通过 `@AuthenticationPrincipal` 获取当前主体。
- 管理接口访问流程：
  - JWT Filter 写入包含 `ROLE_ADMIN` 的 `Authentication`。
  - URL 级规则命中 `hasRole("ADMIN")`。
  - Controller 上的 `@PreAuthorize("hasRole('ADMIN')")` 继续作为方法级防线。
- 异常流程：
  - 没有 token 或 token 无效时返回 401。
  - 已登录但缺少 `ROLE_ADMIN` 时返回 403。

## 9. 并发 / 幂等 / 缓存
- 本次只调整请求授权规则，不新增写入业务流程。
- 不涉及库存扣减、订单状态、支付回调，因此没有新的超卖、重复提交或状态并发风险。
- 不引入缓存；权限判断仍基于请求时加载的当前认证信息。
- 对已有无状态 JWT 模式没有影响，每个请求仍独立完成认证与授权判断。

## 10. 权限与安全
- 公开接口从路径通配改为方法级显式公开，降低误暴露范围。
- 管理员接口采用双层防护：
  - URL 级 `hasRole("ADMIN")`。
  - Controller 级 `@PreAuthorize("hasRole('ADMIN')")`。
- `/api/v1/admin/**` 兜底规则故意保持方法不限，因为它保护的是整个管理命名空间，而不是公开接口。
- `anyRequest().authenticated()` 继续作为默认安全边界，避免新增业务接口默认裸奔。
- 对公开接口携带无效 JWT 的行为不变：JWT Filter 会优先返回 401。

## 11. 测试策略
- 单元测试：
  - 本次不新增纯单元测试，因为变更点是 Spring Security Filter Chain 规则组合。
- 集成测试：
  - 运行 `SystemControllerTest`，验证系统公开接口、Actuator、OpenAPI 仍可访问。
  - 运行 `AuthIntegrationTest`，验证注册、登录、当前用户、管理员 RBAC 仍可用。
- 接口验证：
  - 新增或补充未显式公开方法的请求验证，例如未登录访问非公开系统方法应返回 401。
  - 新增或补充登出、当前用户等登录接口的认证要求验证。
- 异常场景验证：
  - 未登录返回 401。
  - 角色不足返回 403。
  - 过期或篡改 token 返回 401。

## 12. 风险与替代方案
- 风险：把公开路径改成方法级 matcher 后，错误方法请求可能先被安全层拦截为 401，而不是进入 MVC 后返回 405。
  - 应对：当前优先保证公开面最小化；如后续需要严格区分 401/405，可专门设计公开接口的错误方法处理策略。
- 风险：Swagger 或健康检查如果依赖非 GET/HEAD 方法，可能被要求登录。
  - 应对：当前 Springdoc 和 Actuator 基础读取场景使用 GET；健康检查额外放行 HEAD。
- 替代方案 A：继续使用路径级 `permitAll()`。
  - 不采用原因：表达不够精确，容易误以为默认只匹配 GET，且会扩大公开面。
- 替代方案 B：删除管理员命名空间兜底，只列出现有管理接口。
  - 不采用原因：未来新增 `/api/v1/admin/**` 接口时，如果忘记补安全配置，可能落到普通登录即可访问。
- 替代方案 C：完全依赖 Controller 上的 `@PreAuthorize`。
  - 不采用原因：URL 级规则能在进入 Controller 前拦截，配合方法级权限形成更清晰的双层防护。
