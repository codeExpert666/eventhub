# 枚举入参绑定与校验设计

## 1. 背景
- 管理员更新用户状态接口 `PATCH /api/v1/admin/users/{userId}/status` 使用 `@RequestBody UpdateUserStatusRequest` 接收 JSON 请求体，其中 `status` 字段是 `UserStatus` 枚举。
- 管理员用户列表接口 `GET /api/v1/admin/users` 使用 `@ModelAttribute AdminUserQueryRequest` 接收查询参数，其中 `status` 当前使用 `String + @Pattern`，再在请求对象内部转换为 `UserStatus`。
- 两类绑定路径不同：
  - `@RequestBody` 由 Jackson 反序列化 JSON。
  - `@ModelAttribute` 由 Spring MVC `ConversionService` / `WebDataBinder` 绑定查询参数或表单参数。
- Jackson 默认可能接受数字枚举 ordinal，例如 `0` 映射为第一个枚举常量。这会让 HTTP API 暴露不稳定的枚举序号契约，不利于后续新增或重排枚举。

## 2. 目标
- 禁止 Jackson 使用数字 ordinal 反序列化枚举。
- 在相关 DTO 和配置中补充注释，明确 `@RequestBody` 与 `@ModelAttribute` 枚举绑定差异。
- 补充管理员更新用户状态接口的异常入参测试：
  - 非法枚举值。
  - 空值。
  - 大小写错误。
  - 数字 ordinal。
- 成功标准：
  - 合法 `"ENABLED"` / `"DISABLED"` 仍可绑定为 `UserStatus`。
  - 非法字符串、大小写错误和数字 ordinal 均返回统一 400。
  - `null` 状态进入 Bean Validation，并返回 `status 不能为空`。

## 3. 非目标
- 本次不新增用户状态枚举值。
- 本次不调整用户状态流转业务规则。
- 本次不改全局错误码体系。
- 本次不为所有枚举引入自定义 Jackson 反序列化器。
- 本次不把所有 `@ModelAttribute` 枚举字段统一改成字符串；只解释当前用户状态查询字段的设计原因。

## 4. 影响范围
- `backend/src/main/resources/application.yml`
  - 增加 Jackson 反序列化配置，禁止数字枚举 ordinal。
- `modules.auth.dto.request.UpdateUserStatusRequest`
  - 补充 `@RequestBody` 枚举绑定注释。
- `modules.auth.dto.request.AdminUserQueryRequest`
  - 补充 `@ModelAttribute` 查询参数为什么先用字符串承接的注释。
- `modules.auth.AuthIntegrationTest`
  - 增加管理员状态更新接口异常枚举测试。
- 数据库、缓存、外部接口均不调整。

## 5. 领域建模
- `UserStatus`
  - 仍是用户账号状态枚举。
  - 当前合法状态为 `ENABLED`、`DISABLED`。
- `UpdateUserStatusRequest`
  - 命令型请求体 DTO。
  - `status` 使用 `UserStatus` 表达领域允许值。
  - JSON 字符串必须与枚举常量名一致。
- `AdminUserQueryRequest`
  - 查询型 Web DTO。
  - `status` 使用 `String` 承接原始查询参数，再通过 `@Pattern` 进入 Bean Validation。
  - 校验通过后再转换为 `UserStatus`，传给 Mapper 查询条件。
- 关键状态：
  - 无新增状态。
  - 仍只有启用和禁用两种账号状态。

## 6. API 设计
- 合法请求：

```http
PATCH /api/v1/admin/users/{userId}/status
Content-Type: application/json

{"status":"DISABLED"}
```

- 错误场景：
  - `{"status":"LOCKED"}`：Jackson 无法反序列化为 `UserStatus`，返回 400。
  - `{"status":"disabled"}`：大小写不符合枚举常量名，返回 400。
  - `{"status":0}`：数字 ordinal 被全局禁止，返回 400。
  - `{"status":null}`：反序列化成功但 Bean Validation 触发 `@NotNull`，返回 400，字段错误为 `status 不能为空`。
