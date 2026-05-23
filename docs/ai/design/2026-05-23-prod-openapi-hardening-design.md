# 生产环境 OpenAPI 暴露面加固设计

## 1. 背景
- Spring Boot 3.5 升级后，springdoc 在启动日志中提示生产环境可关闭 Swagger UI / OpenAPI 文档端点。
- 当前项目默认公开 `GET /v3/api-docs`、`GET /v3/api-docs/**`、`GET /swagger-ui.html`、`GET /swagger-ui/**`，这对本地开发、学习演示和接口联调有价值。
- 生产环境如果继续公开接口文档，可能向外部调用方或扫描器暴露接口路径、请求响应模型、枚举值、管理端入口等实现细节。
- 本次安全加固的核心是按环境收敛文档暴露面：开发和测试继续可用，生产默认关闭。

## 2. 目标
- 在 `prod` profile 下关闭 springdoc 的 OpenAPI JSON 与 Swagger UI 端点。
- 在 `prod` profile 下撤掉 Spring Security 对 OpenAPI / Swagger UI 路径的公开放行。
- 保持 `dev` / `test` 下现有 OpenAPI 测试与联调体验不变。
- 补充测试，证明开发/测试文档仍可访问，生产 profile 下未认证访问文档路径会被安全层拦截，已认证访问也不会返回文档资源。
- 更新 README 和 `docs/ai/` 文档，让生产安全姿态可复盘。

## 3. 非目标
- 本次不移除 `springdoc-openapi-starter-webmvc-ui` 依赖。
- 本次不调整 Controller 上的 OpenAPI 注解。
- 本次不引入网关、独立管理端口、IP 白名单或外部认证代理。
- 本次不改变业务 API、JWT 签发解析、RBAC 角色模型或 Actuator `health/info` 公开策略。
- 本次不新增数据库表、缓存或消息流程。

## 4. 影响范围
- `backend/src/main/resources/application.yml`
  - 显式声明 OpenAPI JSON 与 Swagger UI 在非生产默认启用。
- `backend/src/main/resources/application-prod.yml`
  - 覆盖 springdoc 配置，生产关闭 OpenAPI JSON 与 Swagger UI。
- `backend/src/main/java/com/eventhub/infra/security/config/SecurityConfig.java`
  - 按 springdoc 开关决定是否注册文档路径的 `permitAll()` 白名单。
- `backend/src/test`
  - 保留现有 `test` profile 的 OpenAPI 可用性测试。
  - 新增 `prod` profile 下文档路径未认证访问返回 401 的集成测试。
  - 新增 `prod` profile 下携带合法 JWT 访问文档路径仍返回 404 的集成测试。
- `README.md`
  - 标注 Swagger / OpenAPI 是 dev/test 联调入口，生产 profile 默认关闭。
- 不涉及数据表、索引、缓存、外部接口契约变更。

## 5. 领域建模
- 本次不新增业务领域实体。
- 可把文档端点视为基础设施资源：
  - OpenAPI JSON：接口契约描述资源，默认路径 `GET /v3/api-docs`。
  - Swagger UI：浏览器调试资源，入口 `GET /swagger-ui.html` 与静态资源路径 `/swagger-ui/**`。
  - Security 白名单：决定未认证请求是否可以进入对应资源处理链路。
- 环境状态：
  - `dev` / `test`：文档资源启用，安全层允许未认证访问。
  - `prod`：文档资源关闭，安全层不再允许未认证访问。

## 6. API 设计
- `dev` / `test` profile：
  - `GET /v3/api-docs` 返回 200，并包含当前应用的 OpenAPI 文档。
  - `GET /swagger-ui.html` 与 `/swagger-ui/**` 可用于浏览器联调。
- `prod` profile：
  - `GET /v3/api-docs` 未携带 JWT 时返回 401。
  - `GET /swagger-ui.html` 未携带 JWT 时返回 401。
  - `GET /v3/api-docs` 携带合法 JWT 时返回 404，不提供 OpenAPI JSON。
  - `GET /swagger-ui.html` 携带合法 JWT 时返回 404，不提供 Swagger UI 页面。
- 错误响应：
  - 未认证访问生产文档路径复用现有 Spring Security 401 统一响应，`code=AUTH-401`。
  - 已认证访问生产文档路径进入 MVC 后复用静态资源缺失的统一 404 响应，`code=COMMON-404`。

