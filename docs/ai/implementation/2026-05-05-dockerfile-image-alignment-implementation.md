# Dockerfile 镜像版本对齐实现说明

## 1. 本次改动解决了什么问题

本次解决 `backend/Dockerfile` 构建阶段 Maven 镜像版本偏旧的问题。此前容器构建使用 `maven:3.9.9-eclipse-temurin-17`，而本机开发环境已经切换到 Homebrew Maven 3.9.15 和 Eclipse Temurin JDK 17。调整后，容器构建工具链和本机 Maven patch 版本保持一致。

## 2. 改动内容
- 新增了什么：
  - 新增设计文档 `docs/ai/design/2026-05-05-dockerfile-image-alignment-design.md`。
  - 新增实现说明 `docs/ai/implementation/2026-05-05-dockerfile-image-alignment-implementation.md`。
- 修改了什么：
  - 将 `backend/Dockerfile` 构建阶段基础镜像从 `maven:3.9.9-eclipse-temurin-17` 调整为 `maven:3.9.15-eclipse-temurin-17-noble`。
  - 将运行阶段基础镜像从 `eclipse-temurin:17-jre` 调整为 `eclipse-temurin:17-jre-noble`。
  - 同步更新 Dockerfile 中关于 Maven 版本、Temurin 17 和 Ubuntu Noble 变体的注释。
- 删除了什么：
  - 没有删除源码、配置或业务能力。

## 3. 为什么这样设计
- Maven 3.9.15 与当前本机 Homebrew Maven 版本一致，可以减少本地构建和容器构建之间的工具链差异。
- 继续使用 Eclipse Temurin 17，保持与 `pom.xml` 中 `java.version=17` 的项目基线一致。
- 构建阶段使用 Maven/JDK 镜像，运行阶段继续使用 JRE 镜像，保持多阶段构建的职责边界。
- 显式使用 `noble` 变体，让基础操作系统更可见，便于后续做漏洞扫描、镜像升级和云原生部署排查。

## 4. 替代方案
- 方案 A：只把构建镜像改为 `maven:3.9.15-eclipse-temurin-17`，运行镜像不动。
- 方案 B：引入 Maven Wrapper，让本机和容器都通过仓库内 wrapper 固定 Maven 版本。
- 方案 C：把基础镜像固定到 digest。
- 为什么没有采用：
  - 没有采用方案 A，是因为运行阶段也显式使用 Noble 变体可以让构建和运行基础发行版保持一致。
  - 没有采用方案 B，是因为当前项目已有 Maven 3.9+ 前置条件，单次 Dockerfile 镜像对齐不需要额外引入 wrapper 文件。
  - 没有采用方案 C，是因为 digest 固定会降低阅读友好度，当前阶段先保持标签可读；后续进入 CI/CD 发布阶段后再考虑 digest pinning。

## 5. 测试与验证
- 已通过 Docker Hub 标签接口确认 `maven:3.9.15-eclipse-temurin-17-noble` 标签处于 active 状态。
- 已通过 Docker Hub 标签接口确认 `eclipse-temurin:17-jre-noble` 标签处于 active 状态。
- 已运行 `mvn -q clean -Ptest test`，验证本机 Maven 3.9.15 + Temurin 17 工具链可以正常编译并通过现有测试。
- 已确认本机代理 `http://127.0.0.1:7897` 可访问 Docker Registry，`curl --proxy http://127.0.0.1:7897 https://registry-1.docker.io/v2/` 返回 Docker Registry 预期的 401 challenge。
- 已将 OrbStack Docker daemon 代理配置为通过 `http://host.internal:7897` 访问宿主机代理，并重启 OrbStack。
- 已运行 `docker pull --quiet hello-world:latest`，确认 Docker daemon 可以通过代理拉取镜像。
- 已运行构建阶段验证：
  - 命令：`DOCKER_BUILDKIT=1 docker build --pull --target build ... -t eventhub-backend-build-check:maven-3.9.15 ./backend`
  - 结果：成功拉取 `maven:3.9.15-eclipse-temurin-17-noble`，完成 `mvn dependency:go-offline` 和 `mvn package`，并导出 build 阶段镜像。
- 已运行完整 Dockerfile 验证：
  - 命令：`DOCKER_BUILDKIT=1 docker build --pull ... -t eventhub-backend:dockerfile-image-alignment ./backend`
  - 结果：成功拉取 `eclipse-temurin:17-jre-noble`，复制可执行 Jar，并导出最终后端镜像。

## 6. 已知限制
- 当前仍未固定镜像 digest，因此基础镜像会跟随对应标签的安全补丁更新。
- 当前未引入 Maven Wrapper；如果未来多人协作或 CI 环境增多，可以评估是否用 wrapper 固定 Maven 分发版本。
- Docker 构建是否耗时主要取决于首次拉取新基础镜像和 Maven 依赖缓存状态。
- OrbStack 中 Docker daemon 访问宿主机代理时不能直接使用 `127.0.0.1`，应使用 `host.internal` 这类 OrbStack 宿主机地址；否则 daemon 侧拉取镜像仍可能出现 Docker Hub TLS 握手超时。

## 7. 对后续版本的影响
- 对简历可用版的价值：
  - 展示了本机工具链、容器构建工具链和运行镜像边界的基本治理意识。
  - 让 Dockerfile 注释继续保持可学习、可解释。
- 对微服务 / 云原生演进的影响：
  - 明确的基础镜像标签有利于后续接入镜像扫描、CI 构建缓存、Kubernetes 部署和镜像升级策略。
  - 未来如果拆分多个服务，可以复用“构建镜像固定 Maven/JDK，运行镜像只保留 JRE”的多阶段构建模式。
