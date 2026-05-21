# PageResponse hasPrevious 越界页语义修正实现说明

## 1. 本次改动解决了什么问题

修正 `PageResponse` 在请求页码超过总页数时 `hasPrevious` 误判的问题。

原逻辑只判断 `totalPages > 0 && page > 1`。当 `page = 4`、`totalPages = 3` 时，`hasPrevious` 会返回 `true`，这会把无效页码误认为存在上一页的有效当前位置。

## 2. 改动内容
- 新增了什么
  - 新增单元测试 `pageBeyondTotalPagesShouldNotHavePreviousPage`，覆盖 `page > totalPages` 场景。
- 修改了什么
  - `PageResponse.of` 中先提取 `page` 和 `size` 局部变量，减少重复读取。
  - `hasPrevious` 判断调整为 `totalPages > 0 && page > 1 && page <= totalPages`。
  - 补充 `PageResponse.of` 方法注释，说明 `hasPrevious` 只在当前页处于有效页码范围内时才返回 `true`。
- 删除了什么
  - 未删除公共 API 字段或方法。

## 3. 为什么这样设计
- 关键设计原因
  - `hasPrevious` 应表达“当前有效页是否存在上一页”，而不是单纯表达“请求页码是否大于 1”。
  - 越界页不是有效当前位置，因此不能返回 `hasPrevious = true`。
- 与项目当前阶段的匹配点
  - 保持通用分页响应的最小可用闭环。
  - 不改变 Controller、Service、Mapper 分层契约，降低影响面。
  - 通过单元测试锁定公共值对象行为，方便后续活动、场次、订单等列表接口复用。

## 4. 替代方案
- 方案 A
  - 在服务层发现 `page > totalPages` 时直接抛出参数错误。
- 方案 B
  - 自动把越界页纠正为最后一页，再返回分页数据。
- 为什么没有采用
  - 方案 A 会改变现有接口错误语义，可能影响已经依赖“越界页返回空列表”的调用方。
  - 方案 B 会让响应页码和请求页码不一致，容易隐藏前端分页状态问题。
  - 当前改动选择最小行为修正：越界页仍可返回，但分页元数据不能误导调用方。

## 5. 测试与验证
- 跑了哪些测试
  - `mvn test -Dtest=PageResponseTest`
- 手工验证了哪些场景
  - 正常分页：`page = 2`、`totalPages = 3` 时存在上一页和下一页。
  - 空结果集：`totalPages = 0` 时不存在上一页和下一页。
  - 越界页：`page = 4`、`totalPages = 3` 时不存在上一页和下一页。
- 结果如何
  - `PageResponseTest` 共 5 个测试全部通过。

## 6. 已知限制
- 当前版本还缺什么
  - 还没有统一定义 `page > totalPages` 在 HTTP API 层是否应视为业务错误。
  - 目前保持“允许越界页返回空结果”的宽松语义。
- 哪些地方后面需要继续演进
  - 当列表接口增多后，可以统一评估是否需要分页参数规范文档。
  - 如果前端需要自动回到最后一页，应由前端交互或明确 API 设计支持，而不是由通用响应对象隐式修正。

## 7. 对后续版本的影响
- 对简历可用版的价值
  - 提升通用分页组件的边界场景准确性，减少管理端分页控件误判。
- 对微服务 / 云原生演进的影响
  - 分页响应语义更稳定，后续拆分服务时可以作为跨服务列表接口的基础响应约定。