## 7. 数据设计
- 本次不调整数据库表结构。
- 本次不新增索引、唯一约束或 Flyway 脚本。
- 本次不改变用户、角色、用户角色关系的数据读取方式。
- 数据一致性策略不变，文档端点开关只影响基础设施资源暴露，不影响业务数据。

## 8. 关键流程
- 非生产访问 OpenAPI：
  - 请求进入 Spring Security Filter Chain。
  - Security 根据 springdoc 开关注册的 matcher 命中文档路径 `permitAll()`。
  - 请求进入 springdoc 处理链路并返回文档或 Swagger UI 资源。
- 生产未认证访问 OpenAPI：
  - 请求进入 Spring Security Filter Chain。
  - 由于生产配置关闭 springdoc，Security 不注册文档路径 `permitAll()`。
  - 请求落入 `anyRequest().authenticated()`。
  - 未携带合法 JWT 时由认证失败处理器返回 401。
- 生产已认证访问 OpenAPI：
  - 请求先通过 JWT 认证。
  - 请求进入 MVC 后，springdoc 端点因 `enabled=false` 不提供文档内容。
  - Spring MVC 识别为资源不存在，并由全局异常处理返回统一 404 响应。

## 9. 并发 / 幂等 / 缓存
- 本次没有写操作，不涉及幂等、防重复提交或状态流转。
- 不涉及库存、订单、支付等并发一致性问题。
- 不新增缓存。
- springdoc 是否启用属于启动期配置决策，运行时不会按请求动态切换。

## 10. 权限与安全
- 生产环境采用“双层收敛”：
  - springdoc 自身关闭 OpenAPI JSON 与 Swagger UI。
  - Spring Security 不再把文档路径加入公开白名单。
- 这样比“只关闭 Swagger UI”更完整，因为 `/v3/api-docs` JSON 同样包含接口契约信息。
- 这样也比“生产继续开放但要求登录”更保守，符合生产最小暴露原则。
- Actuator `health/info` 保持现有策略：生产隐藏健康详情，但基础探活仍可公开。

## 11. 测试策略
- 单元测试：
  - 本次不新增纯单元测试，核心行为依赖 Spring Boot 配置绑定、Spring Security Filter Chain 和 springdoc 自动配置组合。
- 集成测试：
  - 继续运行 `SystemControllerTest`，验证 `test` profile 下 `/v3/api-docs` 仍可访问。
  - 新增生产 profile 集成测试，覆盖 `/v3/api-docs` 与 `/swagger-ui.html` 未认证访问返回 401。
  - 新增生产 profile 集成测试，先通过管理员种子账号登录获取合法 JWT，再覆盖 `/v3/api-docs` 与 `/swagger-ui.html` 已认证访问返回 404。
  - 验证生产 profile 下 `/actuator/health` 仍可访问，避免安全加固误伤基础探活。
- 接口验证：
  - 如需手工验证，可用 `SPRING_PROFILES_ACTIVE=prod` 启动后访问上述路径确认行为。
- 异常场景验证：
  - 未认证生产文档路径返回 401。
  - 已认证生产文档路径返回 404。
  - 非生产文档路径继续返回 200。

## 12. 风险与替代方案
- 风险：生产环境如果临时需要 Swagger UI 排障，默认关闭会降低临时联调便利性。
  - 应对：可通过显式外部配置临时开启，但不把开启状态写入仓库默认生产配置。
- 风险：安全白名单和 springdoc 开关如果未来配置不一致，可能出现“端点关闭但路径仍公开 404”或“端点启用但未公开”的行为差异。
  - 应对：Security 直接读取 springdoc 的 `enabled` 配置，减少双写配置漂移；同时用已认证 404 测试锁定“资源本身关闭”的行为。
- 替代方案 A：只设置 `springdoc.swagger-ui.enabled=false`。
  - 不采用原因：这只关闭页面，不关闭 `/v3/api-docs` JSON。
- 替代方案 B：生产保留文档端点，但要求 JWT 登录。
  - 不采用原因：接口文档不是生产业务能力，默认关闭比鉴权后开放更符合最小暴露原则。
- 替代方案 C：完全移除 springdoc 依赖。
  - 不采用原因：开发、测试和学习演示仍需要 OpenAPI，直接移除会牺牲当前阶段的联调效率。
