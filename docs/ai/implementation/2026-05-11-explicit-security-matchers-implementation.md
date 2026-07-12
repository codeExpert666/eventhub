# Security Matcher 显式化实现说明

## 1. 本次改动解决了什么问题

本次改动解决了 `SecurityConfiguration` 中部分 `requestMatchers` 未指定 HTTP Method，导致路径级公开范围不够明确的问题。

调整前，类似 `.requestMatchers("/api/v1/system/**").permitAll()` 的规则会匹配所有请求方法。对于学习和简历型项目来说，这容易造成两个问题：

- 阅读代码时容易误以为未指定方法就是默认 GET。
- 未来在同一命名空间下新增写接口时，可能被已有路径通配 `permitAll()` 意外公开。

调整后，当前真实存在且需要公开的接口都显式声明请求方法，管理员接口也显式列出当前 GET/PATCH 入口。

## 2. 改动内容
- 新增了什么
  - 新增 `docs/ai/design/2026-05-11-explicit-security-matchers-design.md`。
  - 新增当前实现说明文档。
  - 新增系统命名空间非公开方法的集成测试。
  - 新增 Actuator `HEAD /actuator/health` 放行测试。
  - 新增 `POST /api/v1/auth/logout` 未登录访问返回 401 的集成测试。
- 修改了什么
  - `SecurityConfiguration` 中公开接口从路径级 matcher 调整为 `HttpMethod + path`。
  - `GET /api/v1/system/ping` 和 `POST /api/v1/system/echo` 分别显式公开。
  - `GET /actuator/health`、`GET /actuator/info`、对应 HEAD 请求显式公开。
  - `GET /v3/api-docs`、`GET /v3/api-docs/**`、Swagger UI 和 `GET /favicon.ico` 显式公开。
  - `POST /api/v1/auth/logout`、`GET /api/v1/me` 显式声明为登录接口。
  - `GET /api/v1/admin/users`、`PATCH /api/v1/admin/users/*/status` 显式声明为 ADMIN 接口。
  - 保留 `/api/v1/admin/**` 的 ADMIN 兜底保护。
- 删除了什么
  - 删除了 `/api/v1/system/**` 的公开路径通配规则。
  - 删除了 Actuator、Swagger、favicon 的方法不限公开规则。

## 3. 为什么这样设计
- 关键设计原因
  - Spring Security 未指定 `HttpMethod` 的 matcher 会匹配所有方法，公开接口使用方法级 matcher 更符合最小暴露原则。
  - 当前 Controller 中系统接口只有 `GET /ping` 和 `POST /echo`，没有必要把整个 `/api/v1/system/**` 命名空间都公开。
  - 登出和当前用户接口虽然最终也会被 `anyRequest().authenticated()` 保护，但显式写出能让安全配置成为一份可读的接口权限清单。
  - 管理员接口保留 URL 级 `hasRole("ADMIN")`，并且 Controller 上已有 `@PreAuthorize("hasRole('ADMIN')")`，形成进入 Controller 前和方法调用前的双层防护。
  - `/api/v1/admin/**` 兜底故意不限制方法，用于保护未来新增管理端接口，避免遗漏精确规则后退化为普通登录用户可访问。
- 与项目当前阶段的匹配点
  - 当前仍是单体后端阶段，不需要引入更复杂的权限 DSL 或外部策略引擎。
  - 通过小范围配置调整和集成测试即可关闭公开范围不明确的问题。
  - 文档中保留了接口清单和取舍说明，便于后续复盘 Spring Security matcher 的匹配语义。

## 4. 替代方案
- 方案 A：继续使用路径级 `permitAll()`
  - 没有采用，因为公开范围过宽，且容易让读代码的人误解未指定方法的语义。
- 方案 B：所有接口都只依赖 `anyRequest().authenticated()` 和 Controller 方法级权限
  - 没有采用，因为公开接口仍需要明确放行，管理员接口也应该尽量在 URL 级提前拦截。
- 方案 C：删除 `/api/v1/admin/**` 兜底，只保留当前两个管理员接口规则
  - 没有采用，因为未来新增管理员接口时，忘记补配置会落到 `anyRequest().authenticated()`，导致只登录但非 ADMIN 的用户可能进入后续链路。
- 方案 D：引入自定义权限规则枚举或集中式 API 权限注册表
  - 没有采用，因为当前接口数量很少，直接在 `SecurityConfiguration` 中清晰声明更简单，也更适合阶段 1 学习目标。

## 5. 测试与验证
- 已执行：
  - `mvn -q -Dtest=SystemControllerTest,AuthIntegrationTest test`
  - `mvn -q test`
  - `git diff --check`
- 验证结果：
  - 相关集成测试通过。
  - 全量测试通过。
  - diff 空白检查通过。
- 覆盖场景：
  - 注册、登录仍然公开可用。
  - `GET /api/v1/system/ping` 和 `POST /api/v1/system/echo` 仍然公开可用。
  - 未登录访问未公开的 system 方法返回 401。
  - `HEAD /actuator/health` 被显式放行。
  - 未登录访问 `/api/v1/me` 和 `/api/v1/auth/logout` 返回 401。
  - 普通用户访问管理员接口返回 403。
  - 管理员用户访问管理员接口成功。
  - 过期 token、篡改 token、禁用用户旧 token 仍返回 401。

## 6. 已知限制
- 未显式公开的错误方法请求可能先被安全层拦截为 401，而不是进入 Spring MVC 后返回 405。
- 当前没有配置跨域 CORS 预检请求放行；后续如果引入前端独立域名，需要单独设计 `OPTIONS` 策略。
- Swagger 和 Actuator 当前只按开发、探活常用的 GET/HEAD 场景放行。
- 管理员接口仍是角色级授权，尚未细化到权限点或资源级别。

## 7. 对后续版本的影响
- 对简历可用版的价值
  - 权限配置更像真实项目中的安全白名单和管理端保护策略。
  - 可以清楚解释 Spring Security 中路径匹配、方法匹配、`permitAll`、`authenticated`、`hasRole` 的组合关系。
  - 测试覆盖了“公开接口不是整个命名空间公开”的边界。
- 对微服务 / 云原生演进的影响
  - 明确的接口权限清单有利于后续迁移到网关鉴权或资源服务鉴权。
  - 管理端命名空间兜底规则有利于未来拆分管理后台服务时识别权限边界。
  - 保持 `anyRequest().authenticated()` 的默认安全姿态，便于后续模块增量接入。
