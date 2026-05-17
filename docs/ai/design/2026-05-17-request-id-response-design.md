# requestId 响应注入重构设计

## 1. 背景
- 当前 `ApiResponse` 在静态工厂方法中直接从 MDC 读取 `requestId`，导致 `common/api` 反向依赖 `infra/logging`，也让统一响应模型隐式依赖线程上下文。
- MDC 的主要职责应是日志上下文，不应成为响应数据结构的隐藏输入。异步线程、定时任务、MQ 消费等非 HTTP 场景也不应该因为调用 `ApiResponse.success/failure` 而默认读取 MDC。
- 项目当前仍需要保持 HTTP 成功响应、全局异常响应、响应头和日志里的 requestId 行为基本一致。

## 2. 目标
- 移除 `ApiResponse` 对 MDC、Servlet、Spring Web 和 `RequestIdFilter` 的依赖。
- 由 `RequestIdFilter` 统一完成 requestId 的生成或复用、写入 MDC、写入请求 attribute、写入响应 header、请求结束后清理 MDC。
- 新增 Web 出口层组件，在响应写出前只对 `ApiResponse` 类型补充 requestId。
- 保持 Controller 与 `GlobalExceptionHandler` 的调用方式基本不变，继续使用 `ApiResponse.success(...)` 和 `ApiResponse.failure(...)`。
- 响应 header `X-Request-Id` 与响应 body 中的 `requestId` 保持一致。

## 3. 非目标
- 不改变现有业务接口路径、请求参数、错误码和响应字段命名。
- 不引入分布式链路追踪、MDC 跨线程传播、异步上下文传播或网关级 traceId 方案。
- 不把非 `ApiResponse` 响应包装成统一响应体。
- 不调整日志输出格式，仅保持 `%X{requestId}` 继续可用。

## 4. 影响范围
- `common/api`：`ApiResponse` 只保留统一响应数据结构与构造逻辑。
- `common/constant`：新增 requestId 相关常量，避免 header、MDC key、request attribute 字符串散落。
- `infra/logging`：调整 `RequestIdFilter` 的职责描述和写入 request attribute 的行为。
- `infra/web`：新增 `ApiResponseRequestIdAdvice`，作为 HTTP 响应出口层补充 requestId。
- `common/exception` 与 `modules/system/controller`：保持调用方式不变，仅由集成测试验证响应体 requestId。
- 日志配置：保留 `logback-spring.xml` 中的 `%X{requestId:-N/A}`。
- 数据库、缓存、外部接口：无变更。

## 5. 领域建模
- `ApiResponse<T>`：统一 HTTP JSON 响应模型，包含 `code`、`message`、`data`、`requestId`、`timestamp`。它不主动读取上下文，使用普通 class 建模，允许 Web 出口层通过 `withRequestId` 后置设置 requestId；getter 和私有全参构造器通过 Lombok 生成，减少无业务含义的样板代码。
- `RequestContextConstants`：跨基础设施层共享 requestId 的协议常量，包括请求头名、MDC key 和 request attribute 名，是 requestId key 定义的单一来源。
- `RequestIdFilter`：HTTP 请求级 requestId 生命周期管理者，直接使用 `RequestContextConstants` 中的常量，不再维护二次别名。
- `ApiResponseRequestIdAdvice`：HTTP 出口层的响应体补充器，只处理已经是 `ApiResponse` 的响应。
- 本次不涉及业务实体关系和业务状态流转。

## 6. API 设计
- 对外接口路径和请求参数不变。
- HTTP 请求头：
  - `X-Request-Id`：调用方可选传入。合法时复用，缺失或非法时服务端生成。
- HTTP 响应头：
  - `X-Request-Id`：始终回写本次请求最终使用的 requestId。
- 统一响应体：
  - Controller 或异常处理器构造时：`requestId = null`。
  - HTTP 写出前：`ApiResponseRequestIdAdvice` 从 request attribute 读取 requestId 并填充。
- 错误码和异常场景不变，仍由 `GlobalExceptionHandler` 根据异常类型返回对应 `ApiResponse.failure(...)`。

## 7. 数据设计
- 不调整表结构、索引或唯一约束。
- requestId 不落库，本次只用于请求日志关联、响应头透传和响应体展示。
- 一致性要求限定在单次 HTTP 请求内：响应头与 `ApiResponse.requestId` 必须来自同一个 request attribute。

