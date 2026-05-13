# UserEntity 持久化模型收敛设计

## 1. 背景
- Auth 模块当前同时存在 `UserEntity` 与 `UserCreateParam`：
  - `UserEntity` 是 `users` 表查询结果的 record 快照。
  - `UserCreateParam` 是创建用户时传给 MyBatis 的可写参数对象，用来承接自增主键回填。
- 两者都表达 `users` 表中的账号字段，导致持久化模型在读取与写入路径上出现重复。
- 本次需求要求只保留 `UserEntity`，并将其改为普通类；一个重要原则是 `UserEntity` 字段与数据库表字段一一对应。

## 2. 目标
- 删除 `UserCreateParam`，让注册插入流程直接使用 `UserEntity`。
- 将 `UserEntity` 从 record 改为普通 Java 类。
- 保持 `UserEntity` 字段与 `users` 表字段严格对应：
  - `id`
  - `username`
  - `email`
  - `passwordHash`
  - `status`
  - `createdAt`
  - `updatedAt`
- 保持现有注册、登录、JWT 鉴权、管理员更新用户状态等业务行为不变。
- 保持 MyBatis generated keys 回填 `id` 的能力。

## 3. 非目标
- 不调整 `users` 表结构、索引、唯一约束或迁移脚本。
- 不修改注册、登录、当前用户、管理员用户管理 API 契约。
- 不调整密码哈希算法、JWT 签发逻辑或 RBAC 权限规则。
- 不引入新的 ORM 框架、代码生成器或重量级依赖。
- 不把接口请求 DTO 与持久化实体合并；`RegisterRequest` 仍只表达 HTTP 入参。

## 4. 影响范围
- Auth 持久化对象：`backend/src/main/java/com/eventhub/modules/auth/entity/UserEntity.java`。
- Auth Mapper 接口：`backend/src/main/java/com/eventhub/modules/auth/mapper/UserMapper.java`。
- Auth Mapper XML：`backend/src/main/resources/mapper/auth/UserMapper.xml`。
- Auth 应用服务：`backend/src/main/java/com/eventhub/modules/auth/service/AuthService.java`。
- Auth 认证主体加载服务：`backend/src/main/java/com/eventhub/modules/auth/service/AuthenticatedSubjectService.java`。
- 删除冗余参数对象：`backend/src/main/java/com/eventhub/modules/auth/mapper/param/UserCreateParam.java`。
- 不涉及缓存、消息队列或外部接口。

## 5. 领域建模
- 核心实体：`UserEntity`。
- `UserEntity` 是 `users` 表的持久化对象，只表达数据库行数据，不承载 HTTP 请求校验、密码校验、token 签发或权限判断流程。
- 字段与表字段对应关系：
  - `users.id` -> `UserEntity.id`
  - `users.username` -> `UserEntity.username`
  - `users.email` -> `UserEntity.email`
  - `users.password_hash` -> `UserEntity.passwordHash`
  - `users.status` -> `UserEntity.status`
  - `users.created_at` -> `UserEntity.createdAt`
  - `users.updated_at` -> `UserEntity.updatedAt`
- 关键状态仍是 `UserStatus`：
  - `ENABLED`：允许登录和访问受保护接口。
  - `DISABLED`：禁止登录，旧 token 访问受保护接口时也会被拒绝。

## 6. API 设计
- 不新增或修改 HTTP API。
- 现有接口保持不变：
  - `POST /api/v1/auth/register`
  - `POST /api/v1/auth/login`
  - `POST /api/v1/auth/logout`
  - `GET /api/v1/me`
  - `GET /api/v1/admin/users`
  - `PATCH /api/v1/admin/users/{userId}/status`
- 请求参数、响应结构和错误码不变。
- 内部 Mapper 契约调整：
  - `UserMapper.insert(UserCreateParam param)` 改为 `UserMapper.insert(UserEntity user)`。
  - MyBatis 继续把数据库生成的 `id` 回填到传入的 `UserEntity.id`。

