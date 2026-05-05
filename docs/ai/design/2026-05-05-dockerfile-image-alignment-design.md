# Dockerfile 镜像版本对齐设计

## 1. 背景
- 本机开发环境已经切换为 Homebrew 安装的 Eclipse Temurin JDK 17 和 Maven 3.9.15。
- 当前 `backend/Dockerfile` 的构建阶段仍使用 `maven:3.9.9-eclipse-temurin-17`，与本机 Maven patch 版本不一致。
- Docker Hub 官方 Maven 镜像已经提供 `3.9.15-eclipse-temurin-17-noble` 标签，适合用来对齐构建工具链。

## 2. 目标
- 将后端镜像构建阶段升级到 Maven 3.9.15 + Eclipse Temurin 17。
- 明确构建阶段和运行阶段都使用 Ubuntu Noble 变体，降低基础发行版标签漂移带来的隐性差异。
- 保持项目 Java 17 基线不变，保证本机 Maven 测试和容器构建语义一致。

## 3. 非目标
- 不升级 Spring Boot、业务依赖或 Maven 插件版本。
- 不引入 Maven Wrapper。
- 不调整 Docker Compose 服务拓扑、端口、数据库或 Redis 配置。
- 不把 Java patch 版本固定到具体 `17.0.x`，当前项目只约束 Java 17 主版本。

## 4. 影响范围
- 涉及模块：`backend/Dockerfile`。
- 涉及表 / 缓存 / 外部接口：不涉及数据库表、缓存或业务外部接口。
- 涉及开发流程：影响 `docker compose build backend` / `docker compose up -d` 首次构建时使用的基础镜像。

## 5. 领域建模
- 核心实体：不涉及业务领域实体。
- 实体关系：不涉及。
- 关键状态：不涉及业务状态流转。
- 工程对象：后端应用镜像仍分为 `build` 和 `runtime` 两个阶段。

## 6. API 设计
- 接口列表：不新增或修改 API。
- 请求参数：不涉及。
- 响应结构：不涉及。
- 错误码 / 异常场景：不涉及业务错误码。

## 7. 数据设计
- 表结构调整：不涉及。
- 索引设计：不涉及。
- 唯一约束：不涉及。
- 数据一致性考虑：镜像构建不改变运行时数据模型。

## 8. 关键流程
- 正常流程：
  1. Docker 构建阶段基于 `maven:3.9.15-eclipse-temurin-17-noble` 下载依赖并执行 `mvn package`。
  2. 运行阶段基于 `eclipse-temurin:17-jre-noble` 复制可执行 Jar。
  3. 容器启动时继续通过 `java -jar /app/app.jar` 运行后端应用。
- 异常流程：
  - 如果基础镜像拉取失败，Docker 构建会在解析 `FROM` 或拉取镜像阶段失败。
- 状态流转：不涉及业务状态机。

## 9. 并发 / 幂等 / 缓存
- 并发：不涉及业务并发控制。
- 幂等：不涉及业务幂等。
- 缓存：仍保留 Docker 分层缓存策略，先复制 `pom.xml` 再复制源码，避免普通源码修改反复下载 Maven 依赖。

## 10. 权限与安全
- 不改变接口鉴权和角色权限。
- 运行阶段继续使用 JRE 镜像而不是 Maven/JDK 镜像，避免把构建工具带入最终运行镜像。
- 显式使用 Noble 变体，便于后续安全扫描和基础镜像升级时定位操作系统基线。

## 11. 测试策略
- 单元测试：运行 `mvn -q clean -Ptest test`，验证当前 Temurin 17 / Maven 3.9.15 本机工具链可正常编译和测试。
- 集成测试：上述 Maven 测试会覆盖已有 Spring Boot 集成测试。
- 接口验证：本次不修改 API，不需要额外接口手工验证。
- 异常场景验证：通过 Docker Hub 标签查询确认 `maven:3.9.15-eclipse-temurin-17-noble` 和 `eclipse-temurin:17-jre-noble` 标签可用。

## 12. 风险与替代方案
- 当前方案风险：
  - 首次构建需要拉取新基础镜像，耗时取决于网络环境。
  - `17-jre-noble` 仍会跟随 Temurin 17 安全补丁更新，未锁死到 digest。
- 备选方案：
  - 方案 A：只升级 Maven 构建镜像，不调整运行镜像。
  - 方案 B：使用无操作系统后缀的 `maven:3.9.15-eclipse-temurin-17` 和 `eclipse-temurin:17-jre`。
  - 方案 C：固定到镜像 digest。
- 为什么不选备选方案：
  - 不只升级构建镜像，是为了让构建阶段和运行阶段的基础发行版都显式为 Noble，便于后续排查。
  - 不使用无后缀标签，是为了减少底层发行版切换带来的隐性差异。
  - 暂不固定 digest，是因为当前学习型项目更需要可读、可维护的标签；digest 固定适合后续进入 CI/CD 安全扫描和发布阶段时再引入。
