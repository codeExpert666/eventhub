# 枚举入参绑定与校验实现说明

## 1. 本次改动解决了什么问题

- 关闭 Jackson 数字 ordinal 到枚举的反序列化能力，避免 `{"status":0}` 被隐式解释为 `UserStatus.ENABLED`。
- 在 `UpdateUserStatusRequest` 中补充 `@RequestBody` 枚举绑定说明，明确非法字符串、空值和数字 ordinal 的处理路径。
- 在 `AdminUserQueryRequest` 中补充 `@ModelAttribute` 查询参数为什么使用 `String + @Pattern` 的说明。
- 为管理员更新用户状态接口补充非法枚举值、空值、大小写错误和数字 ordinal 的 MockMvc 集成测试。

## 2. 改动内容
- 新增了什么
  - 新增设计文档 `docs/ai/design/2026-05-22-enum-binding-validation-design.md`。
  - 新增本实现说明文档。
  - 新增 ADR `docs/ai/adr/2026-05-22-string-enum-api-contract.md`。
  - 新增 4 个管理员状态更新接口异常入参集成测试。
- 修改了什么
  - `application.yml`：
    - 增加 `spring.jackson.deserialization.fail-on-numbers-for-enums=true`。
  - `UpdateUserStatusRequest`：
    - 增加注释说明 `@RequestBody` 下枚举字段由 Jackson 反序列化。
    - 说明非法枚举字符串和大小写错误会在反序列化阶段失败，`null` 会进入 `@NotNull` 校验。
  - `AdminUserQueryRequest`：
    - 增加注释说明 `@ModelAttribute` 不走 Jackson，而是走 Spring MVC 类型转换。
    - 说明查询参数先用字符串承接，再用 `@Pattern` 校验的原因。
  - `AuthIntegrationTest`：
    - 增加 `LOCKED`、`null`、`disabled`、`0` 四类异常状态输入测试。
- 删除了什么
  - 无。

## 3. 为什么这样设计
- 对 `@RequestBody` 保留枚举字段：
  - 状态更新是命令型 JSON 接口，Controller 和 Service 边界拿到 `UserStatus` 更贴近领域模型。
  - 合法字符串枚举由 Jackson 统一转换即可，不需要每个请求 DTO 重复写字符串解析逻辑。
- 全局禁止数字 ordinal：
  - 数字枚举值不可读，且和 Java 枚举声明顺序强耦合。
  - 一旦后续新增、重排枚举，旧客户端传数字可能产生错误业务含义。
  - 全局配置能统一所有未来请求体枚举字段的 API 契约。
- `@ModelAttribute` 查询参数继续使用 `String + @Pattern`：
  - 查询参数天然来自字符串。
  - 先用字符串接住原始输入，可以让非法状态进入 Bean Validation，返回更明确的字段错误。
  - 校验通过后再转换为 `UserStatus`，避免 Mapper 和 Service 处理未校验字符串。
- 测试放在 `AuthIntegrationTest`：
  - 枚举绑定涉及 Spring MVC、Jackson、Bean Validation、安全鉴权和全局异常处理，集成测试比纯单元测试更能覆盖真实行为。

## 4. 替代方案
- 方案 A：把 `UpdateUserStatusRequest.status` 改成 `String + @Pattern`
  - 优点是错误提示可以完全字段化。
  - 未采用原因是命令型请求体直接使用领域枚举更清晰；非法 JSON 类型和非法枚举名属于请求体格式问题，由 Jackson 分支处理即可。
- 方案 B：为 `UserStatus` 编写自定义 Jackson 反序列化器
  - 优点是可以为非法值返回更细粒度的错误信息。
  - 未采用原因是当前只需要禁用数字 ordinal，Spring Boot 原生配置已经足够；自定义反序列化器会增加维护成本。
- 方案 C：接受 Jackson 默认数字 ordinal
  - 优点是无需改配置。
  - 未采用原因是数字 ordinal 会把 Java 内部声明顺序暴露为外部协议，不适合作为长期 API 契约。
- 方案 D：开启大小写不敏感枚举
  - 优点是客户端传 `disabled` 也能成功。
  - 未采用原因是当前 API 文档示例和数据库存储都使用大写枚举名，保持严格输入更利于学习阶段理解契约边界。

## 5. 测试与验证
- 跑了哪些测试
  - `mvn -q -Dtest=AuthIntegrationTest test`
  - `mvn -q test`
- 手工验证了哪些场景
  - 未单独启动应用做 curl 验证；MockMvc 已覆盖完整 Web 入参链路。
- 结果如何
  - `AuthIntegrationTest`：23 个测试全部通过。
  - 全量测试：50 个测试全部通过，0 失败，0 错误。
  - 新增场景结果：
    - `{"status":"LOCKED"}` 返回 400，消息为“请求体格式不合法”。
    - `{"status":null}` 返回 400，消息为“请求体参数校验失败”，字段错误为 `status 不能为空`。
    - `{"status":"disabled"}` 返回 400，消息为“请求体格式不合法”。
    - `{"status":0}` 返回 400，消息为“请求体格式不合法”。
  - 全量测试日志中出现的 `simulated unexpected failure` 是既有全局异常处理器测试主动抛出的异常，测试结果仍为通过。

## 6. 已知限制
- `HttpMessageNotReadableException` 当前统一返回 `body -> 请求体缺失或 JSON 格式错误`，不会把非法枚举字段精确标记为 `status`。
- `@RequestBody` 非法枚举字符串和大小写错误属于 Jackson 反序列化错误，无法触发 `@NotNull` 这类字段校验。
- 全局禁止数字 ordinal 会影响未来所有 JSON 请求体枚举字段；这是期望行为，但后续新增枚举接口时需要继续按字符串枚举设计文档。

## 7. 对后续版本的影响
- 对简历可用版的价值：
  - 可以清楚展示 Spring MVC 中 `@RequestBody` 与 `@ModelAttribute` 的绑定差异。
  - 补齐了枚举 API 契约的异常测试，避免隐式 ordinal 行为成为线上风险。
- 对微服务 / 云原生演进的影响：
  - 明确字符串枚举作为服务间和前后端 API 契约，更利于后续拆分服务时保持协议稳定。
  - 如果未来引入 OpenAPI SDK 生成，也应继续生成字符串枚举，而不是数字枚举。
