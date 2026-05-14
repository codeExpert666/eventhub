# RoleEntity 持久化模型重构实现说明

## 1. 本次改动解决了什么问题

- 将 Auth 模块中的 `RoleEntity` 从 Java record 改为普通 Java 类。
- 明确 `RoleEntity` 字段与 `roles` 表字段一一对应，降低后续表字段和实体字段漂移的风险。
- 将 MyBatis 对 `RoleEntity` 的映射从构造器映射调整为属性映射，使其与当前 `UserEntity` 的持久化模型风格保持一致。

## 2. 改动内容
- 新增了什么
  - 新增设计文档：`docs/ai/design/2026-05-14-role-entity-persistence-model-design.md`。
  - 新增实现说明：本文档。
- 修改了什么
  - `RoleEntity` 改为带 getter、setter、无参构造器的普通类。
  - `RoleEntity` 保留 `id`、`code`、`name`、`description`、`createdAt` 五个字段，对应 `roles` 表五列。
  - `RoleMapper.xml` 的 `RoleEntityResultMap` 从 `<constructor>` 映射改为 `<id>` / `<result>` 属性映射。
  - `RoleMapper.xml` 新增 `RoleColumns` SQL 片段，集中维护 `roles` 查询字段集合。
  - `AuthService` 中读取默认 USER 角色主键的方式从 `userRole.id()` 改为 `userRole.getId()`。
- 删除了什么
  - 删除了 `RoleEntity` record 定义和对应 constructor resultMap。

## 3. 为什么这样设计
- 关键设计原因
  - 用户明确要求将 record 重构为普通类，因此实体不再使用 record 构造器作为 MyBatis 映射入口。
  - 显式属性 resultMap 能清楚表达 `roles.id/code/name/description/created_at` 到 Java 字段的逐列对应关系。
  - 与 `UserEntity` 保持相同的“普通类 + 显式 resultMap”风格，后续审查 Auth 持久化模型时更一致。
  - 不使用 Lombok `@Data`，只使用 `@Getter`、`@Setter`、`@NoArgsConstructor`，避免额外生成不需要的 `toString()`、`equals()`、`hashCode()` 语义。
- 与项目当前阶段的匹配点
  - 改动限定在 Auth 持久化边界内，不影响 API、JWT、RBAC 或数据库结构。
  - 当前角色模型仍是最小闭环：稳定角色编码用于权限判断，不提前扩展权限点、菜单或角色状态。

## 4. 替代方案
- 方案 A：保留 record，只补充字段对应注释
  - 优点：实体不可变，代码量少。
  - 未采用原因：不满足“重构为普通类”的明确需求。
- 方案 B：删除显式 resultMap，依赖 MyBatis 下划线转驼峰自动映射
  - 优点：XML 更短。
  - 未采用原因：当前项目强调学习和可复盘，显式映射更方便检查数据库字段和实体字段是否完整对应。
- 方案 C：使用 Lombok `@Data`
  - 优点：注解更少。
  - 未采用原因：`RoleEntity` 只需要 JavaBean 访问器；`@Data` 会生成当前不需要的对象相等和字符串输出语义。

## 5. 测试与验证
- 跑了哪些测试
  - `mvn test -Dtest=AuthIntegrationTest`
  - `mvn test`
  - `mvn clean test`
- 手工验证了哪些场景
  - 本次未额外做 Swagger 或浏览器手工验证；HTTP 契约没有变化，相关行为由 Auth 集成测试覆盖。
- 结果如何
  - 测试通过。
  - `AuthIntegrationTest` 共 15 个用例，Failures: 0，Errors: 0，Skipped: 0。
  - 全量测试共 33 个用例，Failures: 0，Errors: 0，Skipped: 0。
  - `mvn clean test` 已确认源码能从干净 `target` 目录重新编译并通过测试。
  - 覆盖注册默认角色绑定、登录、当前用户、管理员列表、普通用户访问管理员接口被拒绝、禁用用户旧 token 等关键 Auth/RBAC 场景。

## 6. 已知限制
- `RoleEntity` 现在是可变对象，需要继续约束其只作为持久化对象在 Mapper 和 Service 内部流转。
- `roles` 表当前只支持内置角色编码，尚未建模动态权限点、角色状态或后台角色管理流程。
- 当前没有单独的 `RoleMapper` 单元测试；角色读取能力通过 Auth 集成测试间接覆盖。

## 7. 对后续版本的影响
- 对简历可用版的价值
  - Auth 模块实体风格更统一，`users` / `roles` 两个核心账号权限表都有清晰的 Java 持久化模型。
  - 面试解释 MyBatis resultMap 时，可以直接展示数据库列到实体字段的逐列映射。
- 对微服务 / 云原生演进的影响
  - 本次不改变服务边界，对后续拆分 Auth 服务没有负面影响。
  - 后续如果角色权限模型扩展为独立权限服务，可以在服务边界处再引入更完整的领域模型和查询模型。
