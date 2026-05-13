# UserEntity 持久化模型收敛实现说明

## 1. 本次改动解决了什么问题

- 解决 Auth 模块中 `UserEntity` 与 `UserCreateParam` 字段重复的问题。
- 将 `UserEntity` 从 record 改为普通 Java 类，使其既能承接查询结果，也能作为 MyBatis 插入参数接收自增主键回填。
- 明确 `UserEntity` 字段与 `users` 表字段一一对应，避免后续新增字段时读取模型和写入参数对象出现漂移。

## 2. 改动内容
- 新增了什么
  - 新增设计文档：`docs/ai/design/2026-05-14-user-entity-persistence-model-design.md`。
  - 新增实现说明：本文档。
- 修改了什么
  - `UserEntity` 改为带 getter/setter/no-args constructor 的普通类。
  - `UserEntity` 保留 `id`、`username`、`email`、`passwordHash`、`status`、`createdAt`、`updatedAt` 七个字段，对应 `users` 表七列。
  - `UserEntity.enabledUser(...)` 用于构造注册插入所需的启用用户对象，主键和时间字段继续交给数据库生成。
  - `UserMapper.insert(...)` 参数从 `UserCreateParam` 改为 `UserEntity`。
  - `UserMapper.xml` 的 `UserEntityResultMap` 从 record 构造器映射改为普通类属性映射。
  - `AuthService` 和 `AuthenticatedSubjectService` 从 record accessor 调用切换为 getter 调用。
- 删除了什么
  - 删除 `backend/src/main/java/com/eventhub/modules/auth/mapper/param/UserCreateParam.java`。

## 3. 为什么这样设计
- 关键设计原因
  - 当前阶段的需求是“只保留 `UserEntity`”，因此注册写入路径不再保留额外参数对象。
  - `UserEntity` 改为普通类后，MyBatis 可以通过 setter 回填 generated keys，避免为拿到 `id` 再引入二次查询。
  - 显式 `resultMap` 继续保留，便于检查 `users` 表字段与 `UserEntity` 字段是否同步。
  - 不使用 Lombok `@Data`，避免给包含 `passwordHash` 的实体生成 `toString()`，降低敏感字段被日志误输出的风险。
- 与项目当前阶段的匹配点
  - 改动保持在 Auth 持久化边界内，没有扩大到 API、权限、JWT 或数据库结构。
  - 保持“最小可用闭环”，只消除当前真实冗余，不提前抽象读写模型分层。
  - 对学习项目来说，实体字段和 XML 映射的逐列对应更直观，也更适合后续 code review。

## 4. 替代方案
- 方案 A：继续保留 `UserCreateParam`
  - 优点：读写模型分离，插入参数对象语义更窄。
  - 未采用原因：与本次“只保留 `UserEntity`”目标冲突，并且当前字段重复收益不高。
- 方案 B：保留 record 版本 `UserEntity`，插入后通过用户名或邮箱二次查询主键
  - 优点：实体不可变。
  - 未采用原因：增加一次数据库查询，并且不能满足“改为普通类”的要求。
- 方案 C：引入 MyBatis Generator 或新 ORM 生成实体
  - 优点：可批量生成表实体和映射代码。
  - 未采用原因：本次只是 Auth 模块小范围模型收敛，引入生成器会明显扩大复杂度。

## 5. 测试与验证
- 跑了哪些测试
  - `mvn test -Dtest=AuthIntegrationTest`
- 手工验证了哪些场景
  - 本次未额外做浏览器或 Swagger 手工验证；相关 API 行为由 Auth 集成测试覆盖。
- 结果如何
  - 测试通过。
  - `AuthIntegrationTest` 共 15 个用例，Failures: 0，Errors: 0，Skipped: 0。
  - 覆盖注册成功、重复用户名、重复邮箱、并发注册、登录、禁用用户登录、无 token、登出鉴权、普通用户访问管理员接口、过期 token、篡改 token、禁用用户旧 token、当前用户接口和管理员用户列表。

## 6. 已知限制
- `UserEntity` 现在是可变对象，需要继续约束其只在持久化层和服务层内部流转，不直接作为接口响应返回。
- `UserEntity.enabledUser(...)` 仍带有“默认启用用户”的轻量工厂语义，后续如果用户创建流程支持审核、邮箱验证或邀请注册，可能需要重新评估默认状态的来源。
- 旧历史设计文档中仍会提到 `UserCreateParam`，它们反映当时的实现背景；当前实现以本次文档和代码为准。

## 7. 对后续版本的影响
- 对简历可用版的价值
  - Auth 模块持久化模型更清晰，减少重复对象，代码阅读成本更低。
  - `users` 表与 `UserEntity` 的字段对应关系更直接，便于面试时解释 MyBatis 映射和主键回填机制。
- 对微服务 / 云原生演进的影响
  - 本次不改变服务边界和外部契约，对后续拆分 Auth 服务没有负面影响。
  - 后续如果引入独立用户中心，可以在服务边界处再拆分 API DTO、领域模型和持久化模型，而不是在当前阶段过早复杂化。
