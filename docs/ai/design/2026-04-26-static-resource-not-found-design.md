# 静态资源不存在统一 404 处理设计

## 1. 背景
- 本地启动后访问 `http://localhost:8080/v3/api-docs` 时，浏览器会额外自动请求 `/favicon.ico`。
- 当前项目没有提供 favicon 静态资源，Spring MVC 会抛出 `NoResourceFoundException`。
- 该异常目前没有被 `GlobalExceptionHandler` 的具体分支捕获，会落入未知异常兜底分支，导致日志打印 ERROR 堆栈并向调用方返回 500。
- 这会让一个普通的“资源不存在”问题看起来像服务端内部故障，影响本地调试和后续接口排障判断。

## 2. 目标
- 为 `NoResourceFoundException` 增加明确的统一异常处理分支。
- 将不存在的静态资源或未匹配资源请求映射为 HTTP 404，而不是 HTTP 500。
- 保持项目统一响应体结构，返回 `ApiResponse` 中的应用层错误码、错误消息、requestId 和 timestamp。
- 增加轻量测试，确保该异常分支不会再被未知异常兜底误处理。

## 3. 非目标
- 本次不新增真实 `favicon.ico` 文件；浏览器请求 favicon 时可以返回 404，但不应被记录成系统内部异常。
- 不调整 OpenAPI / Swagger UI 的配置路径。
- 不调整 Spring Boot 默认静态资源查找位置。
- 不引入自定义静态资源控制器或额外依赖。

## 4. 影响范围
- 涉及模块：
  - `common/error`：补充资源不存在对应的统一错误码。
  - `common/exception`：补充全局异常处理分支。
  - `common/exception` 测试：补充异常处理器的轻量单元测试。
- 涉及表 / 缓存 / 外部接口：无。
- 受影响请求：
  - `/favicon.ico` 这类不存在的静态资源请求。
  - 其他被 Spring MVC 资源处理链识别但最终找不到资源的请求。

## 5. 领域建模
- 核心对象：
  - `ErrorCode.NOT_FOUND`：表达资源不存在的统一错误分类。
  - `NoResourceFoundException`：Spring MVC 在静态资源查找失败时抛出的框架异常。
  - `ApiResponse<Void>`：资源不存在时返回给调用方的统一失败响应。
- 实体关系：
  - `GlobalExceptionHandler` 捕获 `NoResourceFoundException`。
  - 处理器使用 `ErrorCode.NOT_FOUND` 组装 `ApiResponse`。
  - HTTP status 使用 `ErrorCode.NOT_FOUND.httpStatus`，响应体 code 使用 `ErrorCode.NOT_FOUND.code`。
- 关键状态：无业务状态流转。

## 6. API 设计
- 不新增业务 HTTP API。
- 错误响应契约：
  - HTTP status：`404 Not Found`
  - `code`：`COMMON-404`
  - `message`：`请求的资源不存在`
  - `data`：`null`
  - `requestId`：继续由 `RequestIdFilter` 写入 MDC 后透传到响应体
- 异常场景：
  - 请求 `/favicon.ico`，但 classpath 静态资源目录中不存在该文件。
  - 请求其他不存在的静态资源路径。

## 7. 数据设计
- 表结构调整：无。
- 索引设计：无。
- 唯一约束：无。
- 数据一致性考虑：无持久化数据变更。

## 8. 关键流程
- 正常流程：
  - 浏览器或客户端请求静态资源。
  - Spring MVC 静态资源处理器尝试查找资源。
  - 如果资源存在，按 Spring Boot 默认静态资源机制返回文件内容。
- 异常流程：
  - Spring MVC 未找到静态资源并抛出 `NoResourceFoundException`。
  - `GlobalExceptionHandler` 的资源不存在分支捕获该异常。
  - 系统返回统一 404 响应，不再进入未知异常分支。
- 状态流转：无。

## 9. 并发 / 幂等 / 缓存
- 并发：本次只新增无状态异常处理逻辑，不引入共享可变状态。
- 幂等：GET 静态资源请求天然应保持幂等，本次响应映射不改变幂等语义。
- 缓存：不新增缓存。后续如果补充真实 favicon，可再考虑浏览器缓存或静态资源缓存策略。

## 10. 权限与安全
- 角色访问：无变化，静态资源缺失属于公开路径的资源查找结果。
- 鉴权与鉴别约束：无变化。
- 安全考虑：
  - 404 响应不返回底层堆栈信息，避免泄露内部实现细节。
  - 不把常规资源缺失记录为 ERROR，减少日志噪声，避免掩盖真正的系统异常。

## 11. 测试策略
- 单元测试：
  - 直接构造 `NoResourceFoundException`，调用全局异常处理器方法。
  - 验证 HTTP status 为 404。
  - 验证响应体应用层 code 为 `COMMON-404`。
  - 验证响应消息为 `请求的资源不存在`。
  - 验证 `data` 为 `null`。
- 集成测试：
  - 使用 `SystemControllerTest` 通过 MockMvc 请求 `/favicon.ico`。
  - 验证完整 Web 链路会返回统一 404 响应，而不是 500。
- 接口验证：
  - 本地启动后可手工访问 `/favicon.ico`，预期返回统一 404，而不是 500。
- 异常场景验证：
  - 缺失静态资源不应再落入未知异常兜底分支。

## 12. 风险与替代方案
- 当前方案的风险：
  - 如果后续希望浏览器完全不发出 favicon 404，还需要额外提供真实 `favicon.ico`。
  - 新增 `COMMON-404` 后，后续业务层资源不存在也可能复用该错误码，需要保持语义清晰。
- 备选方案：
  - 方案 A：只添加 `backend/src/main/resources/static/favicon.ico`。
  - 方案 B：关闭或重配 Spring MVC 静态资源映射。
  - 方案 C：继续让未知异常兜底处理。
- 为什么不选备选方案：
  - 不选方案 A 作为唯一方案，因为它只能解决 favicon，无法解决其他不存在静态资源误报 500 的问题。
  - 不选方案 B，因为当前项目仍需要保留 Springdoc / Swagger UI 等 Web 资源能力，不应为一个 404 场景改变全局静态资源机制。
  - 不选方案 C，因为资源不存在是可预期客户端场景，不应被归类为系统内部错误。