- `GET /api/v1/admin/users?status=LOCKED`：
  - 继续由 `AdminUserQueryRequest.status` 的 `@Pattern` 拦截，返回明确的“用户状态只能是 ENABLED 或 DISABLED”。

## 7. 数据设计
- 表结构调整：无。
- 索引设计：无。
- 唯一约束：无。
- 数据一致性考虑：
  - 本次只调整 Web 入参解析规则，不改变持久化数据。
  - 禁止数字 ordinal 可以避免客户端把枚举声明顺序当成长期 API 契约。

## 8. 关键流程
- `@RequestBody` 正常流程：
  1. 客户端提交 JSON 请求体。
  2. Jackson 将 `"DISABLED"` 反序列化为 `UserStatus.DISABLED`。
  3. Bean Validation 校验 `@NotNull`。
  4. Controller 调用 Service 更新用户状态。
- `@RequestBody` 异常流程：
  - 非法枚举字符串或数字 ordinal 在 Jackson 阶段失败，进入 `HttpMessageNotReadableException` 分支。
  - `null` 在 Jackson 阶段可绑定为 `null`，随后进入 `MethodArgumentNotValidException` 分支。
- `@ModelAttribute` 查询流程：
  1. Spring MVC 将原始查询参数绑定为字符串。
  2. Bean Validation 使用 `@Pattern` 校验允许值。
  3. DTO 内部转换为 `UserStatus`。

## 9. 并发 / 幂等 / 缓存
- 并发：
  - 本次不改变状态更新的数据库写入策略。
  - 入参解析失败会在 Controller 边界前返回，不进入并发写入路径。
- 幂等：
  - 请求格式校验不引入新的幂等机制。
  - 同一状态重复提交的业务语义维持现状。
- 缓存：
  - 不涉及缓存。

## 10. 权限与安全
- 管理员状态更新接口仍由 `@PreAuthorize("hasRole('ADMIN')")` 保护。
- 枚举值校验发生在业务执行前，非法请求不会触达 Service 或 Mapper。
- 禁止数字 ordinal 降低 API 误用风险，避免枚举声明顺序泄漏为外部协议。

## 11. 测试策略
- 单元测试：
  - 本次不新增纯单元测试，枚举绑定属于 Web 层集成行为。
- 集成测试：
  - 使用 MockMvc 覆盖管理员更新用户状态接口。
  - 合法枚举路径沿用既有禁用用户测试。
  - 新增非法字符串、`null`、小写字符串、数字 ordinal 的 400 断言。
- 接口验证：
  - 不单独启动应用手工 curl，MockMvc 覆盖完整 Spring MVC、Jackson、Bean Validation 和 Security 链路。
- 异常场景验证：
  - 验证 `@RequestBody` 反序列化错误和 Bean Validation 错误分别能落入统一 400 响应。

## 12. 风险与替代方案
- 当前方案风险：
  - 关闭数字 ordinal 是全局 Jackson 行为，会影响所有未来 `@RequestBody` 枚举字段。
  - 这是刻意选择：HTTP API 应优先使用稳定、可读的字符串枚举名。
- 备选方案：
  - 方案 A：只在 `UpdateUserStatusRequest` 上写自定义反序列化器。
  - 方案 B：把 `UpdateUserStatusRequest.status` 也改成 `String + @Pattern`。
  - 方案 C：接受 Jackson 默认数字 ordinal。
- 为什么不选备选方案：
  - 不选方案 A：当前需求是全局 API 契约约束，自定义反序列化器会增加样板代码。
  - 不选方案 B：状态更新是命令型 JSON 请求体，直接使用 `UserStatus` 能让 Service 边界拿到领域类型；非法值由 Jackson 统一识别即可。
  - 不选方案 C：数字 ordinal 与枚举声明顺序耦合，后续新增枚举时兼容性风险较高。
