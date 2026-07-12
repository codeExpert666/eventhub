# 后端工程根目录扁平化实现说明

## 1. 本次改动解决了什么问题

本次改动移除了仓库根目录与实际 Spring Boot 工程之间多余的 `backend/` 层级。迁移后，仓库根目录直接包含 `pom.xml`、`Dockerfile`、`lombok.config`、`src/` 和 Maven 生成的 `target/`，开发者不再需要先执行 `cd backend` 才能构建、测试或启动应用。

改动只解决工程路径与工具入口不统一的问题，不改变 Java 包、业务逻辑、API、数据库迁移、认证授权或 Docker 服务语义。

## 2. 改动内容
- 移动：
  - `backend/Dockerfile` → `Dockerfile`
  - `backend/lombok.config` → `lombok.config`
  - `backend/pom.xml` → `pom.xml`
  - `backend/src/` → `src/`
- 删除：
  - 移除已经为空的 `backend/` 目录。
  - 旧 `backend/target/` 中的 class、测试报告和 Jar 属于派生产物，迁移到根目录后通过 `mvn clean` 清除，并在新的工程根重新生成。
- 构建与工具配置：
  - `docker-compose.yml` 的后端构建上下文由 `./backend` 改为仓库根目录 `.`。
  - 新增 `.dockerignore`，排除 `.git/`、本地 IDE/索引目录和 `target/`，避免根构建上下文携带无关内容。
  - `.gitignore` 改为忽略根目录 Maven 与 JDTLS 产物。
  - 修正 `pom.xml`、`lombok.config` 和 Spring 配置注释中的工程路径表述。
  - 更新本机已存在但被 Git 忽略的 IntelliJ Maven POM 与源码编码路径。
  - 执行 `codegraph index --force`，清除旧索引并按根目录 `src/` 重建本地代码图。
- 当前状态文档与操作入口：
  - 更新 `README.md` 的目录树和 Maven 命令。
  - 更新 `docs/roadmap/stage-0-project-foundation.md` 的推荐目录结构。
  - 更新 `prompts/module-dto-organization-refactor.md` 中仍会被执行的源码扫描与 Maven 命令。
- 文档沉淀：
  - 新增本次设计文档和目录布局 ADR。
  - 既有 `docs/ai/` 历史文档保留原 `backend/` 路径，避免改写当时的设计、实现和验证事实。

## 3. 为什么这样设计
- 当前仓库只有一个可构建后端应用，仓库根与 Maven 模块根合并后，Maven、Docker、IDE、CodeGraph 和说明文档可以共享同一工作目录。
- 保留 `artifactId=backend`、Compose `backend` 服务名和 `eventhub-backend` 镜像名，可以把“物理目录迁移”和“工程语义重命名”解耦，减少无收益的运行配置变化。
- 旧 `target/` 可能包含旧绝对路径、编译结果和测试报告，因此使用根目录 `mvn clean` 重建比原样保留更可靠。
- Compose 使用根目录上下文后补充 `.dockerignore`，既满足 Dockerfile 的 `COPY pom.xml` / `COPY src` 语义，又控制构建上下文体积和本地信息暴露范围。
- 历史 `docs/ai/` 保留原路径，本次 ADR 作为新约定生效边界，兼顾当前可执行性和复盘真实性。

## 4. 替代方案
- 方案 A：保留 `backend/`，只增加根目录启动脚本。
  - 未采用，因为脚本只能隐藏层级，不能消除 Compose、IDE 和代码分析工具中的双重工程根。
- 方案 B：根目录新增聚合父 POM，继续把 `backend/` 作为 Maven 子模块。
  - 未采用，因为当前没有第二个 Maven 模块，聚合层只会增加构建和依赖管理复杂度。
- 方案 C：只移动 POM 和源码，保留 Dockerfile 或部分文档在旧目录。
  - 未采用，因为这会产生多个相对路径基准，也无法彻底移除 `backend/`。
