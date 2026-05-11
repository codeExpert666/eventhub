# RequestIdFilter 顺序前置实现说明

## 1. 本次改动解决了什么问题

本次改动解决 `RequestIdFilter` 默认顺序可能晚于 Spring Security 外层代理过滤器的问题。

在未登录访问受保护接口、JWT 无效、权限不足等场景中，Spring Security 可能在请求进入 Controller 前直接返回错误响应。如果 requestId 过滤器执行得更晚，这类响应就可能缺少 `X-Request-Id`，日志 MDC 中也无法稳定关联同一个请求标识。

## 2. 改动内容
- 新增了什么
  - 未新增业务能力。
- 修改了什么
  - 在 `RequestIdFilter` 上增加 `@Order(Ordered.HIGHEST_PRECEDENCE)`，使其作为普通 Servlet Filter Bean 自动注册时具备明确的最高优先级。
  - 增强 `SystemControllerTest` 中未登录访问受保护接口的 401 测试，断言响应头包含 `X-Request-Id`。
- 删除了什么
  - 未删除代码。

## 3. 为什么这样设计
- 关键设计原因
  - requestId 是全局请求追踪上下文，应该包住 Spring Security、Controller、异常处理器等后续链路。
  - `@Order` 是 Spring Boot 自动注册 `Filter` Bean 时可识别的顺序声明，能用最小改动表达当前意图。
  - `Ordered.HIGHEST_PRECEDENCE` 明确早于 Spring Security 默认外层过滤器顺序，能覆盖认证失败这种最需要排障标识的场景。
- 与项目当前阶段的匹配点
  - 当前项目仍处于单体后端学习阶段，优先保持基础设施链路简单可读。
  - 不引入额外链路追踪依赖，也不改变现有安全配置结构。

## 4. 替代方案
- 方案 A
  - 新增 `FilterRegistrationBean<RequestIdFilter>` 并通过 `setOrder(...)` 显式控制顺序。
- 方案 B
  - 把 requestId 逻辑作为 Spring Security 内部过滤器，通过 `addFilterBefore(...)` 放到安全链路前部。
- 为什么没有采用
  - 未采用方案 A：当前只需要控制单个已有 Filter Bean 的顺序，新增配置类会增加样板代码。
  - 未采用方案 B：requestId 属于全局 Servlet 请求追踪，不应该绑定到 Spring Security 内部链，否则未来存在非安全链路请求时职责边界不清晰。

## 5. 测试与验证
- 跑了哪些测试
  - `mvn test -Dtest=SystemControllerTest`
- 手工验证了哪些场景
  - 本次未额外启动应用做手工 HTTP 验证，使用 MockMvc 集成测试覆盖完整 Web/Security/Filter 链路。
- 结果如何
  - 测试通过：`Tests run: 11, Failures: 0, Errors: 0, Skipped: 0`。
  - 未登录访问受保护接口返回 401 时，已断言响应头存在 `X-Request-Id`。

## 6. 已知限制
- 当前版本还缺什么
  - 未新增独立的 Filter 顺序 introspection 测试；当前通过 401 响应头结果验证顺序是否满足业务目标。
  - 异步请求或跨线程执行时，MDC 传播仍需要后续结合具体异步执行模型单独处理。
- 哪些地方后面需要继续演进
  - 如果未来引入 OpenTelemetry、网关或服务间调用，需要把 requestId 与 traceId/spanId 的关系重新梳理。
  - 如果新增更多基础设施 Filter，需要集中维护过滤器顺序约定。

## 7. 对后续版本的影响
- 对简历可用版的价值
  - 认证失败、授权失败等高频排障场景具备稳定 requestId，提升可观测性完整度。
- 对微服务 / 云原生演进的影响
  - 为后续网关透传 requestId、日志聚合、链路追踪集成保留清晰入口。
