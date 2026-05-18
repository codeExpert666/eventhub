# 管理员用户分页查询实现说明

## 1. 本次改动解决了什么问题

- 将管理员用户查询接口从一次性查询全部用户，调整为按 `page` / `size` 分页查询。
- 补齐 `common` 包中的通用分页请求与分页响应模型，避免后续活动、订单、票种等列表接口重复定义分页结构。
- 给分页参数增加边界限制，防止调用方传入过大的 `size` 制造不受控查询。
- 将管理员用户列表默认排序调整为新注册用户优先，符合管理端查看近期注册账号的常见使用习惯。
- 新增 `username`、`email`、`status`、`createdAt`、`updatedAt` 筛选能力。
- 优化列表接口角色查询，把逐用户查询角色调整为按当前页用户 ID 批量查询，消除列表路径上的 N+1 查询。

## 2. 改动内容
- 新增了什么
  - 新增 `PageRequest`：
    - 统一定义默认页码 `1`、默认页大小 `20`、最大页大小 `100`。
    - 提供 `offset()`，将 1-based 页码转换为 SQL `OFFSET`。
  - 新增 `PageResponse<T>`：
    - 统一返回 `items`、`page`、`size`、`total`、`totalPages`、`hasNext`、`hasPrevious`。
  - 新增 `AdminUserQueryRequest`：
    - 承接管理员用户列表的 GET 查询参数。
    - 包含分页、账号字段筛选、状态筛选和时间范围筛选。
    - 使用 Bean Validation 校验分页边界、字段长度、状态取值和时间范围。
  - 新增 `UserQueryCriteria`：
    - 作为 Service 传给 Mapper 的查询条件对象。
    - 对 `username`、`email` 等筛选值做 trim / normalize。
  - 新增 `UserRoleCodeResult`：
    - 承接批量角色查询的 `userId + roleCode` 扁平结果。
  - 新增 `PageRequestTest` 和 `PageResponseTest`，覆盖分页值对象核心规则。
- 修改了什么
  - `AdminUserController.listUsers`：
    - 从散落的分页参数改为 `@ModelAttribute AdminUserQueryRequest`。
    - 响应保持 `ApiResponse<PageResponse<UserInfo>>`。
  - `AuthService.listUsers`：
    - 方法参数改为 `AdminUserQueryRequest`。
    - 内部转换出 `PageRequest` 和 `UserQueryCriteria`。
    - 当前页用户角色改为批量查询并按用户 ID 分组。
  - `UserMapper` / `UserMapper.xml`：
    - 删除全量查询契约 `findAll`。
    - 新增 `countByCriteria` 统计筛选后的总数。
    - `findPage` 支持筛选条件、`LIMIT` / `OFFSET`，并按 `created_at DESC, id DESC` 排序。
  - `RoleMapper` / `RoleMapper.xml`：
    - 新增 `findRoleCodesByUserIds`，用于按当前页用户 ID 批量查角色编码。
  - `GlobalExceptionHandler`：
    - 新增 `BindException` 处理，确保 GET 查询对象绑定失败或校验失败时返回统一 400。
  - `AuthIntegrationTest`：
    - 更新管理员查询用户列表的响应断言。
    - 新增分页参数、新注册用户优先排序、字段筛选、时间范围筛选、非法筛选参数的集成测试。
- 删除了什么
  - 删除管理员用户列表路径上的全量查询 Mapper 方法。

## 3. 为什么这样设计
- 分页模型放在 `common.api`：
  - 分页是跨业务模块的接口协议，不属于 Auth 私有能力。
  - 后续活动列表、场次列表、订单列表都可以直接复用同一响应结构。
- 管理员查询参数使用 `AdminUserQueryRequest`：
  - GET 查询参数已经从 `page/size` 扩展到多个筛选字段，继续堆在 Controller 方法签名上会变得难读。
  - 查询对象可以集中维护默认值、校验规则和转换逻辑。
- 继续手写 MyBatis SQL：
  - 当前项目已经使用 Mapper XML 管理 SQL。
  - `COUNT + WHERE + ORDER BY + LIMIT/OFFSET` 对当前需求足够清晰，不需要为了一个接口引入分页插件。
