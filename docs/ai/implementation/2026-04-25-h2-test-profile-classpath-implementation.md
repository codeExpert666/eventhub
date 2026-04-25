# H2 测试配置 classpath 修复实现说明

## 1. 本次改动解决了什么问题
- 修复 IDEA 在 `application-test.yml` 中提示无法解析 `org.h2.Driver` 的问题。
- 根因是 `application-test.yml` 原本位于 `src/main/resources`，而 H2 依赖是 `test` scope；主资源配置按主运行 classpath 解析，看不到测试依赖中的 H2 Driver。

## 2. 改动内容
- 新增了什么：
  - 在 `backend/src/test/resources/application-test.yml` 放置测试 profile 配置，并补充说明为什么该文件属于测试资源。
- 修改了什么：
  - 更新 `backend/src/main/resources/application.yml` 顶部注释，明确本地/生产配置位于主资源目录，测试配置位于测试资源目录。
- 删除了什么：
  - 删除 `backend/src/main/resources/application-test.yml`，避免测试专用配置继续被主运行 classpath 解析。

## 3. 为什么这样设计
- `test` profile 当前主要服务于 `@ActiveProfiles("test")` 的自动化测试，不是正式应用运行环境。
- `com.h2database:h2` 保持 `test` scope，可以避免 H2 被打进正式运行依赖。
- `src/test/resources` 会进入 Maven/IDEA 的测试 classpath，正好能看到 test-scope 依赖，因此 `org.h2.Driver` 的配置位置和依赖作用域保持一致。

## 4. 替代方案
- 方案 A：把 H2 依赖改成 runtime scope 或默认 compile scope。
  - 没有采用，因为这会让生产运行时也携带 H2，扩大正式包依赖面。
- 方案 B：删除 `driver-class-name`，依赖 Spring Boot 从 `jdbc:h2:` URL 自动推断驱动。
  - 没有采用，因为这只是隐藏了类名解析提示，没有修正测试配置和 test-scope 依赖之间的 classpath 边界问题。
- 方案 C：保留文件位置，并在 IDEA 中单独忽略该提示。
  - 没有采用，因为这属于 IDE 层绕过，无法沉淀为仓库级约束。

## 5. 测试与验证
- 已运行 `mvn -q -Dtest=SystemControllerTest test`，验证 Spring Boot 测试上下文能加载 `src/test/resources/application-test.yml`，并成功通过 H2 建连与 Flyway 迁移。
- 已运行 `git diff --check`，验证 YAML 和 Markdown 没有空白格式问题。
- 手工验证建议：在 IDEA 中刷新 Maven 后，查看 `driver-class-name: org.h2.Driver` 是否还提示无法解析。

## 6. 已知限制
- 本方案不支持把 `test` profile 当作正式应用 profile 直接用 `spring-boot:run` 启动；这与 H2 继续保持 test scope 是一致的。
- H2 的 MySQL 兼容模式不能完全等价于真实 MySQL，涉及复杂 SQL 或数据库方言差异时仍需要真实 MySQL 集成验证。

## 7. 对后续版本的影响
- 对简历可用版的价值：
  - 测试配置和依赖作用域更加清晰，便于讲清楚自动化测试环境如何隔离外部依赖。
- 对微服务 / 云原生演进的影响：
  - 保持生产镜像和运行包依赖面更干净，后续拆分服务时也更容易区分运行依赖与测试依赖。
