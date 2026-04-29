# MyBatis 持久化规范化改造设计

## 1. 背景
- 当前阶段 1 已完成用户注册、登录、JWT 与 RBAC 闭环，`auth` 模块已经具备 `mapper` 包，但实际实现仍基于 `JdbcTemplate`。
- `JdbcTemplate` 适合基础阶段学习 SQL，但随着后续活动、场次、票种、订单、库存、支付等表逐步增加，手写 `RowMapper`、主键回填和 SQL 组织会越来越分散。
- 企业项目中更常见的做法是使用 MyBatis 统一管理 SQL 映射、参数绑定、结果映射和 Mapper 接口，让服务层只依赖稳定的数据访问契约。

## 2. 目标
- 引入 MyBatis Spring Boot Starter，并在当前 Spring Boot 3.3.2 / Java 17 技术栈下选择兼容版本。
- 将现有 `auth` 模块的 `UserMapper`、`RoleMapper` 从 `JdbcTemplate` 类改造为 MyBatis Mapper 接口。
- 将 SQL 下沉到 `resources/mapper/auth/*.xml`，让 SQL、结果映射和 Java 接口形成清晰对应关系。
- 保持现有 API、业务流程、数据库表结构和测试断言不变。
- 成功标准：
  - 注册、登录、用户状态更新、角色查询仍按原有行为工作。
  - Mapper 层不再直接依赖 `JdbcTemplate`、`RowMapper`、`GeneratedKeyHolder` 等底层 JDBC 细节。
  - `AuthIntegrationTest` 通过，证明 MyBatis 映射能支撑完整认证授权链路。

## 3. 非目标
- 不引入 MyBatis-Plus，避免在当前学习阶段隐藏 SQL 和表结构细节。
- 不迁移到 JPA / Hibernate，避免引入实体生命周期、懒加载、脏检查等额外概念。
- 不调整 `users`、`roles`、`user_roles` 表结构与 Flyway 历史迁移。
- 不扩展新的业务 API，不改变 JWT、RBAC、用户禁用等认证授权规则。
- 不提前为所有未来模块生成通用 BaseMapper，后续按业务模块逐步增加 Mapper。

## 4. 影响范围
- 构建依赖：
  - `backend/pom.xml` 新增 `mybatis-spring-boot-starter` 版本属性与依赖。
  - 原直接使用 `spring-boot-starter-jdbc` 的数据访问入口切换为 MyBatis starter。
- 配置：
  - `application.yml` 新增 `mybatis.mapper-locations` 与 `map-underscore-to-camel-case`。
- Java 包结构：
  - `modules/auth/mapper/UserMapper`
  - `modules/auth/mapper/RoleMapper`
  - `modules/auth/mapper/param/UserCreateParam`
  - `infra/persistence/MyBatisConfig`
- Mapper XML：
  - `resources/mapper/auth/UserMapper.xml`
  - `resources/mapper/auth/RoleMapper.xml`
- 数据库：
  - 不新增表，不修改索引，不修改种子数据。
- 外部接口：
  - 不变。

## 5. 领域建模
- `UserEntity`
  - 继续表示 `users` 表的一行持久化数据。
  - 仍是 record，只表达数据快照，不承担密码校验和 token 签发。
- `RoleEntity`
  - 继续表示 `roles` 表的一行持久化数据。
  - 权限判断仍依赖稳定角色编码 `USER`、`ADMIN`。
- `UserCreateParam`
  - 新增 Mapper 参数对象，用于注册插入场景。
  - 负责承载 `username`、`email`、`passwordHash`、默认 `status`，并接收数据库生成的 `id`。
  - 该对象属于持久化入参，不是领域实体，也不暴露给 Controller。

关键状态：
- 用户状态仍只有 `ENABLED` / `DISABLED`。
- 本次不改变状态流转，只改变状态数据的读取与写入方式。

## 6. API 设计
- 本次无新增或变更 API。
- 受影响但契约不变的接口：
  - `POST /api/v1/auth/register`
  - `POST /api/v1/auth/login`
  - `GET /api/v1/me`
  - `GET /api/v1/admin/users`
  - `PATCH /api/v1/admin/users/{userId}/status`
- 请求参数、响应结构、错误码保持阶段 1 既有设计：
  - 认证失败：`AUTH-401`
  - 授权失败：`AUTH-403`
  - 用户名 / 邮箱重复：`AUTH-409`
  - 参数错误：`COMMON-400`

