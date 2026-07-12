# 后端工程根目录扁平化设计

## 1. 背景
- 当前仓库是以后端为主体的单体单模块项目，但 Maven 工程仍额外嵌套在 `backend/` 下。
- 本地 Maven 命令需要先进入 `backend/`，Docker Compose 也需要把 `backend/` 指定为构建上下文，增加了没有实际模块隔离收益的目录层级。
- 本次希望让仓库根目录直接成为后端工程根，减少开发、构建、IDE 导入和后续自动化中的路径分歧。
- 迁移前工作区干净，`backend/` 顶层仅包含 `Dockerfile`、`lombok.config`、`pom.xml`、`src/` 和被忽略的 `target/`；根目录不存在同名冲突。

## 2. 目标
- 将 `backend/Dockerfile`、`backend/lombok.config`、`backend/pom.xml` 和 `backend/src/` 移到仓库根目录。
- 迁移后彻底移除 `backend/` 目录，并由根目录直接执行 Maven、Docker 和 IDE 工程导入。
- 同步修正所有会影响当前构建、运行或后续操作的路径引用。
- 保持现有业务代码、HTTP 契约、数据模型、数据库迁移和运行时行为不变。
- 成功标准：根目录 Maven 全量测试通过，生产 profile 可打包，Compose 构建配置可解析且后端镜像可构建，当前活文档不再要求进入 `backend/`。

## 3. 非目标
- 不修改 Maven 坐标，继续保留 `artifactId=backend` 和应用名 `eventhub-backend`。
- 不重命名 Docker Compose 的 `backend` 服务、容器或镜像；这些名称表达服务角色，不是目录路径。
- 不修改 Java 包结构、模块边界、DTO 规则、API、错误码或业务状态流转。
- 不修改表结构、索引、Flyway 版本脚本名称或内容。
- 不引入 Maven 多模块、前端工程或新的重量级依赖。
- 不对 `docs/ai/` 下既有历史设计、实现和 ADR 文档做机械路径替换；这些文档保留当时的工程事实。

## 4. 影响范围
- 构建入口：`backend/pom.xml` 移为根目录 `pom.xml`。
- 源码与测试：`backend/src/` 整体移为根目录 `src/`。
- 容器构建：`backend/Dockerfile` 移到根目录，`docker-compose.yml` 的构建上下文从 `./backend` 改为 `.`。
- 本地规则：更新 `.gitignore`，新增 `.dockerignore`，同步本机已存在的 IntelliJ Maven 与源码路径。
- 当前状态文档：更新 `README.md`、`docs/roadmap/stage-0-project-foundation.md` 和仍可执行的重构提示词。
- 代码注释：修正 `pom.xml`、`lombok.config`、`application.yml`、`application-test.yml` 中已失效的目录描述。
- 涉及表 / 缓存 / 外部接口：不涉及。

## 5. 领域建模
- 核心实体：不涉及业务领域实体变更。
- 实体关系：不变。
- 关键状态：不涉及业务状态机。
- 工程模型：仓库继续采用单体单模块 Spring Boot，只把“仓库根”和“Maven 模块根”合并为同一目录。

## 6. API 设计
- 接口列表：不新增、删除或修改任何接口。
- 请求参数：不变。
- 响应结构：不变。
- 错误码 / 异常场景：不变。

## 7. 数据设计
- 表结构调整：无。
- 索引设计：无。
- 唯一约束：无。
- 数据一致性考虑：Flyway 迁移脚本只发生物理路径上移，仍通过 `classpath:db/migration` 加载，不改变迁移版本和执行顺序。

## 8. 关键流程
- 正常流程：
  1. 先写入本设计与目录布局 ADR，固定迁移边界。
  2. 将四个受 Git 跟踪的后端工程条目移动到仓库根目录。
  3. 将旧 `backend/target/` 视为可重建派生产物，不保留其中可能包含旧绝对路径的 class、报告和 Jar；迁移后由根目录 Maven 构建重新生成 `target/`。
  4. 更新 Git/Docker 忽略规则、Compose 构建上下文、当前说明、操作提示和路径注释。
  5. 更新本地 IntelliJ 路径，并强制重建 CodeGraph 本地索引，避免开发工具继续引用旧源码位置。
  6. 在仓库根目录完成测试、打包、Compose 和路径残留验证。