## 7. 数据设计
- 不调整表结构。
- 不调整索引和唯一约束。
- `users` 表字段仍由 `V2__stage_1_auth_jwt_rbac.sql` 定义。
- 插入用户时仍只显式写入 `username`、`email`、`password_hash`、`status`，`id`、`created_at`、`updated_at` 由数据库生成默认值。
- 查询用户时仍读取完整 `users` 字段集合，避免实体字段与表字段漂移。
- 数据一致性继续依赖：
  - `uk_users_username`
  - `uk_users_email`
  - 注册事务内写入用户和默认角色关系。

## 8. 关键流程
- 正常注册流程：
  1. Controller 校验 `RegisterRequest`。
  2. Service 归一化用户名和邮箱。
  3. Service 通过 `existsByUsername` / `existsByEmail` 做前置重复检查。
  4. Service 使用 `UserEntity.enabledUser(...)` 构造待插入用户。
  5. `UserMapper.insert(user)` 插入 `users` 表并回填 `user.id`。
  6. Service 使用回填的 `user.id` 写入默认 USER 角色关系。
  7. Service 重新查询并返回用户摘要。
- 登录与鉴权流程：
  - Mapper 查询返回普通类 `UserEntity`。
  - Service 通过 getter 读取密码哈希、状态、用户名、邮箱和主键。
  - 鉴权主体加载继续查询最新用户状态，确保禁用账号的旧 token 无法继续访问。
- 异常流程：
  - 如果插入影响行数不是 1，或 `id` 未回填，继续快速失败。
  - 并发注册导致唯一约束冲突时，继续把 `DuplicateKeyException` 转换为稳定业务异常。

## 9. 并发 / 幂等 / 缓存
- 本次不新增缓存。
- 本次不新增幂等键。
- 并发注册的最终一致性防线仍是数据库唯一约束。
- `UserEntity` 改为可写普通类后，写路径会短暂持有一个待回填对象；该对象只在单次请求线程内使用，不作为跨线程共享状态。

## 10. 权限与安全
- 不修改 Security 配置和接口访问规则。
- 不改变密码保存策略，数据库中仍只保存 BCrypt 哈希。
- `UserEntity` 仍不暴露给 Controller 响应；接口响应继续使用 `UserInfo`，避免返回 `passwordHash`。
- 普通类不使用 `@Data`，避免为包含 `passwordHash` 的实体生成 `toString()`，降低日志误输出敏感字段的风险。

## 11. 测试策略
- 编译验证：确认删除 `UserCreateParam` 后没有残留引用。
- 集成测试：运行 Auth 集成测试，覆盖注册、重复账号、并发注册、登录、禁用用户、JWT 鉴权和 RBAC。
- 异常场景验证：
  - `id` 回填失败仍会快速失败。
  - 并发重复注册仍只能成功一个请求。
- API 手工验证：如需要，可继续通过 Swagger 或 MockMvc 验证注册和登录响应不包含 `passwordHash`。

## 12. 风险与替代方案
- 当前方案风险：
  - `UserEntity` 从 record 改为普通类后，需要同步调整所有 record accessor 调用。
  - MyBatis resultMap 从构造器映射改为属性映射，字段遗漏会影响查询结果完整性。
  - `UserEntity` 变为可变对象后，需要约束它只作为持久化对象使用，不向响应层泄漏。
- 备选方案 A：继续保留 `UserCreateParam`。
  - 优点是读写模型分离；缺点是与“只保留 `UserEntity`”的需求冲突，且当前字段重复明显。
- 备选方案 B：保留 record，并通过二次查询拿到插入后的用户 id。
  - 没有采用，因为会增加一次数据库查询，且不能满足“改为普通类”的要求。
- 备选方案 C：引入 MyBatis 注解 SQL 或代码生成器重新生成实体。
  - 没有采用，因为本次只是小范围模型收敛，引入新机制会扩大复杂度，不符合最小可用闭环。