## 7. 数据设计
- 表结构调整：无。
- 索引设计：沿用阶段 1 既有设计。
  - `uk_users_username`：支撑用户名唯一与登录查询。
  - `uk_users_email`：支撑邮箱唯一与登录查询。
  - `uk_roles_code`：保证角色编码稳定唯一。
  - `uk_user_roles_user_role`：防止重复绑定角色。
  - `idx_user_roles_role_id`：保留后续按角色反查用户能力。
- 数据一致性考虑：
  - 注册仍在事务中完成用户插入与默认角色绑定。
  - 并发注册时，MyBatis 只替换 SQL 执行方式；唯一约束兜底策略不变。
  - `UserCreateParam.id` 由数据库生成主键回填，避免注册后再通过用户名二次查询主键。

## 8. 关键流程
- 正常注册流程：
  1. Controller 校验请求体。
  2. Service 规范化用户名和邮箱。
  3. `UserMapper.existsByUsername` / `existsByEmail` 做友好预检查。
  4. Service 创建 `UserCreateParam`，调用 `UserMapper.insert`。
  5. MyBatis 执行 `INSERT` 并回填 `id`。
  6. Service 查询 `USER` 角色并写入 `user_roles`。
  7. Service 查询用户摘要并返回。
- 登录流程：
  1. Service 调用 `UserMapper.findByUsernameOrEmail`。
  2. MyBatis 将 `users` 行映射为 `UserEntity`。
  3. Service 校验密码和状态后签发 JWT。
- 管理员更新用户状态流程：
  1. Service 调用 `UserMapper.updateStatus`。
  2. 受影响行数为 0 时返回用户不存在。
  3. 更新成功后重新读取用户摘要。

异常流程：
- Mapper 查不到单条记录时返回 `Optional.empty()`，由 Service 转换为业务异常或认证异常。
- 数据库唯一约束冲突仍由 Spring 事务边界转换为 `DuplicateKeyException`，Service 保持原有业务错误转换。

## 9. 并发 / 幂等 / 缓存
- 超卖风险：
  - 本次不涉及票库存，不新增超卖风险。
- 防重复提交：
  - 注册接口仍依赖用户名和邮箱唯一约束防并发重复账号。
  - 用户状态更新是同值幂等的 `PATCH`，MyBatis 改造不改变语义。
- 缓存：
  - 本次不新增缓存。
  - 角色查询仍每次查库，避免权限变更与缓存失效问题提前复杂化。

## 10. 权限与安全
- 本次不改变 Spring Security 规则。
- 公开接口、登录用户接口、管理员接口的访问边界保持阶段 1 既有配置。
- MyBatis XML 使用参数绑定，不拼接用户输入，避免 SQL 注入风险。
- JWT 密钥、密码哈希、用户禁用即时校验策略不变。

## 11. 测试策略
- 单元测试：
  - 现有 `ApiResponseTest`、`BusinessExceptionTest` 不受影响。
- 集成测试：
  - 重点执行 `mvn -q -Dtest=AuthIntegrationTest test`，覆盖注册、登录、角色绑定、管理员权限、禁用用户、token 异常等完整链路。
  - 执行 `mvn -q test`，确认 MyBatis 自动配置不会破坏系统模块、异常处理和 OpenAPI 相关测试。
- 接口验证：
  - 本次主要依赖 MockMvc 集成测试；如后续启动本地服务，可按 README 的注册登录流程做手工验证。
- 异常场景验证：
  - 重复用户名、重复邮箱、错误密码、禁用用户、普通用户访问管理员接口仍由现有集成测试覆盖。

## 12. 风险与替代方案
- 当前方案风险：
  - MyBatis XML 的 namespace、statement id 与 Java 接口方法必须严格匹配，否则启动或调用时失败。
  - record 构造器映射需要明确 `resultMap`，否则下划线字段、枚举、时间字段的映射可读性不足。
  - 新增依赖版本必须与 Spring Boot 3.3.2 兼容。
- 备选方案：
  - 继续使用 `JdbcTemplate`：最轻量，但后续 SQL 与映射代码会逐渐分散。
  - 使用 MyBatis 注解 SQL：文件少，但复杂 SQL 可读性和维护性不如 XML。
  - 使用 MyBatis-Plus：CRUD 快，但会弱化当前学习阶段对 SQL、索引和状态流转的理解。
  - 使用 JPA：抽象更高，但当前票务平台后续会有库存扣减、幂等写入、复杂查询，直接 SQL 可控性更重要。
- 为什么不选备选方案：
  - 当前项目目标是贴近企业真实项目，同时保持学习可解释性；MyBatis XML 能兼顾 SQL 可见、映射集中、服务层简洁与后续复杂查询演进。
