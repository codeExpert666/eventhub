# 用户角色绑定影响行数校验设计

## 1. 背景
- 注册普通用户时，系统会在同一个事务中创建 `users` 记录并写入 `user_roles` 默认角色关系。
- 当前 `RoleMapper.addRoleToUser` 返回 `void`，调用方只能依赖数据库异常判断失败。
- 对学习型后端项目而言，默认角色绑定是注册闭环的关键步骤，应显式表达“期望成功插入 1 行”的持久化契约。

## 2. 目标
- 将 `RoleMapper.addRoleToUser(Long userId, Long roleId)` 从 `void` 调整为返回 `int` 受影响行数。
- 在 `AuthService.register` 中校验默认角色绑定必须影响 1 行。
- 当影响行数不是 1 时，抛出 `IllegalStateException`，触发事务回滚，避免出现“用户已创建但没有默认角色”的半完成状态。
- 成功标准：注册流程正常绑定默认 USER 角色；异常写入结果能被服务层明确识别。

## 3. 非目标
- 不修改 `users`、`roles`、`user_roles` 表结构。
- 不新增或修改 HTTP API。
- 不调整 RBAC 角色语义、默认角色编码或 Spring Security 配置。
- 不引入新的异常类型、依赖或统一结果封装。
- 不处理重复绑定的幂等化语义；当前注册流程只在新建用户后绑定一次默认角色。

## 4. 影响范围
- Auth Mapper 接口：`backend/src/main/java/com/eventhub/modules/auth/mapper/RoleMapper.java`。
- Auth 应用服务：`backend/src/main/java/com/eventhub/modules/auth/service/AuthService.java`。
- MyBatis XML：`backend/src/main/resources/mapper/auth/RoleMapper.xml` 的 SQL 不需要调整，`<insert>` 天然支持返回影响行数。
- 数据表：涉及 `user_roles` 写入，但不改变结构和索引。
- 不涉及缓存、消息队列、外部接口。

## 5. 领域建模
- 核心实体：
  - `UserEntity`：注册流程中新创建的用户。
  - `RoleEntity`：默认 `USER` 角色的持久化对象。
  - `user_roles`：用户与角色的关系表。
- 关键关系：
  - 一个用户可以拥有多个角色。
  - 注册成功的普通用户必须至少拥有默认 `USER` 角色。
- 关键状态：
  - 本次不新增业务状态。
  - 注册事务成功提交后，用户与默认角色关系应同时存在。

## 6. API 设计
- 不新增或修改 HTTP API。
- 内部 Mapper 契约调整：
  - 调整前：`void addRoleToUser(Long userId, Long roleId)`。
  - 调整后：`int addRoleToUser(Long userId, Long roleId)`。
- 服务层异常场景：
  - `findByCode("USER")` 返回空：继续抛出 `IllegalStateException("Default USER role is missing")`。
  - `addRoleToUser(...)` 影响行数不是 1：抛出 `IllegalStateException("Failed to bind default USER role")`。

## 7. 数据设计
- 不调整表结构。
- 不调整索引或唯一约束。
- 数据一致性考虑：
  - 注册方法继续使用 `@Transactional`。
  - 用户插入、默认角色查询、用户角色关系插入处于同一事务。
  - 默认角色绑定失败时通过运行时异常触发事务回滚。

## 8. 关键流程
- 正常流程：
  1. 校验用户名和邮箱是否已存在。
  2. 插入 `users`，并校验影响行数为 1 且主键已回填。
  3. 查询默认 `USER` 角色。
  4. 插入 `user_roles` 用户角色关系。
  5. 校验 `user_roles` 插入影响行数为 1。
  6. 查询并返回注册后的用户摘要。
- 异常流程：
  - 用户插入异常：沿用现有异常处理。
  - 默认角色缺失：抛出系统不变量异常并回滚。
  - 默认角色绑定影响行数不是 1：抛出系统不变量异常并回滚。
- 状态流转：
  - 本次没有新增订单、支付或用户状态流转。

## 9. 并发 / 幂等 / 缓存
- 并发：
  - 注册并发冲突仍主要由 `users.username`、`users.email` 唯一约束兜底。
  - 默认角色绑定发生在新用户主键生成之后，通常不会出现同一用户重复绑定。
- 幂等：
  - 本次不把注册接口改造成幂等接口。
  - 如果未来支持注册请求幂等键，再重新设计重复提交语义。
- 缓存：
  - 本次不引入角色缓存。

## 10. 权限与安全
- 不修改接口权限规则。
- 不修改 JWT 生成、认证主体加载或角色到权限的转换逻辑。
- 该校验提升了注册后权限数据完整性，降低用户缺少默认角色导致授权行为异常的风险。

## 11. 测试策略
- 编译验证：
  - 确认 Mapper 方法签名调整后所有调用点均已同步。
- 集成测试：
  - 优先运行现有 Auth 相关测试；如果当前仓库没有对应测试类，则运行后端测试或编译。
- 异常场景验证：
  - 通过代码审查确认 `affectedRows != 1` 会抛出运行时异常并触发事务回滚。
  - 数据库约束异常仍由 Spring/MyBatis 异常机制抛出，不被静默吞掉。

## 12. 风险与替代方案
- 当前方案风险：
  - 如果未来 `addRoleToUser` 改成 `INSERT IGNORE` 或 upsert，影响行数可能出现 0，需要重新定义幂等成功语义。
  - 当前没有单独的 Mapper 单元测试直接模拟影响行数为 0 的分支。
- 备选方案 A：继续使用 `void`，只依赖数据库异常。
  - 未采用原因：默认角色绑定是注册闭环关键动作，返回影响行数能让服务层契约更明确。
- 备选方案 B：返回 `boolean` 表示是否成功。
  - 未采用原因：`boolean` 会丢失影响 0 行、1 行、多行的细节，不如 `int` 贴近 MyBatis 和 SQL 写操作语义。
- 备选方案 C：新增专门的领域方法封装角色绑定。
  - 未采用原因：当前只有一个调用点，直接在应用服务中校验影响行数更小、更清晰。
