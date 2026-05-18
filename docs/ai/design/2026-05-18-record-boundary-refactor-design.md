# Record 与 Lombok 边界重构设计

## 1. 背景
- 当前项目使用 Java `record` 表达只读 DTO、VO 和简单值对象，这与前期 Lombok ADR 中“简单只读 DTO/VO 优先 record”的原则一致。
- 本轮重构最初拆成了 record 边界、Lombok 注解最小化、record 风格访问方法移除、`AuthenticatedPrincipal` 保留 record 等多个文档。
- 这些改动本质上都服务同一个目标：明确当前阶段不可变模型、record、普通类和 Lombok 注解的使用边界。
- 根据最新代码状态，真正从 record 改为普通类的是 `PageResponse`：
  - 它包含 `totalPages`、`hasNext`、`hasPrevious` 等派生字段。
  - record 的公开 canonical constructor 会允许调用方绕过 `of(...)`，手工构造不一致分页元数据。
- `AuthenticatedPrincipal` 当前只保存最小身份快照，没有复杂行为、隐藏构造入口或 `UserDetails` 集成需求，因此继续保留 record。

## 2. 目标
- 用一套文档沉淀本次重构结论，避免同一次重构在设计、实现和 ADR 层面各生成多份碎片文档。
- 建立当前阶段的 record 使用判断规则：
  - 纯请求/响应 DTO、简单值对象、JWT 最小 claims、最小认证身份快照继续使用 `record`。
  - 需要隐藏构造入口或约束派生字段一致性的对象改为普通类。
- 将 `PageResponse` 从 `record` 改为不可变普通类，并通过私有构造器 + 静态工厂维护分页元数据一致性。
- 只保留当前真实需要的 Lombok 注解：`PageResponse` 上的 `@Getter`。
- 移除 `PageResponse` 的 record 风格访问方法，普通类统一使用 JavaBean getter。
- `AuthenticatedPrincipal` 保持 Java `record`，继续使用 `userId()`、`username()`、`authorities()` record accessor。
- 保持现有 REST API JSON 字段、认证流程和安全规则不变。

## 3. 非目标
- 不把所有 DTO/VO 统一改成普通类。
- 不调整 `ApiResponse`、`PageRequest`、登录/注册请求、用户响应、system 示例响应等仍然适合 record 的类型。
- 不把 `AuthenticatedPrincipal` 提前升级为普通类、`UserDetails` 或更复杂的安全主体模型。
- 不保留当前没有实际调用方的 Lombok `@EqualsAndHashCode`、`@ToString`。
- 不使用 `@Data`，避免生成 setter 破坏不可变边界。
- 不改变分页 API 的字段命名和响应结构。
- 不改变 Spring Security 认证流程、JWT 内容、数据库表结构或权限规则。

## 4. 影响范围
- `backend/src/main/java/com/eventhub/common/api/PageResponse.java`
  - 从 record 改为普通不可变类。
  - 保留 `of(...)` 工厂方法。
  - 通过私有构造器收口创建入口。
  - 通过 Lombok `@Getter` 生成 JavaBean getter，以保证 Jackson 序列化和内部读取。
  - 不再暴露 `items()`、`page()`、`hasNext()` 等 record 风格访问方法。
- `backend/src/main/java/com/eventhub/infra/security/principal/AuthenticatedPrincipal.java`
  - 保持 record。
  - 继续防御性复制权限集合。
- 测试影响：
  - `PageResponseTest` 覆盖分页元数据和 items 不可变性，并统一使用 JavaBean getter。
  - `AuthIntegrationTest` 覆盖认证链路，确认 principal 保持 record 不影响 JWT 认证。
- 文档影响：
  - 本次只保留一份设计文档、一份实现说明、一份 ADR。
  - 删除同次重构下按小步骤拆出的重复文档。
- 不涉及表、缓存、外部接口。

## 5. 领域建模
- `PageResponse<T>`
  - 通用分页响应模型。
  - 核心字段：`items`、`page`、`size`、`total`。
  - 派生字段：`totalPages`、`hasNext`、`hasPrevious`。
  - 约束：派生字段只能由 `of(items, pageRequest, total)` 根据统一规则计算，避免不同模块手写分页元数据。
  - 类型选择：普通不可变类，因为它需要隐藏全参构造入口。
- `AuthenticatedPrincipal`
  - 当前登录用户在 Spring Security 上下文中的最小身份快照。
  - 字段：`userId`、`username`、`authorities`。
  - 约束：权限集合在 compact constructor 中复制为不可变列表。
  - 类型选择：record，因为当前只有不可变数据快照职责。

## 6. API 设计
- 对外 REST API 不变。
- `PageResponse` JSON 字段保持：
  - `items`
  - `page`
  - `size`
  - `total`
  - `totalPages`
  - `hasNext`
  - `hasPrevious`
- Java 内部调用：
  - `PageResponse` 使用 `getItems()`、`getPage()`、`getTotalPages()`、`isHasNext()`、`isHasPrevious()` 等 JavaBean getter。
  - `AuthenticatedPrincipal` 继续使用 `userId()`、`username()`、`authorities()` record accessor。

