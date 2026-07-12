# Java 21 作为后端运行时基线

## 标题
将 EventHub 后端 Java 基线从 17 升级到 21

## 状态
- accepted

## 背景
项目已先完成 Spring Boot `3.5.14` 升级，框架层面回到 Spring Boot 3.x 的受支持版本。上一阶段为了降低排障复杂度，Java 基线暂时保持在 17。

现在需要推进第二步，把 JDK 基线升级到 Java 21 LTS。这个决策会影响本地开发、Maven 编译、Docker 构建镜像和最终运行镜像，但不应改变当前业务行为。

## 决策
- 将 `backend/pom.xml` 的 `java.version` 从 `17` 调整为 `21`。
- 将 Docker build 阶段切换到 `maven:3.9.9-eclipse-temurin-21`。
- 将 Docker runtime 阶段切换到 `eclipse-temurin:21-jre`。
- README 前置条件同步要求 Java 21。
- Maven Surefire 测试阶段显式加载 Mockito agent，避免 Java 21 下运行期动态 attach 预警。
- 当前不引入 Java 21 新语法重写业务代码，也不引入虚拟线程等运行时模型调整。

## 备选方案
- 方案 1：继续保持 Java 17。
- 方案 2：只更新 Maven 编译基线，不更新 Docker 运行镜像。
- 方案 3：升级到 Java 21，同时引入 Maven Enforcer Plugin 做 JDK 版本前置检查。
- 方案 4：忽略 Mockito 在 Java 21 下的动态 agent 预警，等未来 JDK 出现失败后再处理。

## 决策理由
- Java 21 是 LTS 版本，相比 Java 17 更适合作为后续一段时间的学习、简历和演进基线。
- Spring Boot 3.5.x 支持 Java 21，先在 Boot 3.5 阶段完成 JDK 升级，可以为后续 Spring Boot 4.x 迁移减少变量。
- Maven 编译基线和 Docker 运行镜像必须同步，否则可能出现本地构建成功但容器运行失败的版本错配。
- 暂不引入 Maven Enforcer Plugin，是为了保持本次改动最小；`java.version=21` 已经会让 JDK 17 构建在编译阶段失败。后续接入 CI 时可以再补充更明确的 JDK 前置校验。
- Java 21 测试运行时会提示 Mockito 动态加载 agent 的未来兼容风险；显式配置 Surefire `argLine` 可以把 agent 加载前移到测试 JVM 启动阶段，避免依赖运行期自附加。

## 影响
- 好处：
  - 编译、测试和容器运行统一到 Java 21。
  - 为后续 Spring Boot 4.x、虚拟线程评估、现代 JDK 性能与安全更新打基础。
  - 文档和实际构建基线保持一致，减少开发环境误配。
  - Java 21 下测试日志减少 Mockito 动态 agent 预警，后续 JDK 升级更稳。
- 代价：
  - 开发者本地必须安装并切换到 JDK 21。
  - 后续 CI/CD 和部署环境也必须同步升级到 Java 21。
  - Docker 构建依赖 Temurin 21 镜像可拉取。
  - Surefire `argLine` 对 Mockito 本地仓库路径有显式引用，后续如果改用不同测试 agent 或 Jacoco，需要一起调整。
- 后续可能需要调整的地方：
  - 接入 CI 后增加 JDK 21 matrix 或 Enforcer 检查。
  - 评估是否引入 Java 21 的虚拟线程，但必须配合压测和数据库连接池容量设计。
  - 在 Java 21 基线稳定后，再单独评估 Spring Boot 4.0.6。
