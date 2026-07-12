# PageResponse hasPrevious 越界页语义修正设计

## 1. 背景
- 当前通用分页响应 `PageResponse` 会根据 `page`、`size` 和 `total` 计算 `totalPages`、`hasNext`、`hasPrevious`。
- 原有 `hasPrevious` 判断为 `totalPages > 0 && page > 1`，当请求页码超过总页数时，例如 `page = 4`、`totalPages = 3`，仍会返回 `hasPrevious = true`。
- 这会让前端分页控件把一个无效页码误认为有效当前位置，并展示“上一页可用”的状态。

## 2. 目标
- 明确 `hasPrevious` 的语义：只有当前请求页是有效页，并且不是第一页时，才表示存在上一页。
- 修正 `page > totalPages` 时 `hasPrevious` 误判为 `true` 的问题。
- 补充单元测试覆盖越界页场景。

## 3. 非目标
- 不改变分页接口响应字段结构。
- 不在本次改动中新增分页错误码或把越界页改为接口异常。
- 不调整数据库分页查询策略。

## 4. 影响范围
- 涉及模块：`common/api` 通用分页响应。
- 涉及测试：`PageResponseTest`。
- 不涉及数据库表、缓存、外部接口和权限配置。

## 5. 领域建模
- 核心对象：`PageResponse<T>`。
- 关键字段：
  - `page`：当前请求页码，使用 1-based 语义。
  - `size`：每页条数。
  - `total`：满足查询条件的总记录数。
  - `totalPages`：根据 `total` 和 `size` 计算出的总页数。
  - `hasNext`：当前有效页之后是否还有下一页。
  - `hasPrevious`：当前有效页之前是否还有上一页。
- 关键状态：
  - 空结果集：`totalPages = 0`，`hasNext = false`，`hasPrevious = false`。
  - 有效第一页：`page = 1`，`hasPrevious = false`。
  - 有效中间页或末页：`1 < page <= totalPages`，`hasPrevious = true`。
  - 越界页：`page > totalPages`，`hasPrevious = false`。

## 6. API 设计
- 接口列表：不新增接口。
- 请求参数：沿用现有分页参数 `page` 和 `size`。
- 响应结构：沿用现有 `PageResponse` 字段。
- 错误码 / 异常场景：
  - 本次不把 `page > totalPages` 定义为错误。
  - 越界页继续允许返回空列表和分页元数据，但元数据不能暗示该页是有效页。

## 7. 数据设计
- 表结构调整：无。
- 索引设计：无。
- 唯一约束：无。
- 数据一致性考虑：本次只修正响应元数据计算，不改变数据读取一致性模型。

## 8. 关键流程
- 正常流程：
  1. 服务层查询总数 `total`。
  2. `PageResponse.of` 根据 `total` 和 `size` 计算 `totalPages`。
  3. 根据 `page < totalPages` 计算 `hasNext`。
  4. 根据 `totalPages > 0 && page > 1 && page <= totalPages` 计算 `hasPrevious`。
- 异常流程：
  - 当 `page > totalPages` 时，不抛异常，`hasPrevious` 返回 `false`。
- 状态流转：不涉及业务状态流转。

## 9. 并发 / 幂等 / 缓存
- 并发：不涉及库存、订单或并发写入。
- 幂等：不涉及提交类操作。
- 缓存：不涉及缓存读写。

## 10. 权限与安全
- 不改变认证、鉴权和角色权限。
- 该修正只影响分页响应元数据，不扩大接口访问范围。

## 11. 测试策略
- 单元测试：
  - 保留正常分页元数据计算测试。
  - 保留空结果集测试。
  - 新增请求页超过总页数时 `hasPrevious = false` 的测试。
- 集成测试：
  - 本次通用值对象逻辑可由单元测试覆盖，暂不新增集成测试。
- 接口验证：
  - 后续如分页接口暴露越界页场景，可补充 API 手工验证。
- 异常场景验证：
  - 覆盖 `total = 0` 与 `page > totalPages`。

## 12. 风险与替代方案
- 当前方案风险：
  - 如果前端此前依赖越界页 `hasPrevious = true` 来跳回上一页，该行为会变化。但这种依赖本身建立在无效页语义上，不建议保留。
- 备选方案：
  - 方案 A：服务层发现 `page > totalPages` 时直接返回参数错误。
  - 方案 B：服务层自动把越界页纠正为最后一页。
- 为什么不选备选方案：
  - 方案 A 会改变接口错误语义，影响面更大。
  - 方案 B 会让响应中的 `page` 和调用方请求不一致，容易掩盖调用方分页状态问题。
  - 当前阶段选择最小修正：允许越界页返回，但分页元数据必须准确表达“该页不是有效当前位置”。
