# 管理员用户分页查询设计

## 1. 背景
- 当前 `GET /api/v1/admin/users` 已从全量列表调整为分页查询，响应为 `PageResponse<UserInfo>`。
- 管理端用户列表仍有三个明显演进点：
  - 默认按 `id ASC` 排序时，管理员优先看到的是早期用户，不符合“更关心新注册用户”的后台使用习惯。
  - 只能分页，不能按账号字段、状态或时间字段筛选。
  - 当前页用户在组装 `UserInfo.roles` 时仍逐个查询角色，存在 N+1 查询问题。
- 本次继续在同一个管理员用户列表能力上迭代，不新增独立设计文档。

## 2. 目标
- 管理员用户列表默认按新注册用户优先排序。
- 支持按 `username`、`email`、`status`、`createdAt`、`updatedAt` 筛选。
- 优化列表接口中的角色查询，将逐用户查询角色调整为按当前页用户 ID 批量查询。
- 保持分页响应结构和管理员权限边界不变。
- 成功标准：
  - `GET /api/v1/admin/users?page=1&size=20` 返回最新注册用户优先的分页结果。
  - `username` / `email` 支持包含匹配。
  - `status` 支持 `ENABLED` / `DISABLED` 精确筛选。
  - `createdAtFrom` / `createdAtTo` 支持创建时间范围筛选。
  - `updatedAtFrom` / `updatedAtTo` 支持更新时间范围筛选。
  - 当前页角色信息通过一次批量 SQL 查回，不再按用户数量线性增加角色查询次数。

## 3. 非目标
- 本次不做角色筛选。
- 本次不开放动态排序字段，避免排序字段白名单、索引设计和前端契约同时扩散。
- 本次不引入 PageHelper、Spring Data Page 或其他分页依赖。
- 本次不调整 `users`、`roles`、`user_roles` 表结构。
- 本次不新增数据库迁移；索引优化留到数据量增长或查询计划验证后再单独处理。

## 4. 影响范围
- `common.api`
  - 继续复用 `PageRequest` 和 `PageResponse<T>`。
- `common.exception`
  - 补充 GET 查询对象绑定失败的统一 400 处理，覆盖页码类型错误、日期格式错误等场景。
- `modules.auth.dto.request`
  - 新增管理员用户查询请求对象，承接分页和筛选参数。
- `modules.auth.controller`
  - 管理员用户查询接口由多个散落的 `@RequestParam` 收敛为一个查询请求对象。
- `modules.auth.service`
  - 用户列表服务方法改为接收查询请求，并组装分页、筛选和角色批量查询结果。
- `modules.auth.mapper`
  - 用户分页 SQL 增加筛选条件和 `created_at DESC, id DESC` 排序。
  - 角色 Mapper 增加按用户 ID 集合批量查询角色编码的方法。
- 数据库
  - 继续使用 `users`、`roles`、`user_roles` 表。
  - 不新增缓存或外部接口。

## 5. 领域建模
- `PageRequest`
  - 通用分页请求值对象。
  - 只表达分页规则本身，不承接 HTTP 参数绑定、字段格式化或业务筛选条件。
  - 当前实现为 Java `record`，天然不可继承；后续也应优先保持不可变值对象语义。
  - 字段：
    - `page`：从 1 开始的页码。
    - `size`：每页条数。
  - 派生能力：
    - `offset()`：转换为 SQL `OFFSET`。
- `PageResponse<T>`
  - 通用分页响应值对象。
  - 字段：
    - `items`：当前页数据。
    - `page`：当前页码。
    - `size`：每页条数。
    - `total`：总记录数。
    - `totalPages`：总页数。
    - `hasNext`：是否存在下一页。
    - `hasPrevious`：是否存在上一页。
- `AdminUserQueryRequest`
  - 管理员用户列表 GET 查询请求对象。
  - 与 `PageRequest` 是组合关系，不是继承关系：
    - `AdminUserQueryRequest` 属于 Web 入参 DTO，需要默认值、setter、`@DateTimeFormat` 和 Bean Validation。
    - `PageRequest` 属于通用分页值对象，用于服务层内部表达已经校验后的分页规则。
    - 管理员用户查询请求“包含分页参数”，但不应被建模为“分页请求的一种子类”。
  - 字段：
    - `page` / `size`：分页参数。
    - `username`：用户名包含匹配。
    - `email`：邮箱包含匹配。
    - `status`：用户状态精确匹配。
    - `createdAtFrom` / `createdAtTo`：创建时间范围。
    - `updatedAtFrom` / `updatedAtTo`：更新时间范围。
- `UserQueryCriteria`
  - Service 传给 Mapper 的查询条件对象。
  - 负责保存已经 trim / normalize 后的筛选字段。
- `UserRoleCodeResult`
  - 批量角色查询的扁平结果行。
  - 字段：
    - `userId`
    - `roleCode`
- `UserEntity`
  - 仍表示 `users` 表持久化对象。
