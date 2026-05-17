# ADR：requestId 由 HTTP 出口层注入统一响应体

## 标题
requestId 由 HTTP 出口层注入统一响应体

## 状态
- accepted

## 背景
当前 `ApiResponse` 在静态工厂方法中直接读取 MDC，导致统一响应模型依赖日志上下文和 `infra/logging`。这会带来两个问题：

- `common/api` 不再是纯响应协议模型，而是隐式依赖线程上下文和基础设施实现。
- 非 HTTP 场景调用 `ApiResponse.success/failure` 时，也会被迫接受 MDC 作为隐藏输入，异步线程、定时任务或 MQ 消费场景的边界不清。

同时，项目仍希望保持 HTTP 响应头 `X-Request-Id`、响应体 `requestId` 和日志 MDC 中的 requestId 一致，避免破坏现有 Controller 与全局异常处理器的调用方式。

## 决策
采用以下职责划分：

- `ApiResponse` 只作为统一响应数据结构，工厂方法不读取 MDC 或任何 Web 上下文。
- `RequestIdFilter` 负责 requestId 的生成或复用，并写入 MDC、request attribute 和 response header，最后清理 MDC。
- `ApiResponseRequestIdAdvice` 基于 `ResponseBodyAdvice`，在 HTTP 响应写出前只对 `ApiResponse` 补充 requestId。
- Controller 和 `GlobalExceptionHandler` 继续返回 `ApiResponse.success(...)` / `ApiResponse.failure(...)`，不显式处理 requestId。

## 备选方案
- 方案 1：继续在 `ApiResponse` 中读取 MDC。
- 方案 2：在 Controller 和 `GlobalExceptionHandler` 中显式读取 requestId 并传给 `ApiResponse`。
- 方案 3：在 `ApiResponse` 中通过 `RequestContextHolder` 读取当前 `HttpServletRequest` attribute。
- 方案 4：在 HTTP 出口层使用 `ResponseBodyAdvice` 注入 requestId。

## 决策理由
选择方案 4，原因如下：

- 分层更清晰：`common/api` 不依赖日志、Servlet 或 Spring Web 上下文。
- 行为更集中：requestId 生命周期仍由过滤器负责，响应体补充由 Web 出口层负责。
- 兼容性更好：Controller 和异常处理器调用方式基本不变。
- 非 HTTP 场景更明确：`ApiResponse` 不再从 MDC 隐式取值，避免异步和定时任务误用线程上下文。

没有选择其他方案的原因：

- 方案 1 保留了当前反向依赖和隐藏输入问题。
- 方案 2 会把基础设施细节扩散到业务入口层，重复代码多。
- 方案 3 虽然避开 MDC，但仍让通用响应模型依赖 Spring Web 上下文，本质上没有解决纯模型边界问题。

## 影响
- 好处：
  - `ApiResponse` 成为更纯粹的响应协议模型。
  - requestId 在日志、响应头和响应体中的来源更清晰。
  - 后续引入异步任务、MQ 消费或定时任务时，不会因为 `ApiResponse` 隐式读取 MDC 而产生错误假设。
- 代价：
  - 需要新增一个 Web 出口层组件，并用测试覆盖非 `ApiResponse` 不被修改的边界。
  - 直接调用异常处理器的纯单元测试不会经过 Advice，因此 requestId 需要由 Web 集成测试验证。
- 后续可能需要调整的地方：
  - 如果未来接入网关 traceId、OpenTelemetry 或跨服务链路追踪，可在 `RequestIdFilter` 或专门的 tracing 组件中演进，不需要改 `ApiResponse`。
  - 如果未来存在 streaming、文件下载或特殊消息转换器，应继续确保 Advice 只处理 `ApiResponse`。