## 7. 数据设计
- 本次不调整数据库表结构、索引、唯一约束或迁移脚本。
- 分页查询仍由现有 Mapper 负责 `LIMIT/OFFSET`，`PageResponse` 只负责表达接口响应元数据。
- `AuthenticatedPrincipal` 仍从 `users` 与 `roles` 查询结果组装，不新增持久化字段。

## 8. 关键流程
- 分页响应流程：
  1. Controller 接收查询请求。
  2. Service 构造 `PageRequest` 并查询总数与当前页数据。
  3. Service 调用 `PageResponse.of(...)`。
  4. `PageResponse` 内部统一计算 `totalPages`、`hasNext`、`hasPrevious` 并复制 items。
  5. Jackson 通过 Lombok 生成的 getter 输出原有 JSON 字段。
- 认证主体流程：
  1. `JwtAuthenticationFilter` 解析 JWT。
  2. `AuthenticatedPrincipalService` 查询用户状态和角色。
  3. 构造 `AuthenticatedPrincipal` record，权限集合复制为不可变列表。
  4. 过滤器通过 `principal.authorities()` 构造 Spring Security 权限。
  5. 过滤器将主体写入 `SecurityContext`。

## 9. 并发 / 幂等 / 缓存
- 本次重构不涉及库存、订单、支付或写入幂等逻辑，不引入超卖风险。
- `PageResponse` 不缓存查询结果，只复制当前页列表，避免调用方后续修改列表影响响应内容。
- `AuthenticatedPrincipal` 不引入缓存，仍保持每次受保护请求加载最新用户状态和角色的策略。
- `authorities` 不可变可以避免同一次请求内因外部集合被修改导致授权判断变化。

## 10. 权限与安全
- `AuthenticatedPrincipal` 继续只保存认证鉴权最小信息，避免把完整用户资料放入 `SecurityContext`。
- 构造器继续只接收最小身份信息，不包含邮箱、手机号、头像、密码哈希或 token。
- `authorities` 继续防御性复制，保证认证完成后的权限快照不可被调用方修改。
- 当前不实现 `UserDetails`，避免把更重的 Spring Security 用户模型提前引入业务边界。
- record 自动生成 `toString()`，但当前 `AuthenticatedPrincipal` 字段不包含敏感资料；如果后续加入敏感字段，需要重新评估是否继续使用 record。
- 现有 `/api/v1/admin/**`、`/api/v1/me` 等安全规则不变。

## 11. 测试策略
- 单元测试：
  - `PageResponseTest` 验证分页元数据计算、空结果行为、items 不可变性和 JavaBean getter。
  - `ApiResponseTest`、`PageRequestTest` 确认相邻通用 API 模型未被破坏。
- 集成测试：
  - `AuthIntegrationTest` 验证登录、JWT 认证、权限不足、禁用用户旧 token、管理员分页 JSON 字段等链路仍可用。
- 静态检查：
  - 使用 `rg` 搜索 `PageResponse` 是否还残留 record 风格访问方法。
  - 使用 `rg` 搜索 `AuthenticatedPrincipal` 是否误用 JavaBean getter。
- 构建验证：
  - 执行 `mvn -q -Dtest=PageResponseTest,AuthIntegrationTest test`。
  - 执行 `mvn -q test` 做全量回归。

## 12. 风险与替代方案
- 风险：`PageResponse` 普通类缺少 getter 时 Jackson 可能无法按原 JSON 字段序列化。
  - 应对：为 `PageResponse` 使用 Lombok `@Getter` 生成 JavaBean getter。
- 风险：Lombok boolean getter 命名影响测试或 JSON 字段。
  - 应对：测试使用 `isHasNext()` / `isHasPrevious()`，集成测试验证 JSON `hasNext` / `hasPrevious`。
- 风险：未来 `AuthenticatedPrincipal` 加入敏感字段时，record 自动 `toString()` 可能不再合适。
  - 应对：等出现明确复杂度后再重构为普通类，并显式控制输出字段。
- 备选方案 A：保留所有 record，仅补充注释说明使用边界。
  - 不采用原因：`PageResponse` 的公开构造入口仍然允许构造不一致派生字段。
- 备选方案 B：把所有 record 全部改成 Lombok 普通类。
  - 不采用原因：登录/注册请求、用户响应、JWT claims、`AuthenticatedPrincipal` 等仍是清晰的不可变数据载体。
- 备选方案 C：将 `AuthenticatedPrincipal` 提前改成普通类。
  - 不采用原因：当前没有 `UserDetails`、租户上下文、权限版本等实际需求。
- 备选方案 D：继续保留 `PageResponse` 的 record 风格兼容方法。
  - 不采用原因：普通类长期暴露两套访问风格会增加维护成本。
- 备选方案 E：移除所有 Lombok 注解并手写 `PageResponse` getter。
  - 不采用原因：`@Getter` 是当前真实需要且低风险的样板代码替代。