- `UserInfo`
  - 仍作为对外用户摘要，避免泄露 `passwordHash`。
- 状态
  - 用户状态仍为 `ENABLED` / `DISABLED`，本次不改变状态机。

## 6. API 设计
- 接口：

```http
GET /api/v1/admin/users?page=1&size=20
GET /api/v1/admin/users?username=alice&email=example.com&status=ENABLED
GET /api/v1/admin/users?createdAtFrom=2026-05-01T00:00:00&createdAtTo=2026-05-18T23:59:59
GET /api/v1/admin/users?updatedAtFrom=2026-05-01T00:00:00&updatedAtTo=2026-05-18T23:59:59
```

- 请求参数：
  - `page`：可选，默认 `1`，最小 `1`。
  - `size`：可选，默认 `20`，最小 `1`，最大 `100`。
  - `username`：可选，最长 32，trim 后为空则忽略；使用包含匹配。
  - `email`：可选，最长 128，trim 后为空则忽略；会转小写后使用包含匹配。
  - `status`：可选，只允许 `ENABLED` 或 `DISABLED`。
  - `createdAtFrom`：可选，ISO 日期时间，例如 `2026-05-01T00:00:00`。
  - `createdAtTo`：可选，ISO 日期时间，例如 `2026-05-18T23:59:59`。
  - `updatedAtFrom`：可选，ISO 日期时间。
  - `updatedAtTo`：可选，ISO 日期时间。

- 响应结构：

```json
{
  "code": "COMMON-000",
  "message": "成功",
  "data": {
    "items": [
      {
        "id": 10,
        "username": "alice",
        "email": "alice@example.com",
        "status": "ENABLED",
        "roles": ["USER"]
      }
    ],
    "page": 1,
    "size": 20,
    "total": 1,
    "totalPages": 1,
    "hasNext": false,
    "hasPrevious": false
  },
  "requestId": "xxx",
  "timestamp": "2026-05-18T10:00:00+08:00"
}
```

- 错误场景：
  - 未登录访问：401，沿用安全层认证失败响应。
  - 非 `ADMIN` 访问：403，沿用安全层权限不足响应。
  - `page < 1`：400，统一参数校验失败。
  - `size < 1` 或 `size > 100`：400，统一参数校验失败。
  - `status` 非 `ENABLED` / `DISABLED`：400，统一参数校验失败。
  - 日期格式无法绑定为 `LocalDateTime`：400，统一参数校验失败。
  - `createdAtFrom > createdAtTo` 或 `updatedAtFrom > updatedAtTo`：400，统一参数校验失败。

## 7. 数据设计
- 表结构调整：
  - 无。
- 索引设计：
  - 排序调整为 `ORDER BY created_at DESC, id DESC`，更符合管理端“新注册用户优先”的默认视图。
  - 当前 `users.id` 有主键索引，`created_at` 暂无单独索引；本次不新增迁移，后续如果用户量增长，应评估新增 `(created_at, id)` 组合索引。
  - `username`、`email` 当前有唯一索引，但包含匹配 `LIKE '%xxx%'` 不能充分利用普通 BTree 索引；短期作为管理后台低频查询可接受。
  - `status` 暂无索引；后续如果大量按状态筛选，可以评估 `(status, created_at, id)` 组合索引。
- 唯一约束：
  - `uk_users_username`、`uk_users_email` 不变。
- 数据一致性考虑：
  - 分页列表先查询总数，再查询当前页数据，两次查询之间如果有用户新增或状态变化，可能出现轻微读偏差。
  - 管理端列表是查询型接口，当前阶段接受这种最终一致的读视图；不为此引入事务快照或锁。

## 8. 关键流程
- 正常流程：
  1. 管理员携带有效 JWT 请求 `GET /api/v1/admin/users`。
  2. Spring MVC 将查询参数绑定到 `AdminUserQueryRequest`。
  3. Bean Validation 校验分页、状态、字段长度和时间范围。
  4. Controller 调用 `AuthService.listUsers(request)`。
  5. Service 从请求对象中构造 `PageRequest` 和 `UserQueryCriteria`。
  6. Service 按筛选条件查询 `users` 总数。
  7. Service 使用同一筛选条件、`ORDER BY created_at DESC, id DESC`、`LIMIT`、`OFFSET` 查询当前页用户。
  8. Service 提取当前页用户 ID，一次性调用 RoleMapper 批量查询角色编码。
  9. Service 按用户 ID 分组角色编码，将 `UserEntity` 转为 `UserInfo` 并组装 `PageResponse<UserInfo>`。
  10. Controller 返回统一 `ApiResponse`。
- 异常流程：
  - 参数不合法：Bean Validation 或绑定异常由全局异常处理器转为 400。
  - 无权限：Spring Security 拦截，返回 401 或 403。
- 状态流转：
  - 无新增状态流转。