## 8. 关键流程
- 正常流程：
  1. 请求进入 `RequestIdFilter`。
  2. 过滤器读取 `X-Request-Id`，合法则复用，否则生成新的 requestId。
  3. 过滤器写入 MDC、`HttpServletRequest` attribute 和 `HttpServletResponse` header。
  4. Controller 返回 `ApiResponse.success(data)`，此时响应体 requestId 仍为 `null`。
  5. `ApiResponseRequestIdAdvice` 在响应写出前识别 `ApiResponse`，从 request attribute 读取 requestId 并设置到当前响应体。
  6. 请求结束后 `RequestIdFilter` 在 `finally` 中清理 MDC。
- 异常流程：
  1. Controller 或 Spring MVC 抛出异常。
  2. `GlobalExceptionHandler` 返回 `ApiResponse.failure(...)`。
  3. 出口层 Advice 统一补充 requestId。
- 非 `ApiResponse` 流程：
  - Actuator、OpenAPI、文件下载、字符串响应等非 `ApiResponse` body 原样返回，不做包装和修改。

## 9. 并发 / 幂等 / 缓存
- 并发：MDC 基于线程上下文，必须在 `finally` 清理，避免 Servlet 线程复用导致 requestId 污染。
- 幂等：本次不把 requestId 作为幂等键使用，也不改变后续订单、库存等业务幂等设计。
- 缓存：不新增缓存。
- 异步：不为响应体填充 requestId 而传播 MDC。未来如果需要异步日志关联，应在日志基础设施层单独设计 MDC 传播，不回灌到 `ApiResponse`。

## 10. 权限与安全
- 本次不改变鉴权与授权规则。
- 外部传入的 `X-Request-Id` 仍按既有正则做格式校验，避免日志注入、超长头和格式污染。
- `ApiResponseRequestIdAdvice` 只读取 request attribute，不读取 MDC，也不从请求参数或请求体中提取 requestId。

## 11. 测试策略
- 单元测试：
  - `ApiResponseTest` 验证工厂方法不再隐式填充 requestId，并验证 `withRequestId` 会设置当前响应对象的 requestId。
  - `RequestIdFilterTest` 验证请求头复用、生成、request attribute、响应头和 MDC 清理。
  - `ApiResponseRequestIdAdviceTest` 验证只处理 `ApiResponse`，对 `null` 与非 `ApiResponse` 原样返回。
- 集成测试：
  - `SystemControllerTest` 验证成功响应与异常响应中的 `requestId` 和 `X-Request-Id` 一致。
  - 验证调用方传入合法 `X-Request-Id` 时响应头与响应体复用同一值。
  - 验证 Actuator 等非 `ApiResponse` 响应不被错误注入。
- 接口验证：
  - 使用 MockMvc 覆盖 HTTP 正常、校验失败、资源不存在和 requestId 非法重建场景。
- 异常场景验证：
  - 非法 JSON、字段校验失败、静态资源不存在仍返回统一错误响应，并由 Advice 注入 requestId。

## 12. 风险与替代方案
- 当前方案风险：
  - `ResponseBodyAdvice` 运行在 Web 出口层，如果未来存在特殊消息转换器或 streaming 场景，需要继续确保只对 `ApiResponse` 生效。
  - 直接调用 `GlobalExceptionHandler` 的纯单元测试不会经过 Advice，因此 requestId 应继续由 Web 集成测试覆盖。
- 备选方案：
  - 在 `ApiResponse` 中继续读取 MDC：实现最少，但会保留 common 对 infra 和线程上下文的隐式依赖，不符合本次重构目标。
  - 在每个 Controller 或异常处理器中显式传入 requestId：行为直观，但会污染业务入口层并带来大量重复代码。
  - 使用 `RequestContextHolder` 在 `ApiResponse` 中读取 request attribute：能避开 MDC，但仍让通用响应模型依赖 Spring Web 上下文，非 HTTP 场景边界不清。
- 不选备选方案的原因：
  - 本项目当前阶段更需要清晰分层和可复盘的基础设施边界。让 Filter 负责 requestId 生命周期、让 Advice 负责 HTTP 出口补充响应体，职责更单一，也能保持业务代码改动最小。
