# MyBatis 持久化规范化改造实现说明

## 1. 本次改动解决了什么问题

本次把现有 `auth` 模块的数据访问层从 `JdbcTemplate` 实现迁移为 MyBatis Mapper 规范：

- 解决 `mapper` 包名与实际实现不一致的问题。
- 去掉服务附近的 JDBC 细节，例如 `RowMapper`、`GeneratedKeyHolder`、`JdbcTemplate`。
- 建立后续业务模块可复用的 MyBatis 组织方式：Mapper 接口、XML SQL、显式 `resultMap`、参数对象、统一扫描配置。
- 保持阶段 1 已有注册、登录、JWT、RBAC、管理员用户管理行为不变。

## 2. 改动内容
- 新增了什么
  - 新增 `mybatis-spring-boot.version` 与 `mybatis-spring-boot-starter` 依赖。
  - 新增 `infra/mybatis/MyBatisConfig`，集中声明 Mapper 扫描边界。
  - 新增 `resources/mapper/auth/UserMapper.xml` 与 `RoleMapper.xml`，集中维护账号、角色相关 SQL。
  - 新增 `UserCreateParam`，用于用户插入时承接数据库生成主键回填。
  - 新增 MyBatis 设计文档与 ADR。
- 修改了什么
  - `UserMapper` 从 `JdbcTemplate` 类改为 MyBatis `@Mapper` 接口。
  - `RoleMapper` 从 `JdbcTemplate` 类改为 MyBatis `@Mapper` 接口。
  - `AuthService.register` 改为通过 `UserCreateParam` 获取插入后的用户主键。
  - `application.yml` 新增 XML mapper 扫描路径和下划线转驼峰配置。
  - `backend/pom.xml` 将直接 JDBC 数据访问依赖替换为 MyBatis starter。
- 删除了什么
  - 删除主代码中直接依赖 `JdbcTemplate`、`RowMapper`、`GeneratedKeyHolder`、`KeyHolder` 的持久化实现代码。

## 3. 为什么这样设计
- 当前项目后续会进入活动、订单、库存、支付等更真实的业务模块，SQL 会变复杂；MyBatis XML 比注解 SQL 更适合承载复杂查询和写入语句。
- Mapper 接口让 Service 继续依赖清晰的数据访问契约，不把 SQL 执行细节扩散到业务流程中。
- `resultMap` 显式映射 record 构造器参数，能清楚表达数据库列、枚举、时间字段如何变成 Java 持久化对象。
- `UserCreateParam` 用来处理自增主键回填，避免为了拿 id 而让服务层使用 `Map` 或二次查询用户。
- 保留数据库唯一约束兜底并发注册冲突，说明本次只是替换持久化技术实现，不改变业务一致性边界。

## 4. 替代方案
- 方案 A：继续使用 `JdbcTemplate`。
  - 没有采用：短期简单，但后续表和查询增多后，SQL、参数绑定和结果映射会散落在 Java 类中。
- 方案 B：使用 MyBatis 注解 SQL。
  - 没有采用：简单查询文件更少，但复杂 SQL 放在注解里可读性较差，也不利于后续 Review。
- 方案 C：引入 MyBatis-Plus。
  - 没有采用：能快速生成 CRUD，但当前项目更重视学习 SQL、索引、状态流转和并发边界。
- 方案 D：迁移到 JPA / Hibernate。
  - 没有采用：抽象更高，但会引入实体生命周期、懒加载、脏检查等概念；当前票务平台更需要直接掌控 SQL。

## 5. 测试与验证
- 跑了哪些测试
  - `mvn -q -DskipTests compile`
  - `mvn -q -Dtest=AuthIntegrationTest test`
  - `mvn -q test`
  - `rg -n "JdbcTemplate|RowMapper|GeneratedKeyHolder|KeyHolder|EmptyResultDataAccessException" backend/src/main/java`
  - `git diff --check`
- 手工验证了哪些场景
  - 本次未额外启动本地 HTTP 服务，主要通过 MockMvc 集成测试验证接口链路。