## 9. 并发 / 幂等 / 缓存
- 并发：
  - 本接口只读，不涉及库存扣减、订单创建或状态写入，不存在超卖风险。
  - 总数查询、分页数据查询和角色批量查询之间不加锁，避免为了管理端列表牺牲写入吞吐。
- 幂等：
  - GET 查询天然幂等，相同参数在数据未变化时返回同一页视图。
- 缓存：
  - 本次不缓存用户分页结果。用户状态、角色绑定和时间筛选都会影响列表，如果过早缓存需要额外失效策略，当前收益不高。

## 10. 权限与安全
- 访问角色：
  - 仅 `ADMIN`。
- 鉴权与鉴别约束：
  - 继续依赖 `SecurityConfig` 路径规则和 `@PreAuthorize("hasRole('ADMIN')")` 双层约束。
  - 响应继续使用 `UserInfo`，不返回 `passwordHash`。
  - `size` 最大值限制为 100，防止调用方绕过分页意图制造大查询。
  - `username`、`email` 作为绑定参数进入 MyBatis，SQL 使用参数占位，不拼接原始输入，避免 SQL 注入。

## 11. 测试策略
- 单元测试：
  - 覆盖 `PageRequest` 的默认值、偏移量和非法参数。
  - 覆盖 `PageResponse` 的总页数、上一页和下一页计算。
- 集成测试：
  - 管理员访问用户列表返回分页结构。
  - 默认排序返回新注册用户优先。
  - 指定 `page` / `size` 可限制返回条数。
  - `username` / `email` / `status` 筛选可组合使用。
  - `createdAt` / `updatedAt` 范围筛选可用。
  - 非法分页参数、非法状态、非法时间范围返回 400。
  - 普通用户访问仍返回 403。
- 接口验证：
  - 可通过 Swagger 或 curl 手工验证 `GET /api/v1/admin/users?page=1&size=20&status=ENABLED`。
- 异常场景验证：
  - `page=0`。
  - `size=0`。
  - `size=101`。
  - `status=LOCKED`。
  - `createdAtFrom` 晚于 `createdAtTo`。

## 12. 风险与替代方案
- 当前方案风险：
  - `LIKE '%xxx%'` 不适合超大用户表上的高频搜索，后续可能需要改为前缀匹配、全文索引或专门搜索服务。
  - `COUNT(*)` 在用户量很大且筛选条件复杂时也会成为成本点；当前阶段用户表简单，先接受。
  - `created_at DESC, id DESC` 当前没有组合索引，后续应结合真实数据量和查询计划评估索引迁移。
- 备选方案 A：引入 PageHelper。
  - 优点：减少手写 `LIMIT/OFFSET` 和 `COUNT`。
  - 未采用原因：本项目当前处于学习型单体阶段，手写 SQL 更能保持数据访问行为透明；为一个接口引入依赖收益不明显。
- 备选方案 B：使用游标分页。
  - 优点：大数据量深翻页性能更好。
  - 未采用原因：管理端列表当前需要总数和页码体验，且短期数据量不大；游标分页会提高前端与接口理解成本。
- 备选方案 C：只做分页，不做筛选和角色批量查询。
  - 优点：改动更小。
  - 未采用原因：用户已经明确提出筛选和 N+1 优化需求，本次应一次性补齐列表查询的基础可用性。
- 备选方案 D：一次性支持动态排序字段。
  - 优点：管理端灵活性更高。
  - 未采用原因：动态排序需要白名单、索引和前端交互约束；当前最明确的业务需求是“新注册用户优先”，固定排序更稳。
- 备选方案 E：让 `AdminUserQueryRequest` 继承 `PageRequest`，并在后续流程中统一使用 `AdminUserQueryRequest`。
  - 优点：表面上减少一次 `toPageRequest()` 转换，调用链看起来更短。
  - 未采用原因：
    - `PageRequest` 当前是 Java `record`，不能被继承；如果为继承改成普通类，会削弱它作为不可变分页值对象的语义。
    - `AdminUserQueryRequest` 是 Spring MVC 绑定用的可变 Web DTO，带有 setter、默认值、日期格式化和 HTTP 参数校验；`PageRequest` 是通用分页规则，二者生命周期和职责不同。
    - 继承表达 is-a 关系，但管理员用户查询请求并不是“分页请求的一种”，而是“包含分页参数的用户查询请求”，组合比继承更准确。
    - 如果 Mapper 直接接收 `AdminUserQueryRequest`，持久化层会依赖 Web DTO 中的字符串状态、格式化注解和原始输入形态，削弱 Controller / Service / Mapper 的分层边界。
- 备选方案 F：移除 `UserQueryCriteria`，让 Mapper 直接使用 `AdminUserQueryRequest`。
  - 优点：少一个参数对象。
  - 未采用原因：`UserQueryCriteria` 承接的是已经 trim、normalize、枚举转换后的数据库查询条件；保留它可以避免 MyBatis XML 直接理解 HTTP 入参细节，也方便后续在服务层组合更多查询来源。
