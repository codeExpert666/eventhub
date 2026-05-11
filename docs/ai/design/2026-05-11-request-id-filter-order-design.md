# RequestIdFilter 顺序前置设计

## 1. 背景
- 当前 `RequestIdFilter` 通过 `@Component` 注册为 Spring Bean，并继承 `OncePerRequestFilter`，Spring Boot 会自动把它作为 Servlet Filter 注册。
- 该过滤器当前没有显式顺序声明，普通 Filter Bean 的默认顺序通常晚于 Spring Security 外层代理过滤器。
- 当 Spring Security 在前置链路中直接返回 401/403 时，`RequestIdFilter` 可能没有机会写入 MDC 与 `X-Request-Id` 响应头，不利于认证失败场景排障。

## 2. 目标
- 让 `RequestIdFilter` 在 Servlet 容器过滤器链中足够靠前执行，覆盖 Spring Security 认证和授权失败场景。
- 保持现有 requestId 生成、校验、响应头回写和 MDC 清理逻辑不变。
- 成功标准：
  - 未登录访问受保护接口返回 401 时，响应头仍包含 `X-Request-Id`。
  - 已有公开接口、非法 requestId 重建逻辑保持不变。

## 3. 非目标
- 不调整 JWT 解析、认证失败处理器或授权规则。
- 不引入分布式链路追踪组件。
- 不改变统一响应体结构。
- 不修改数据库、缓存或外部接口。

## 4. 影响范围
- 涉及模块：
  - `infra.logging.RequestIdFilter`
  - Web/Spring Security 请求入口链路
  - 相关 MockMvc 集成测试
- 涉及表 / 缓存 / 外部接口：
  - 不涉及数据库表。
  - 不涉及缓存。
  - 不新增外部接口。

## 5. 领域建模
- 核心实体：
  - 无新增业务实体。
  - requestId 属于基础设施层的请求追踪上下文，不进入领域模型。
- 实体关系：
  - 不适用。
- 关键状态：
  - 不涉及业务状态流转。

## 6. API 设计
- 接口列表：
  - 不新增 API。
- 请求参数：
  - 继续支持客户端通过 `X-Request-Id` 请求头传入合法 requestId。
- 响应结构：
  - 继续通过 `X-Request-Id` 响应头回写本次请求使用的 requestId。
  - 统一响应体结构不变。
- 错误码 / 异常场景：
  - 未认证仍返回现有 `AUTH-401`。
  - 本次变更只保证该失败响应也带有 requestId 响应头。

## 7. 数据设计
- 表结构调整：
  - 不涉及。
- 索引设计：
  - 不涉及。
- 唯一约束：
  - 不涉及。
- 数据一致性考虑：
  - 不涉及持久化数据一致性。

## 8. 关键流程
- 正常流程：
  1. 请求进入 Servlet 容器过滤器链。
  2. `RequestIdFilter` 先读取或生成 requestId。
  3. requestId 写入 MDC 和 `X-Request-Id` 响应头。
  4. 后续进入 Spring Security 与业务 Controller。
  5. 请求结束后在 `finally` 中清理 MDC。
- 异常流程：
  1. 请求进入 `RequestIdFilter` 后已绑定 requestId。
  2. 后续如果被 Spring Security 拦截为 401/403，响应仍能带上 `X-Request-Id`。
  3. `RequestIdFilter` 的 `finally` 仍负责清理 MDC，避免线程复用串用 requestId。
- 状态流转：
  - 不涉及业务状态机。

## 9. 并发 / 幂等 / 缓存
- 并发：
  - requestId 通过 MDC 绑定在当前请求线程上下文中，过滤器结束时清理。
  - 本次只调整执行顺序，不改变线程隔离方式。
- 幂等：
  - 不涉及业务幂等。
- 缓存：
  - 不涉及缓存。

## 10. 权限与安全
- 权限：
  - 不改变任何接口的访问权限。
- 安全：
  - 继续校验外部传入的 requestId 格式，非法值不会进入日志和响应头。
  - 过滤器前置后，认证失败、授权失败、未匹配接口等更早失败的场景也能具备追踪 ID。

## 11. 测试策略
- 单元测试：
  - 暂不新增独立单元测试；过滤器行为通过 Web 集成测试覆盖。
- 集成测试：
  - 新增未登录访问受保护接口的 MockMvc 测试，断言 401 响应包含 `X-Request-Id`。
  - 保留已有公开接口响应头与非法 requestId 重建测试。
- 接口验证：
  - 可手工访问受保护接口，例如 `DELETE /api/v1/system/ping`，确认 401 响应头中存在 `X-Request-Id`。
- 异常场景验证：
  - 重点验证 Spring Security 在请求进入 Controller 前直接返回错误时，requestId 仍然存在。

## 12. 风险与替代方案
- 当前方案的风险：
  - `Ordered.HIGHEST_PRECEDENCE` 会让 `RequestIdFilter` 非常靠前执行。由于它不读取请求体、不做鉴权、不短路请求，风险较低。
  - 如果未来引入需要更早执行的基础设施过滤器，需要重新梳理顺序。
- 备选方案：
  - 使用 `FilterRegistrationBean<RequestIdFilter>` 显式设置 order。
  - 将 requestId 逻辑放入 Spring Security 内部过滤器链。
- 为什么不选备选方案：
  - `FilterRegistrationBean` 需要新增配置类，当前只需要调整单个 Filter Bean 的顺序，`@Order` 更直接。
  - 放入 Spring Security 内部链只能覆盖安全链内部，不适合承担全局 Servlet 请求追踪职责。
