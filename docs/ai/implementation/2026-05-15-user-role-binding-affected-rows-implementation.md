# 用户角色绑定影响行数校验实现说明

## 1. 本次改动解决了什么问题

- 解决 `RoleMapper.addRoleToUser` 返回 `void` 时，服务层无法显式校验默认角色关系是否真的写入的问题。
- 让注册流程中的关键写操作与 `userMapper.insert(user)` 保持一致：都通过受影响行数校验系统不变量。
- 降低“用户创建成功但默认 USER 角色关系没有写入”这种半完成注册状态的风险。

## 2. 改动内容
- 新增了什么
  - 新增设计文档：`docs/ai/design/2026-05-15-user-role-binding-affected-rows-design.md`。
  - 新增实现说明：本文档。
- 修改了什么
  - `RoleMapper.addRoleToUser` 返回值从 `void` 调整为 `int`。
  - `AuthService.register` 在调用 `addRoleToUser` 后校验 `roleBindingRows == 1`。
  - 当默认角色绑定影响行数不是 1 时，抛出 `IllegalStateException("Failed to bind default USER role")`。
  - 补充了 Mapper 返回值选择规则和默认角色绑定校验的代码注释。
- 删除了什么
  - 没有删除业务代码或数据库对象。

## 3. 为什么这样设计
- 关键设计原因
  - MyBatis 的 `<insert>` 可以自然返回受影响行数，Mapper 方法改为 `int` 不需要修改 SQL。
  - 默认 USER 角色绑定是注册闭环中的关键写操作，服务层应明确校验“必须插入 1 条关系”。
  - 影响行数不是 1 时，说明系统不变量没有满足；使用运行时异常可以触发事务回滚，避免提交不完整用户。
- 与项目当前阶段的匹配点
  - 改动范围小，只收紧 Auth 注册流程的持久化契约。
  - 不提前引入复杂角色服务或幂等机制，仍保持单体学习项目的最小可用闭环。

## 4. 替代方案
- 方案 A：继续使用 `void`
  - 优点：代码最少，失败时仍可依赖数据库异常。
  - 未采用原因：无法表达“正常返回但影响行数异常”的场景，不利于学习和审查关键写操作契约。
- 方案 B：返回 `boolean`
  - 优点：调用方判断简单。
  - 未采用原因：`boolean` 抹掉了 SQL 影响行数细节；`int` 更符合 MyBatis 写操作习惯，也能支持后续判断 0、1、多行等情况。
- 方案 C：新增 `bindDefaultRole` 专用服务方法
  - 优点：可以封装默认角色查询和关系写入。
  - 未采用原因：当前只有一个调用点，直接在 `AuthService.register` 中校验更小、更容易理解。

## 5. 测试与验证
- 跑了哪些测试
  - `mvn test -Dtest=AuthIntegrationTest`
  - `mvn clean test -Dtest=AuthIntegrationTest`
  - `mvn test`
- 手工验证了哪些场景
  - 本次未额外做 HTTP 手工验证；注册默认角色绑定由 Auth 集成测试覆盖。
  - 通过代码审查确认 `roleBindingRows != 1` 会抛出运行时异常，并由 `@Transactional` 触发注册事务回滚。
- 结果如何
  - `AuthIntegrationTest`：15 个用例通过，Failures: 0，Errors: 0，Skipped: 0。
  - 后端全量测试：33 个用例通过，Failures: 0，Errors: 0，Skipped: 0。
  - `mvn clean test -Dtest=AuthIntegrationTest` 已确认源码能从干净 `target` 目录重新编译并通过测试。

## 6. 已知限制
- 当前没有单独模拟 `addRoleToUser` 返回 0 的单元测试；该分支主要通过代码审查确认。
- 如果未来把用户角色绑定改为 `INSERT IGNORE`、upsert 或幂等绑定，影响行数为 0 可能需要被视为幂等成功，到时应重新定义返回值语义。
- `DuplicateKeyException` 的捕获范围仍覆盖整个注册写入过程；当前注册新用户后绑定默认角色，不预期出现用户角色重复绑定。

## 7. 对后续版本的影响
- 对简历可用版的价值
  - 注册流程的关键写入校验更严谨，可以清楚解释“为什么写操作不一定用 void”。
  - Mapper 契约更贴近真实项目中对持久化结果的校验方式。
- 对微服务 / 云原生演进的影响
  - 后续拆分 Auth 服务或引入事件驱动注册流程时，默认角色绑定仍可以保留“写入结果必须可验证”的思想。
  - 如果未来改成异步角色初始化，需要重新设计事务边界和补偿机制。