- 结果如何
  - 编译通过。
  - 认证授权集成测试通过。
  - 后端全量测试通过。
  - 主代码中未检出直接 JDBC 数据访问残留。
  - Diff 空白检查通过。

## 6. 已知限制
- 当前只迁移了已有的 `auth` 模块；后续活动、订单、库存模块新增时，需要继续按同一 MyBatis 规范创建 Mapper 和 XML。
- 暂未引入 Mapper 层切片测试；当前通过完整 `AuthIntegrationTest` 覆盖 MyBatis、Flyway、Service、Controller、Security 链路。
- XML mapper 的 statement id、namespace 与接口方法需要保持一致，后续改名时要同步调整。
- 仍未引入分页插件，管理员用户列表保持阶段 1 的最小演示实现。

## 7. 对后续版本的影响
- 对简历可用版的价值
  - 项目持久化层更贴近常见企业 Java 后端结构，便于讲述从 SQL 到 Mapper 再到 Service 的分层边界。
  - 后续演示活动、订单、库存扣减时，可以围绕真实 SQL、索引和事务边界展开，而不是停留在内存或样例代码层面。
- 对微服务 / 云原生演进的影响
  - 单体内的 Mapper 规范可以直接迁移到未来拆分后的独立服务。
  - 后续如接入读写分离、SQL 性能分析、分库分表或链路追踪，MyBatis 边界比散落的 JDBC 代码更容易统一治理。

## 8. Review 复盘与修复记录

本次按业务正确性、边界条件、并发与幂等、权限、缓存一致性、数据库索引与唯一约束、测试覆盖七个维度复盘当前工作区改动。

- 业务正确性
  - 结论：未发现 MyBatis 迁移改变注册、登录、当前用户、管理员用户列表、用户状态更新的业务语义。
  - 依据：Mapper XML 与原 JDBC SQL 保持同等查询条件和排序；`AuthIntegrationTest` 覆盖完整认证授权链路。
- 边界条件
  - 结论：未发现新增边界缺陷。
  - 依据：注册继续在 DTO 层校验格式，在 Service 层规范化用户名和邮箱；单条查询通过 `Optional.empty()` 回到业务异常转换。
- 并发与幂等风险
  - 发现问题：顺序重复注册已有测试，但缺少并发重复注册的显式回归测试。
  - 问题等级：P2。
  - 修改建议：补充并发注册同一账号的集成测试，证明数据库唯一约束和异常转换能保证最终只有一个请求成功。
  - 修复结果：已新增 `AuthIntegrationTest.concurrentRegisterShouldCreateOnlyOneAccount`，使用两个并发 MockMvc 注册请求断言返回状态只能是一个 `200` 和一个 `409`。
  - 说明：用户状态更新仍是同值幂等的 `PATCH`，本次 MyBatis 改造没有改变该语义。
- 权限风险
  - 结论：未发现新增权限风险。
  - 依据：Spring Security 配置、JWT 解析、禁用用户即时查库校验均未改变；Mapper 使用参数绑定，不拼接用户输入。
- 缓存一致性风险
  - 结论：未发现新增缓存一致性风险。
  - 依据：认证授权链路当前没有引入用户或角色缓存，角色和用户状态仍实时查库。
- 数据库索引与唯一约束
  - 结论：当前约束仍合理，无需新增迁移。
  - 依据：`uk_users_username`、`uk_users_email` 支撑账号唯一和登录查询；`uk_roles_code` 保证角色编码唯一；`uk_user_roles_user_role` 防止重复绑定；`idx_user_roles_role_id` 保留后续按角色反查用户能力。
- 测试是否不足
  - 发现问题：并发注册场景原先只停留在实现注释和设计说明中，缺少自动化证明。
  - 问题等级：P2。
  - 修改建议：将并发重复注册固定为集成测试，避免未来调整 Mapper、事务或异常转换时回归。
  - 修复结果：已补测试，并重新执行 `mvn -q -Dtest=AuthIntegrationTest test`、`mvn -q test`、`git diff --check` 通过。
