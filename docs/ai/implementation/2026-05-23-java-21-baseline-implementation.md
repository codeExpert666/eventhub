# Java 21 基线升级实现说明

## 1. 本次改动解决了什么问题

- 将 EventHub 后端从 Java 17 基线推进到 Java 21 LTS。
- 让 Maven 编译目标、本地开发说明、Docker 构建镜像和 Docker 运行镜像保持一致，避免“本地能编译、容器不能运行”的 JDK 版本错配。
- 处理 Java 21 测试运行时暴露出的 Mockito 动态 agent 预警，使测试阶段显式加载 Mockito agent。

## 2. 改动内容
- 新增了什么
  - 新增设计文档：`docs/ai/design/2026-05-23-java-21-baseline-design.md`。
  - 新增 ADR：`docs/ai/adr/2026-05-23-java-21-baseline.md`。
  - 新增实现说明：`docs/ai/implementation/2026-05-23-java-21-baseline-implementation.md`。
- 修改了什么
  - `backend/pom.xml`
    - `java.version` 从 `17` 调整为 `21`。
    - 更新 MyBatis starter 说明注释，使其匹配 Spring Boot `3.5.14` / Java `21` 基线。
    - 增加 `maven-surefire-plugin` 配置，在测试 JVM 启动时通过 `-javaagent` 显式加载 `mockito-core`。
  - `backend/Dockerfile`
    - build 阶段基础镜像从 `maven:3.9.9-eclipse-temurin-17` 调整为 `maven:3.9.9-eclipse-temurin-21`。
    - runtime 阶段基础镜像从 `eclipse-temurin:17-jre` 调整为 `eclipse-temurin:21-jre`。
    - 同步更新镜像选择说明注释。
  - `README.md`
    - 本地启动前置条件从 Java 17 更新为 Java 21。
- 删除了什么
  - 没有删除业务代码、配置项或依赖。

## 3. 为什么这样设计
- Java 基线升级要同时覆盖编译和运行：
  - Maven `java.version=21` 决定源码编译和产物字节码目标。
  - Docker build 阶段使用 JDK 21，保证镜像内构建结果与本地构建一致。
  - Docker runtime 阶段使用 JRE 21，保证 Java 21 字节码可以正常启动。
- 不改业务代码：
  - 本次目标是工程基线演进，不引入 Java 21 新语法或虚拟线程，便于把 JDK 升级风险和业务变更风险隔离。
- 显式加载 Mockito agent：
  - Java 21 会提示运行期动态加载 agent 的未来兼容风险。
  - Surefire 显式 `-javaagent` 只影响测试 JVM，不影响生产运行时，成本较低且边界清楚。

## 4. 替代方案
- 方案 A：继续保持 Java 17。
  - 没有采用，因为用户已经明确要完成 Java 21 基线升级，而且 Java 21 LTS 更适合作为后续演进基础。
- 方案 B：只改 Maven `java.version`，不改 Docker 镜像。
  - 没有采用，因为编译目标和运行镜像不一致会带来部署期失败风险。
- 方案 C：同时引入 Maven Enforcer Plugin 强制要求 JDK 21。
  - 暂未采用，因为 `java.version=21` 已能让 JDK 17 在编译阶段失败；当前先保持最小构建改动，后续接入 CI 时再补充更友好的前置检查。
- 方案 D：忽略 Mockito 动态 agent 预警。
  - 没有采用，因为这个预警来自 Java 21 测试运行时，显式加载 agent 能提前规避后续 JDK 默认策略变化带来的测试风险。

## 5. 测试与验证
- Java / Maven 环境确认：
  - `mvn -version` 使用 Java `21.0.11`。
  - `mvn help:evaluate -Dexpression=java.version -DforceStdout -q` 输出 `21`。
- 单元与集成测试：
  - `mvn -q clean test` 通过。
  - Surefire 结果：`50` 个测试通过，`0` 个失败，`0` 个错误，`0` 个跳过。
  - 测试日志确认应用以 Spring Boot `3.5.14` 启动，并使用 Java `21.0.11`。
  - Surefire 显式加载 Mockito agent 后，不再出现 Mockito 动态 attach 预警。
- 字节码验证：
  - `javap -verbose target/classes/com/eventhub/EventhubApplication.class | rg 'major version'` 输出 `major version: 65`，对应 Java 21。
- 打包验证：
  - `mvn -q -DskipTests package` 通过。
- 格式检查：
  - `git diff --check` 通过。
- Docker 验证：
  - 初次执行 `docker build -t eventhub-backend:java21-baseline backend` 时，Docker Hub 镜像元数据拉取出现 TLS handshake timeout。
  - 通过本机代理为 OrbStack Docker engine 配置 `http-proxy` / `https-proxy` 后，重新执行 `docker build --progress=plain -t eventhub-backend:java21-baseline backend` 通过。
  - 生成镜像：`eventhub-backend:java21-baseline`。
  - 运行 `docker run --rm --entrypoint java eventhub-backend:java21-baseline -version`，确认运行时为 Temurin Java `21.0.11`。
  - 首次冷构建中 `mvn -q -DskipTests dependency:go-offline` 层耗时约 `598s`，主要来自 Docker 构建环境内 Maven 依赖冷下载。

## 6. 已知限制
- 本机默认 `java` 仍指向 Java 17；后续本地开发需要显式切换 `JAVA_HOME` 到 JDK 21，或把系统默认 JDK 调整为 21。
- Docker 镜像构建已经通过，但依赖本机代理访问 Docker Hub；如果换到 CI 或其他机器，需要同步配置 registry 访问方式。
- 还没有接入 CI，因此没有在 CI 配置中声明 JDK 21。
- Surefire `argLine` 显式引用 Maven 本地仓库中的 Mockito jar；后续如果引入 Jacoco 或其他测试 agent，需要统一管理 `argLine`，避免互相覆盖。
- Java 21 的虚拟线程、record pattern 等能力本次没有启用，避免把运行时基线升级和业务/并发模型改造混在一起。

## 7. 对后续版本的影响
- 对简历可用版的价值
  - 技术基线更新到 Java 21 LTS，更符合当前 Java 后端项目的长期维护预期。
  - 构建、测试、容器运行的 JDK 版本边界更加清晰，可在简历和面试中说明演进路径与风险拆分。
- 对微服务 / 云原生演进的影响
  - 后续拆分服务或接入容器化部署时，可以统一要求 Java 21 镜像和运行时。
  - 为后续评估 Spring Boot 4.0.6、虚拟线程、云原生镜像构建和 CI/CD JDK matrix 打下基础。
