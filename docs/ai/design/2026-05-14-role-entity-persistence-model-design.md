# RoleEntity 持久化模型重构设计

## 1. 背景
- Auth 模块的 `RoleEntity` 当前是 Java record，并通过 MyBatis constructor resultMap 从 `roles` 表读取数据。
- 近期 `UserEntity` 已经收敛为普通 Java 类，并明确字段与数据库表字段一一对应。
- 本次需求要求将 `RoleEntity` 从 record 重构为普通类，同时保证实体字段和 `roles` 表字段保持一一对应。

## 2. 目标
- 将 `RoleEntity` 从 record 改为普通 Java 类。
- 保持 `RoleEntity` 字段与 `roles` 表字段严格对应：
  - `id`
  - `code`
  - `name`
  - `description`
  - `createdAt`
- 将 MyBatis 映射从构造器映射调整为普通类属性映射。
- 保持注册时绑定默认 USER 角色、查询用户角色编码等现有业务行为不变。

## 3. 非目标
- 不调整 `roles` 表结构、索引、唯一约束或 Flyway 迁移脚本。
- 不修改 HTTP API 契约。
- 不修改 RBAC 权限语义、角色编码规则或 Spring Security 配置。
- 不引入新的 ORM 框架、代码生成器或重量级依赖。
- 不为角色增加写接口、角色状态或权限点模型。

## 4. 影响范围
- Auth 持久化对象：`backend/src/main/java/com/eventhub/modules/auth/entity/RoleEntity.java`。
- Auth Mapper XML：`backend/src/main/resources/mapper/auth/RoleMapper.xml`。
- Auth 应用服务：`backend/src/main/java/com/eventhub/modules/auth/service/AuthService.java`。
- 数据表：只读取已有 `roles` 表，不改变表结构。
- 不涉及缓存、消息队列、外部接口或数据库迁移。

## 5. 领域建模
- 核心实体：`RoleEntity`。
- `RoleEntity` 是 `roles` 表的持久化对象，只表达数据库行数据，不承载权限判断或接口响应组装逻辑。
- 字段与表字段对应关系：
  - `roles.id` -> `RoleEntity.id`
  - `roles.code` -> `RoleEntity.code`
  - `roles.name` -> `RoleEntity.name`
  - `roles.description` -> `RoleEntity.description`
  - `roles.created_at` -> `RoleEntity.createdAt`
- 角色编码仍是权限判断的稳定输入，例如 `USER`、`ADMIN`。

## 6. API 设计
- 不新增或修改 HTTP API。
- 请求参数、响应结构和错误码不变。
- 内部 Mapper 契约保持不变：
  - `RoleMapper.findByCode(String code)` 继续返回 `Optional<RoleEntity>`。
  - `RoleMapper.findRoleCodesByUserId(Long userId)` 继续返回角色编码列表。
  - `RoleMapper.addRoleToUser(Long userId, Long roleId)` 继续写入用户角色关系。
- 服务层访问角色主键时，从 record accessor `id()` 调整为普通类 getter `getId()`。

## 7. 数据设计
- 不调整表结构。
- `roles` 表字段仍由 `V2__stage_1_auth_jwt_rbac.sql` 定义。
- 当前字段集合：
  - `id BIGINT AUTO_INCREMENT PRIMARY KEY`
  - `code VARCHAR(32) NOT NULL`
  - `name VARCHAR(64) NOT NULL`
  - `description VARCHAR(255)`
  - `created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`
- 唯一约束仍是 `uk_roles_code`，保证一个角色编码只对应一条角色记录。
- 查询角色时继续显式选择完整字段集合，避免实体字段与表字段漂移。

## 8. 关键流程
- 正常注册流程中涉及角色的部分：
  1. 用户插入成功后，服务层调用 `RoleMapper.findByCode("USER")` 查询默认角色。
  2. MyBatis 通过属性映射填充 `RoleEntity` 普通类。
  3. 服务层读取 `role.getId()`。
  4. 服务层调用 `RoleMapper.addRoleToUser(userId, roleId)` 写入 `user_roles`。
- 查询用户角色编码流程不依赖 `RoleEntity`，保持不变。
- 异常流程保持不变：
  - 默认 USER 角色缺失时，注册流程继续抛出 `IllegalStateException` 并触发事务回滚。

## 9. 并发 / 幂等 / 缓存
- 本次不新增缓存。
- 本次不新增幂等键。
- 角色表只读取，不改变并发写入模型。
- 注册并发控制仍依赖 `users` 唯一约束和事务内写入 `user_roles`。
- `RoleEntity` 改为可变普通类后，只作为 MyBatis 查询结果在单次请求线程内读取，不作为跨线程共享状态。

## 10. 权限与安全
- 不修改接口访问规则。
- 不修改角色编码到 `GrantedAuthority` 的转换逻辑。
- `RoleEntity` 不直接作为接口响应返回；角色信息对外仍通过 `UserInfo.roles` 暴露角色编码列表。
- 普通类不使用 Lombok `@Data`，避免无意生成过宽的 `toString()`、`equals()`、`hashCode()` 语义。

## 11. 测试策略
- 编译验证：确认 `RoleEntity` 从 record 改为普通类后没有残留 record accessor 调用。
- 集成测试：运行 Auth 集成测试，覆盖注册默认角色绑定、登录、当前用户角色返回和管理员 RBAC。
- 异常场景验证：
  - 默认 USER 角色查询仍能返回主键。
  - 普通用户访问管理员接口仍被拒绝。
- API 手工验证：本次不改变 API，如集成测试通过，可不额外进行手工验证。

## 12. 风险与替代方案
- 当前方案风险：
  - 需要同步调整 MyBatis resultMap 和服务层 accessor，否则会出现运行期映射失败或编译失败。
  - 普通类可变性比 record 更高，需要继续约束其只作为持久化对象使用。
- 备选方案 A：保留 record，并只补充注释说明字段对应关系。
  - 没有采用，因为不满足“重构为普通类”的明确需求。
- 备选方案 B：删除显式 resultMap，依赖 `map-underscore-to-camel-case` 自动映射。
  - 没有采用，因为当前学习型项目更强调表字段与实体字段的可审查对应关系，显式 resultMap 更直观。
- 备选方案 C：引入 `@Data` 快速生成所有方法。
  - 没有采用，因为角色实体当前只需要 getter、setter 和无参构造器；使用 `@Data` 会额外生成不必要的方法语义。