- 使用 `created_at DESC, id DESC` 排序：
  - `created_at DESC` 表达“新注册用户优先”。
  - `id DESC` 作为同一时间戳下的稳定兜底排序，避免分页边界排序不稳定。
- 角色批量查询：
  - 列表页最多返回 100 个用户，如果继续逐个调用 `findRoleCodesByUserId`，查询次数会随用户数线性增长。
  - 批量查询一次返回扁平结果，再在内存中按 `userId` 分组，逻辑清晰且成本可控。

## 4. 替代方案
- 方案 A：引入 PageHelper
  - 优点是减少分页 SQL 样板代码。
  - 未采用原因是当前接口的筛选、排序和角色批量查询都需要显式 SQL 设计；引入依赖会增加学习和维护成本。
- 方案 B：使用游标分页
  - 优点是深翻页性能更好。
  - 未采用原因是管理端列表通常需要页码和总数；当前阶段用户量不大，`LIMIT/OFFSET` 更直接。
- 方案 C：只在 Auth 模块定义专用分页响应
  - 优点是局部改动更小。
  - 未采用原因是分页会成为多个模块的通用能力，专用响应会造成后续重复和接口风格不统一。
- 方案 D：将 `username` / `email` 设计为精确匹配
  - 优点是更容易利用唯一索引。
  - 未采用原因是管理端查询更常见的是输入片段搜索账号；当前阶段选择包含匹配提升可用性。
- 方案 E：一次性新增排序参数
  - 优点是管理端可灵活切换排序。
  - 未采用原因是用户明确提出的排序诉求是“新注册用户优先”，动态排序还需要白名单、索引和前端交互约束。

## 5. 测试与验证
- 跑了哪些测试
  - 执行 `mvn test`。
  - 结果：45 个测试全部通过，0 失败，0 错误。
- 手工验证了哪些场景
  - 本次未启动应用做 curl 手工验证；分页、筛选和权限链路已通过 MockMvc 集成测试覆盖。
- 结果如何
  - `PageRequest` 默认值、offset 和非法参数单元测试通过。
  - `PageResponse` 总页数、翻页标识和 items 防变更单元测试通过。
  - 管理员用户列表默认分页结构集成测试通过。
  - `page=1&size=1` 限制返回条数集成测试通过。
  - 新注册用户优先排序集成测试通过。
  - `username` / `email` / `status` 组合筛选集成测试通过。
  - `createdAt` / `updatedAt` 范围筛选集成测试通过。
  - `page=0`、`size=101`、`status=LOCKED`、非法时间范围返回 400 的集成测试通过。
  - 普通用户访问管理员用户列表仍返回 403。

## 6. 已知限制
- `username` / `email` 当前使用包含匹配，普通 BTree 索引无法充分支撑 `LIKE '%xxx%'`，后续用户量增长后需要评估前缀匹配、全文索引或搜索服务。
- `created_at DESC, id DESC` 当前没有配套组合索引；后续可以结合真实数据量新增 `(created_at, id)` 组合索引。
- `status` 筛选当前没有单独索引；如果后台高频按状态查看用户，可考虑 `(status, created_at, id)` 组合索引。
- `COUNT(*)` 在用户量非常大时可能成为成本点，后续可以结合筛选条件、近似计数或缓存策略再评估。
- 总数查询、分页数据查询和角色批量查询不是同一个数据库快照；查询过程中如果有用户新增或角色变化，分页元数据和 items 可能有轻微读偏差。当前管理端列表可以接受。

## 7. 对后续版本的影响
- 对简历可用版的价值：
  - 展示了从全量查询演进到分页、筛选、排序和 N+1 优化的完整列表接口改造过程。
  - 提供了可复用的分页响应契约，后续列表接口能保持一致。
  - 能在面试中解释为什么列表接口不能只做 `findAll`，以及如何识别并优化 N+1 查询。
- 对微服务 / 云原生演进的影响：
  - 分页请求和响应模型沉淀在 common 层，后续拆分服务时可以作为 API 契约的一部分迁移。
  - 当前实现没有绑定分页插件，减少未来拆分或替换数据访问层时的耦合。
  - 查询条件对象和批量角色查询让 Auth 模块的数据访问边界更清楚，后续拆分独立认证服务时更容易迁移。
