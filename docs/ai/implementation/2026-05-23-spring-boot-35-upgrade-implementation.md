# Spring Boot 3.5.14 升级实现说明

## 1. 本次改动解决了什么问题

本次改动将后端基础框架从 Spring Boot `3.3.2` 升级到 `3.5.14`，解决项目停留在已结束开源支持分支的问题。

同时，本次处理了升级过程中最直接的兼容依赖问题：

- 旧版 `springdoc-openapi 2.6.0` 与 Spring Boot 3.5 所使用的 Spring Framework 6.2 组合存在运行时兼容风险，尤其会影响 `/v3/api-docs`。
- Flyway 从 10.x 升级到 11.x，保持数据库迁移引擎与新的 Spring Boot 3.5 依赖组合更一致。

## 2. 改动内容
- 新增了什么
  - 新增 Spring Boot 3.5.14 升级设计文档。
  - 新增采用 Spring Boot 3.5.14 作为当前后端基线的 ADR。
  - 新增本实现说明文档。
- 修改了什么
  - `backend/pom.xml` 中 Spring Boot parent 版本从 `3.3.2` 调整为 `3.5.14`。
  - `springdoc.version` 从 `2.6.0` 调整为 `2.8.17`。
  - `flyway.version` 从 `10.10.0` 调整为 `11.7.2`。
  - 更新 MyBatis starter 版本说明注释，使其匹配当前 Spring Boot 3.5.14 / Java 17 基线。
- 删除了什么
  - 未删除代码、配置或测试。

## 3. 为什么这样设计
- 本次只处理 Spring Boot 3.x 内部演进，保持 Java 17 不变，便于把框架升级风险和 JDK 升级风险拆开验证。
- Spring Boot 父工程负责统一管理 Spring 生态依赖，因此升级 parent 是当前项目最清晰的基线调整入口。
- `springdoc` 显式升级是必要动作，因为它直接影响 OpenAPI 文档端点，且现有测试已经覆盖 `/v3/api-docs`。
- Flyway core 与 MySQL 扩展继续使用同一个版本属性，保持迁移引擎内部模块版本一致。
- MyBatis Spring Boot Starter 仍停留在 3.0.x，因为该分支面向 Spring Boot 3.x；4.x 更适合后续 Spring Boot 4 迁移阶段再处理。

## 4. 替代方案
- 方案 A：只升级 Spring Boot parent，不升级 springdoc 和 Flyway。
- 方案 B：直接升级到 Spring Boot 4.0.6。
- 方案 C：本次同时升级 Java 21。

为什么没有采用：

- 不采用方案 A：旧 `springdoc 2.6.0` 在 Spring Framework 6.2 下会影响 OpenAPI 生成，无法满足现有 `/v3/api-docs` 回归要求。
- 不采用方案 B：Spring Boot 4.x 的变更面更大，会涉及测试注解、依赖生态和后续框架基线迁移，不适合作为本次第一步。
- 不采用方案 C：Java 21 升级会同时影响本地 JDK、Docker 镜像、CI/CD 和 Maven 构建约束，拆成下一步更容易定位问题。

## 5. 测试与验证
- 执行 `mvn -q test`：
  - 结果：通过。
  - 共 50 个测试通过，0 failures，0 errors，0 skipped。
  - 覆盖 Auth 集成测试、系统接口测试、全局异常处理、统一响应体和分页对象测试。
  - 测试日志确认应用以 Spring Boot `v3.5.14` 启动，并使用 Java `17.0.19`。
  - `SystemControllerTest` 覆盖 `/v3/api-docs`，确认 OpenAPI 文档生成链路可用。
- 执行 `mvn -q -DskipTests package`：
  - 结果：通过。
  - 确认可执行 Jar 的 Spring Boot repackage 链路正常。

## 6. 已知限制
- 当前仍保持 Java 17，尚未引入 Java 21 LTS 的运行时和语言层收益。
- 本次没有关闭生产环境 Swagger UI / OpenAPI 文档端点；升级后 springdoc 会在测试启动日志中提示生产环境可按需关闭。
- 现有测试能覆盖核心启动、鉴权和 OpenAPI 链路，但尚不能完全覆盖 Spring Security、Jackson、Tomcat 等托管依赖升级后的所有边缘行为。
- 本次没有执行 Docker 镜像构建；Docker 基础镜像仍是 Java 17，与当前 `java.version=17` 保持一致。

## 7. 对后续版本的影响
- 对简历可用版的价值：
  - 项目基础框架回到受支持的 Spring Boot 3.x 基线，降低安全和维护风险。
  - 依赖升级过程有设计文档、ADR 和测试记录，便于面试时解释演进路径。
- 对微服务 / 云原生演进的影响：
  - 后续接入观测、容器化部署和配置管理时，可以基于更接近当前维护线的 Spring Boot 3.5 生态推进。
  - Java 21 和 Spring Boot 4.0.6 可以作为独立演进任务继续评估，减少一次性迁移的排障复杂度。
