# 静态资源不存在统一 404 处理实现说明

## 1. 本次改动解决了什么问题

本次修复了浏览器访问 `http://localhost:8080/v3/api-docs` 后自动请求 `/favicon.ico` 时，缺失静态资源被全局异常处理器误判为未知系统异常的问题。

修复前，`NoResourceFoundException` 会落入 `GlobalExceptionHandler` 的 `Exception.class` 兜底分支，控制台打印 ERROR 堆栈，并返回 HTTP 500。修复后，该异常会被明确映射为 HTTP 404，并继续使用项目统一响应体。

## 2. 改动内容
- 新增了 `ErrorCode.NOT_FOUND`，对应 HTTP 404 和应用层错误码 `COMMON-404`
- 修改了 `GlobalExceptionHandler`，新增 `NoResourceFoundException` 专用处理分支
- 新增了 `GlobalExceptionHandlerTest`，直接验证资源不存在异常会返回统一 404 响应
- 修改了 `SystemControllerTest`，通过 MockMvc 请求 `/favicon.ico`，验证完整 Web 链路不再返回 500
- 新增了设计文档 `docs/ai/design/2026-04-26-static-resource-not-found-design.md`

## 3. 为什么这样设计
- `NoResourceFoundException` 表示静态资源或路径资源不存在，属于可预期的 404 场景，不应该被归类为系统内部错误
- 新增 `ErrorCode.NOT_FOUND` 可以让 HTTP 404 和响应体中的应用层 code 保持统一治理
- 在 `GlobalExceptionHandler` 中增加具体异常分支，符合当前项目“已知异常显式处理，未知异常才打 ERROR 堆栈”的异常处理边界
- 同时保留 `RequestIdFilter` 与 `ApiResponse` 的原有链路，让 404 响应仍然带有 requestId，方便排查具体请求
- 测试分为轻量单元测试和 Web 集成测试：前者锁住处理器契约，后者锁住用户实际遇到的 `/favicon.ico` 请求链路

## 4. 替代方案
- 方案 A：只添加真实 `favicon.ico`
- 方案 B：关闭或重配 Spring MVC 静态资源映射
- 方案 C：继续由未知异常兜底分支处理
- 为什么没有采用：
  - 不选方案 A 作为唯一方案，因为它只能解决 favicon，不能解决其他缺失静态资源或资源路径误报 500 的问题
  - 不选方案 B，因为当前项目仍依赖 Springdoc / Swagger UI 等 Web 资源能力，不应为一个资源缺失场景改变全局资源机制
  - 不选方案 C，因为 404 是明确的客户端资源缺失语义，继续返回 500 会误导排障和监控

## 5. 测试与验证
- 运行定向测试：`cd backend && mvn -q -Dtest=GlobalExceptionHandlerTest,SystemControllerTest test`
- 运行完整测试：`cd backend && mvn -q test`
- 验证结果：
  - `GlobalExceptionHandlerTest` 通过，确认 `NoResourceFoundException` 返回 HTTP 404、`COMMON-404` 和 `请求的资源不存在`
  - `SystemControllerTest` 通过，确认 `/favicon.ico` 在完整 MockMvc 链路中返回统一 404 响应
  - 完整后端测试集通过

## 6. 已知限制
- 当前仍没有提供真实 `favicon.ico` 文件，因此浏览器请求该路径时会得到 404；这已经是正确的资源不存在语义，但浏览器 Network 面板仍会看到该请求失败。
- 如果后续希望完全消除浏览器侧 favicon 404，可以再补充 `backend/src/main/resources/static/favicon.ico`。
- 当前 `COMMON-404` 是通用资源不存在错误码，后续业务模块如果需要更细粒度的“活动不存在”“订单不存在”等错误，可以在业务错误码体系中继续细分。

## 7. 对后续版本的影响
- 对简历可用版的价值：统一错误处理更接近真实后端项目，能清楚区分客户端 404 和服务端 500。
- 对微服务 / 云原生演进的影响：稳定的 404 错误码有利于后续接入网关、前端错误提示、日志检索和监控告警分类。
