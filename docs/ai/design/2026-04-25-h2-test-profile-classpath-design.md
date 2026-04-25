# H2 测试配置 classpath 修复设计

## 1. 背景
- 当前 `backend/src/main/resources/application-test.yml` 中显式配置了 `spring.datasource.driver-class-name: org.h2.Driver`。
- `backend/pom.xml` 中的 `com.h2database:h2` 依赖使用 `test` scope，只会进入测试编译与测试运行 classpath。
- IDEA 在解析 `src/main/resources` 下的配置文件时，会按主运行 classpath 识别类名，因此提示无法解析 `org.h2.Driver`。

## 2. 目标
- 消除 IDEA 对 `org.h2.Driver` 的无法解析提示。
- 保持 H2 只作为测试依赖，不进入正式运行依赖与可执行 Jar。
- 保持 `@ActiveProfiles("test")` 测试上下文继续使用 H2 内存数据库与 Flyway 初始化链路。

## 3. 非目标
- 不修改业务接口、领域模型、数据库表结构或迁移脚本。
- 不把 `test` profile 设计成可通过 `spring-boot:run` 或生产 Jar 直接启动的运行环境。
- 不引入新的数据库、测试框架或构建插件。

## 4. 影响范围
- 配置文件：`application-test.yml` 从 `src/main/resources` 移到 `src/test/resources`。
- 公共配置说明：更新 `application.yml` 顶部注释，明确测试 profile 的资源位置。
- Maven 依赖：保持 `com.h2database:h2` 为 `test` scope。
- 表、缓存、外部接口：无影响。

## 5. 领域建模
- 本次是测试资源与依赖作用域修复，不涉及业务领域实体。
- 关键对象可以理解为：
  - 主运行 classpath：包含 `src/main/resources` 和非 test-scope 依赖。
  - 测试运行 classpath：包含 `src/main/resources`、`src/test/resources` 和 test-scope 依赖。
  - H2 Driver：只应该出现在测试运行 classpath 中。

## 6. API 设计
- 不新增或修改 HTTP API。
- 请求、响应、错误码、异常场景均不变。

## 7. 数据设计
- 不调整表结构、索引、唯一约束或初始化数据。
- 测试环境仍通过 Flyway 在 H2 内存库中执行 `db/migration` 下的迁移脚本。

## 8. 关键流程
- 正常流程：
  1. 测试类使用 `@ActiveProfiles("test")` 启动 Spring Boot 测试上下文。
  2. Spring 在测试 classpath 中加载 `src/test/resources/application-test.yml`。
  3. DataSource 使用 test-scope 中的 `org.h2.Driver` 创建 H2 内存库连接。
  4. Flyway 对 H2 测试库执行迁移脚本。
- 异常流程：
  - 如果错误地用正式运行方式启动 `test` profile，H2 仍不会进入主运行 classpath，这是预期约束。
- 状态流转：
  - 无业务状态流转。

## 9. 并发 / 幂等 / 缓存
- 不涉及库存扣减、重复提交、幂等或缓存一致性。
- H2 内存库仍只服务当前测试 JVM，测试之间的数据隔离继续由测试代码和 Spring 测试上下文负责。

## 10. 权限与安全
- 不涉及用户权限、鉴权或敏感接口。
- 保持测试专用数据库驱动不进入正式运行依赖，减少生产运行时暴露不必要组件。

## 11. 测试策略
- 运行 `mvn -q -Dtest=SystemControllerTest test`，验证 `@ActiveProfiles("test")` 可以加载移动后的配置。
- 检查 `git diff --check`，避免 YAML 和文档出现格式问题。
- 手工在 IDEA 中重新导入 Maven 或刷新项目后，确认 `org.h2.Driver` 可由 test-scope H2 依赖解析。

## 12. 风险与替代方案
- 当前方案风险：
  - 如果有人依赖 `spring-boot:run -Dspring-boot.run.profiles=test` 启动应用，该方式仍然不可用，因为 H2 不在主运行 classpath。
- 备选方案 A：把 H2 依赖改为 runtime scope 或移除 scope。
  - 未采用原因：会把测试数据库驱动带入正式运行依赖，与当前“测试环境自包含但生产依赖保持干净”的目标不一致。
- 备选方案 B：删除 `driver-class-name`，让 Spring Boot 根据 JDBC URL 自动推断驱动。
  - 未采用原因：可以绕过 IDE 提示，但没有解决测试配置位于主资源目录、依赖却是 test scope 的边界错位问题。