- 方案 D：同步把 artifactId、Compose 服务名和镜像名从 `backend` 改为其他名称。
  - 未采用，因为这些名称仍准确表达后端应用角色，改名与目录扁平化无直接收益，还会扩大配置与部署影响范围。

## 5. 测试与验证
- 迁移前基线：在原 `backend/` 中执行 `mvn -Ptest test`，89 个测试全部通过。
- 根目录全量测试：执行 `mvn clean test -Ptest`，重新编译 59 个主源码和 16 个测试源码；89 个测试全部通过，失败 0、错误 0、跳过 0。
- 生产打包：执行 `mvn -DskipTests package -Pprod`，构建成功，并生成 `target/backend-0.0.1-SNAPSHOT.jar`。
- Flyway / API 契约：全量测试中 3 个 Flyway 迁移在 H2 测试库成功执行，OpenAPI 契约测试继续通过。
- Compose 配置：`docker compose config --quiet` 通过，`docker compose config --images` 正确解析后端、MySQL 和 Redis 镜像。
- Docker 上下文：`docker compose build backend` 已成功读取根目录 `Dockerfile`、`.dockerignore`、`pom.xml`，构建上下文约 377 KB；后续容器内执行 Maven 依赖预取时，直连出现 Maven Central TLS 握手中断，第二次直连和临时代理尝试无进展后终止，因此本次未完成最终镜像层输出。该限制发生在远程依赖下载阶段，不是路径或 COPY 失败。
- 结构检查：确认 `backend/` 不存在，根目录所需四个工程条目和重新生成的 `target/` 均存在。
- 内容检查：逐一比较迁移前 Git 对象与迁移后文件，除 `pom.xml`、`lombok.config`、`application.yml`、`application-test.yml` 的路径注释外，其余移动文件内容一致；业务 Java、SQL、Mapper 和测试代码未发生修改。
- 残留路径检查：当前配置、源码、README、路线图、提示词和本地 IDE 路径中，不再存在可执行的 `cd backend`、`./backend` 构建上下文或 `backend/src` 等旧引用。
- 本地索引：`codegraph index --force` 成功索引 85 个文件，生成 1,310 个节点和 2,412 条边。
- 差异质量：`git diff HEAD --check` 通过；新增文档和 `.dockerignore` 也完成行尾空白检查；最终没有暂存文件。
- API 手工验证：未单独启动依赖做手工调用。本次没有接口行为变化，现有集成测试、控制器测试和 OpenAPI 契约测试已覆盖回归。

## 6. 已知限制
- 由于 Docker 构建容器访问 Maven Central 的 TLS / 网络问题，本次只验证到依赖预取阶段，未完成最终镜像构建；网络恢复后应重新执行 `docker compose build backend`。
- 既有 `docs/ai/` 和阶段回顾文档仍包含 `backend/`，这些是历史事实而非当前操作指令；做全仓路径搜索时需要结合文档日期判断。
- IntelliJ 路径更新和 CodeGraph 索引属于本地忽略状态，不会随 Git 提交共享给其他开发者；其他开发环境首次拉取后应从根目录 `pom.xml` 导入并自行建索引。
- 如果未来加入前端或第二个独立构建单元，需要重新评估根目录单工程布局，而不是继续把所有工程内容平铺到根目录。

## 7. 对后续版本的影响
- 对简历可用版的价值：项目构建入口更直接，README、IDE 和 Docker 入口一致，演示和复现步骤更短。
- 对后续功能开发的影响：后续所有 Maven、源码扫描和测试命令默认从仓库根目录执行，新文档应使用 `src/...` 和根目录 `pom.xml`。
- 对微服务 / 云原生演进的影响：本次不改变模块边界或部署语义；出现真实的多构建单元或独立部署需求时，可基于本次 ADR 重新评估 Maven 多模块、`backend/` / `frontend/` 分层或服务仓库拆分。