- 异常流程：
  - 如果目标名称出现冲突，停止移动并保留原目录；迁移前盘点已确认当前无冲突。
  - 如果根目录构建失败，优先检查相对资源路径、Maven 工作目录和 IDE/工具缓存，不修改业务逻辑掩盖结构问题。
- 状态流转：不涉及业务状态流转。

## 9. 并发 / 幂等 / 缓存
- 并发：不改变库存、订单、认证会话等业务并发控制。
- 幂等：不改变接口或消息幂等策略。
- 业务缓存：不改变 Redis 使用边界。
- 构建缓存：Dockerfile 继续先复制 `pom.xml`、再复制 `src/`，保留 Maven 依赖层缓存；根目录新增 `.dockerignore`，避免把 `.git/`、本地 IDE/索引和 `target/` 发送到 Docker 构建上下文。

## 10. 权限与安全
- 不改变角色、鉴权、JWT 或接口访问规则。
- `.dockerignore` 排除本地 Git 元数据、IDE 配置、CodeGraph 索引和构建产物，减少无关本地信息进入 Docker 构建上下文的风险。
- 不移动或改写本地密钥；当前仓库盘点未发现需要随目录迁移处理的密钥文件。

## 11. 测试策略
- 基线验证：迁移前在 `backend/` 执行 `mvn -Ptest test`，确认 89 个测试全部通过。
- 单元与集成测试：迁移后在仓库根目录执行 `mvn clean test -Ptest`。
- 打包验证：在根目录执行 `mvn -DskipTests package -Pprod`，确认 Jar 在根目录 `target/` 生成。
- Compose 验证：执行 `docker compose config`，并在环境允许时执行 `docker compose build backend`，确认根目录构建上下文有效。
- 结构验证：确认 `backend/` 不存在，`pom.xml`、`Dockerfile`、`lombok.config`、`src/` 位于根目录。
- 路径验证：扫描当前配置、源码注释、README、路线图和操作提示中是否仍存在会被执行的 `backend/` 路径；历史 `docs/ai/` 记录允许保留。
- 质量验证：执行 `git diff --check`，检查移动识别、空白错误和意外文件。
- API 手工验证：本次不修改 API，已有 Spring Boot 集成测试和 OpenAPI 契约测试覆盖接口回归，不额外启动真实外部依赖。
- 关键失败场景：验证 Compose 不再引用不存在的 `./backend`，根目录 Maven 不依赖父 POM，Flyway 测试迁移仍可从 classpath 加载。

## 12. 风险与替代方案
- 当前方案的风险：
  - 历史文档仍会出现 `backend/`，全仓简单文本搜索不能直接以“零结果”作为验收，需要区分历史记录和当前可执行引用。
  - 本地 IDE、CodeGraph 或其他未纳入 Git 的工具缓存可能保留旧路径，需要刷新或重建。
  - 如果未来在同一仓库加入前端，根目录会同时承担后端构建根和仓库根职责，届时可能需要重新规划多工程布局。
- 备选方案：
  - 方案 A：保留 `backend/`，只在根目录增加 Maven Wrapper 或代理脚本。
  - 方案 B：在根目录新增聚合父 `pom.xml`，继续保留 `backend/` 子模块。
  - 方案 C：迁移源码和 POM，但让 Dockerfile 或部分运行文档继续留在原层级。
- 为什么不选备选方案：
  - 方案 A 不能消除实际目录层级和 IDE/Compose 路径分歧。
  - 方案 B 会为当前唯一模块增加聚合构建复杂度，不符合最小可用原则。
  - 方案 C 会形成多个工程根，使命令和上下文更难理解，也不能满足彻底移除 `backend/` 的目标。
