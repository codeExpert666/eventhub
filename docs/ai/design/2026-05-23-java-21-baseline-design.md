# Java 21 基线升级设计

## 1. 背景
- 当前项目已完成 Spring Boot `3.5.14` 升级，但 Java 编译与运行基线仍停留在 `17`。
- Java 21 是 LTS 版本，适合作为 Spring Boot 3.5 阶段的下一步运行时基线，也能为后续 Spring Boot 4.x 迁移提前收敛 JDK 环境。
- 这次升级属于工程基线演进，不改变活动预约与票务平台的业务模型、接口语义或数据库结构。

## 2. 目标
- 将 Maven 编译基线从 Java `17` 提升到 Java `21`。
- 将 Docker 构建阶段和运行阶段的基础镜像同步切换到 Temurin 21。
- 更新 README 中的本地开发前置条件，避免开发者继续使用 Java 17 启动或构建。
- 使用 Java 21 实际执行测试和打包，确认当前代码、Lombok、Spring Boot 3.5.14、MyBatis、Flyway、springdoc 组合可以正常工作。

成功标准：
- `backend/pom.xml` 中 `java.version` 为 `21`。
- `backend/Dockerfile` 的 build/runtime 阶段都使用 Java 21 镜像。
- 本地使用 JDK 21 执行 `mvn test` 和 `mvn package` 成功。

## 3. 非目标
- 本次不升级 Spring Boot 4.x。
- 本次不调整 MyBatis、Flyway、springdoc、JJWT 等业务依赖版本，除非验证发现 Java 21 兼容问题。
- 本次不引入 Java 21 新语法重写已有业务代码。
- 本次不改造 CI/CD；只在文档中明确 JDK 21 要求，后续如果接入 CI 再同步配置。
- 本次不修改数据库表结构、索引、接口契约、鉴权规则和业务状态机。

## 4. 影响范围
- `backend/pom.xml`
  - `java.version` 从 `17` 调整为 `21`。
  - 同步更新与 Java 基线相关的注释说明。
  - 在 Surefire 测试阶段显式加载 Mockito agent，避免 Java 21 下动态 attach 预警。
- `backend/Dockerfile`
  - build 阶段从 `maven:3.9.9-eclipse-temurin-17` 切换到 `maven:3.9.9-eclipse-temurin-21`。
  - runtime 阶段从 `eclipse-temurin:17-jre` 切换到 `eclipse-temurin:21-jre`。
- `README.md`
  - 本地启动前置条件从 Java 17 更新为 Java 21。
- `docs/ai/`
  - 增加本次 Java 21 基线升级的设计、实现说明和 ADR。

不涉及：
- 数据表、缓存 key、外部接口、消息契约。
- Controller / Service / Mapper 的业务逻辑。

## 5. 领域建模
- 核心实体不变：用户、角色、活动、订单等领域模型不因 JDK 基线升级变化。
- 实体关系不变。
- 关键状态不变。

本次唯一变化是工程运行时环境：
- 构建时 JDK：Java 21。
- 运行时 JRE：Java 21。
- 编译产物目标版本：Java 21。

## 6. API 设计
- 不新增 API。
- 不修改已有 API 的请求参数、响应结构、错误码或异常语义。
- OpenAPI / Swagger UI 仍由 springdoc 提供，验证重点是 Java 21 下相关测试和应用上下文能正常启动。

## 7. 数据设计
- 不调整表结构。
- 不新增索引。
- 不修改唯一约束。
- 不引入新的数据一致性策略。

Java 21 升级只影响应用进程的编译和运行环境，不改变 MySQL / H2 中的数据模型。

## 8. 关键流程
正常流程：
1. 开发者使用 JDK 21 执行 Maven 构建。
2. Maven Compiler Plugin 根据 `java.version=21` 编译源码。
3. Maven Surefire Plugin 在测试 JVM 启动时显式加载 Mockito agent，避免 Java 21 动态 agent 预警。
4. Spring Boot Maven Plugin 打包可执行 Jar。
5. Docker build 阶段使用 JDK 21 打包 Jar。
6. Docker runtime 阶段使用 JRE 21 运行 Jar。

异常流程：
- 如果开发者仍使用 JDK 17 构建，编译阶段会因为目标 release 为 21 而失败。
- 如果运行环境仍是 JRE 17，Java 21 字节码无法启动，需要同步升级部署运行时。

状态流转：
- 本次没有业务状态流转变更。

## 9. 并发 / 幂等 / 缓存
- 不涉及库存扣减、订单幂等、重复提交或缓存策略变更。
- Java 21 不改变当前接口的并发控制策略。
- 后续如果利用虚拟线程等 Java 21 能力优化并发模型，需要单独设计和压测；本次不启用。

## 10. 权限与安全
- 不调整 Spring Security 配置。
- 不改变 JWT 签发、解析、角色授权、401/403 处理逻辑。
- Java 21 基线可以获得更长周期的 JDK 安全更新，但真实生产环境仍需要部署侧持续更新补丁版本。

## 11. 测试策略
- 构建环境确认：
  - 使用 Java 21 执行 `mvn -version`。
  - 使用 Maven 读取 `project.properties.java.version`，确认值为 `21`。
- 单元 / 集成测试：
  - 在 `backend/` 下使用 Java 21 执行 `mvn test`。
- 打包验证：
  - 在 `backend/` 下使用 Java 21 执行 `mvn -DskipTests package`。
- 关键失败场景：
  - 关注 Lombok 注解处理、Spring 应用上下文启动、MockMvc 接口测试、Flyway/H2 测试环境初始化是否受 JDK 升级影响。
  - 关注 Mockito 在 Java 21 下是否仍出现动态 agent 自附加预警。

## 12. 风险与替代方案
- 风险：
  - 本机默认 `java` 仍可能指向 17，开发者需要切换 `JAVA_HOME`。
  - CI/CD 如果后续接入，需要明确设置 JDK 21。
  - Docker 构建环境需要能够拉取 Temurin 21 镜像。
- 备选方案：
  - 方案 A：继续保持 Java 17，只在文档中推荐 Java 21。
  - 方案 B：只改 Maven `java.version`，暂不改 Docker 镜像。
  - 方案 C：同时引入 Maven Enforcer Plugin 强制检查 JDK 版本。
  - 方案 D：忽略 Java 21 下 Mockito 动态 agent 预警。
- 为什么不选备选方案：
  - 不选方案 A：用户已经明确要完成第二步，继续保持 Java 17 无法形成真实基线升级。
  - 不选方案 B：编译目标与容器运行时不一致会埋下部署失败风险。
  - 暂不选方案 C：当前 Maven 编译目标已经能在 JDK 17 下失败并暴露问题；本次先保持最小改动，后续接入 CI 时再考虑补充更友好的构建前置检查。
  - 不选方案 D：虽然预警当前不阻塞 Java 21，但它指向后续 JDK 版本的测试稳定性风险，使用 Surefire 显式加载 agent 成本较低且边界只影响测试。
