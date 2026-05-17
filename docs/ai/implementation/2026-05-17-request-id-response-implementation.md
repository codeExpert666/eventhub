# requestId 响应注入重构实现说明

## 1. 本次改动解决了什么问题
本次改动解决了 `ApiResponse` 隐式读取 MDC 的分层问题。

重构前，`ApiResponse.success(...)` 和 `ApiResponse.failure(...)` 会从 MDC 读取当前线程的 requestId，并写入响应体。这让 `common/api` 依赖 `infra/logging`，也让非 HTTP 场景、异步线程和定时任务调用统一响应模型时隐式依赖日志上下文。

重构后，`ApiResponse` 只负责构造统一响应数据；HTTP 场景下的 requestId 由 `RequestIdFilter` 写入 request attribute，再由 `ApiResponseRequestIdAdvice` 在响应写出前统一补充。

## 2. 改动内容
- 新增了什么
  - 新增 `common/constant/RequestContextConstants`，集中维护 `X-Request-Id`、MDC key 和 request attribute key。
  - 新增 `infra/web/ApiResponseRequestIdAdvice`，基于 `ResponseBodyAdvice` 只为 `ApiResponse` 补充 requestId。
  - 新增 `RequestIdFilterTest`，覆盖 requestId 生成、复用、非法重建、MDC 写入与清理。
  - 新增 `ApiResponseRequestIdAdviceTest`，覆盖 Advice 对 `ApiResponse`、非 `ApiResponse` 和 null body 的处理。
- 修改了什么
  - `ApiResponse` 移除对 `RequestIdFilter` 和 MDC 的 import 与读取逻辑，工厂方法默认生成 `requestId = null` 的响应。
  - `ApiResponse` 新增 `withRequestId(String requestId)`，用于 Web 出口层在当前响应对象上后置设置 requestId。
  - `ApiResponse` 使用 Lombok `@Getter` 和私有 `@AllArgsConstructor` 生成 getter 与构造器，删除手写样板代码。
  - `RequestIdFilter` 除了写入 MDC 和 response header 外，新增写入 request attribute，并直接使用 `RequestContextConstants` 作为 requestId key 的单一来源。
  - `SystemControllerTest` 增加响应头 requestId 与响应体 requestId 一致性的集成断言。
  - `GlobalExceptionHandlerTest` 明确异常处理器本身不负责 requestId 注入。
- 删除了什么
  - 删除 `ApiResponse` 工厂方法内部读取 MDC 的逻辑。
  - 删除 `ApiResponseTest` 中通过 MDC 验证 requestId 透传的测试假设。

## 3. 为什么这样设计
- `ApiResponse` 属于 `common/api`，应该是通用响应协议模型，不应主动读取 MDC、Servlet request 或 Spring Web 上下文。
- MDC 只保留日志上下文职责，日志 pattern 中的 `%X{requestId:-N/A}` 可以继续工作。
- `RequestIdFilter` 是最适合管理 requestId 生命周期的入口点，因为它最早接触 HTTP 请求，也能在 finally 中清理 MDC。
- `ResponseBodyAdvice` 位于 HTTP 响应出口层，适合在最终写出 JSON 前补充统一响应体字段，同时不会要求 Controller 和异常处理器改写调用方式。
- 使用普通 class 而不是 Java record，是因为 `requestId` 存在 HTTP 出口层后置补充需求；`withRequestId` 原地设置 requestId 并返回当前对象，避免为单字段补充创建额外响应副本。
- 使用 Lombok 只生成 getter 和私有全参构造器，不使用 `@Data`，避免为统一响应模型暴露不必要的公共 setter。

## 4. 替代方案
- 方案 A：继续让 `ApiResponse` 读取 MDC。
  - 未采用原因：会保留 common 对 infra 和线程上下文的隐式依赖，也不利于非 HTTP 场景。
- 方案 B：让 Controller 或 `GlobalExceptionHandler` 显式读取 requestId。
  - 未采用原因：会把基础设施细节扩散到业务入口层，重复代码多，也更容易遗漏异常分支。
- 方案 C：让 `ApiResponse` 通过 `RequestContextHolder` 读取 request attribute。
  - 未采用原因：虽然不再读取 MDC，但仍让通用响应模型依赖 Spring Web 上下文，边界仍然不清晰。
- 方案 D：使用响应拦截器修改序列化结果。
  - 未采用原因：直接改写 JSON 字符串更脆弱，也更容易影响非 JSON 响应和文件下载。
- 方案 E：使用 Lombok `@Data` 一次性生成 getter、setter、equals 和 hashCode。
  - 未采用原因：`ApiResponse` 只需要 getter 和受控的 requestId 后置设置，公开所有 setter 会削弱响应模型边界。

## 5. 测试与验证
- 跑了哪些测试
  - 在 `backend` 目录执行 `mvn test`。
- 手工验证了哪些场景
  - 通过测试覆盖了请求不携带 `X-Request-Id` 时自动生成 requestId。
  - 覆盖了请求携带合法 `X-Request-Id` 时响应头和响应体复用同一值。
  - 覆盖了非法 `X-Request-Id` 被重新生成。
  - 覆盖了 Controller 正常返回 `ApiResponse` 时 requestId 被注入。
  - 覆盖了 `GlobalExceptionHandler` 返回 `ApiResponse` 时 requestId 被注入。
  - 覆盖了 Actuator 这类非 `ApiResponse` 响应不会被注入 requestId 字段。
  - 覆盖了 MDC 在过滤器链执行期间可用、请求结束后被清理。
- 结果如何
  - `mvn test` 通过，结果为 24 个测试全部成功。

## 6. 已知限制
- 当前 requestId 仍是单服务内的请求追踪标识，不等同于分布式 traceId。
- Advice 只处理 Servlet MVC 场景；如果未来引入 WebFlux，需要另行设计响应出口注入方式。
- 当前不做 MDC 跨线程传播。未来如果异步日志需要延续 requestId，应在日志上下文传播层单独设计，而不是回到 `ApiResponse` 中读取 MDC。
- 直接调用 `GlobalExceptionHandler` 的纯单元测试不会经过 `ResponseBodyAdvice`，因此 requestId 注入必须继续依赖 Web 集成测试覆盖。

## 7. 对后续版本的影响
- 对简历可用版的价值
  - 展示了清晰的基础设施边界：响应模型、日志上下文、过滤器和 Web 出口层各司其职。
  - 让统一响应体在 HTTP 和非 HTTP 场景下更可预测，便于后续扩展订单、支付、库存等模块。
- 对微服务 / 云原生演进的影响
  - 后续接入网关、OpenTelemetry、集中日志或调用链追踪时，可以在 requestId/tracing 基础设施层演进，不需要改动业务 Controller 或 `ApiResponse`。
  - 响应头透传和 MDC 日志输出保持稳定，为后续跨服务排障和日志聚合打基础。
