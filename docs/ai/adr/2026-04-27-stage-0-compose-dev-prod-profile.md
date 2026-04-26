# 标题
阶段 0 使用 dev/test/prod 环境命名，并让 Docker Compose 默认启动后端应用容器

## 状态
- accepted

## 背景
阶段 0 最新规划要求项目具备可运行、可测试、可本地一键启动的后端基础工程，并明确列出：

- Maven profile：`dev / test / prod`
- `application-dev.yml`
- Dockerfile
- Docker Compose 启动 MySQL、Redis、应用

当前工程已经具备大部分基础能力，但默认环境仍叫 `local`，Compose 也只启动 MySQL/Redis。为了让路线图、README、配置文件和验收命令保持一致，需要收敛阶段 0 的运行口径。

## 决策
选择以下方案：

- 将默认 Spring profile 从 `local` 调整为 `dev`。
- 使用 `application-dev.yml` 作为本地开发配置文件。
- 在 `backend/pom.xml` 中提供 `dev/test/prod` Maven profile，作为 Maven 启动 Spring Boot 应用时的环境选择入口。
- 新增 `backend/Dockerfile`。
- 在 `docker-compose.yml` 中加入 `backend` 服务，使 `docker compose up -d` 默认启动 MySQL、Redis 和后端应用。
- Compose 中的 `backend` 使用 `prod` profile，通过环境变量注入 MySQL/Redis 地址，避免容器内误用 `localhost`。

## 备选方案
- 方案 1：继续保留 `local` profile，只在文档中说明它等价于 `dev`。
- 方案 2：只新增 Dockerfile，不把后端加入 Compose。
- 方案 3：把后端 Compose 服务放进可选 profile，例如执行 `docker compose --profile app up -d` 才启动应用。

## 决策理由
选择当前方案的原因：

- 最新阶段 0 规划已经明确使用 `dev/test/prod`，直接改配置比长期维护 `local/dev` 两套叫法更清晰。
- 项目还未上线，不存在生产兼容性迁移成本，可以在早期把命名一次性收敛。
- `docker compose up -d` 能启动完整基础闭环，更适合新环境复现、学习演示和阶段验收。
- Compose 内使用服务名 `mysql`、`redis` 连接依赖，能提前建立容器网络下的正确配置习惯。
- Maven profile 只作为启动入口，不改变 Spring Boot 仍由 `application-*.yml` 管理环境差异的主模型。

## 影响
- 好处：
  - 阶段 0 规划、README、配置文件和启动方式一致。
  - 新同学或面试演示时可以用一条 Compose 命令启动完整基础环境。
  - 后续云原生阶段可以在当前 Dockerfile / Compose 基础上继续演进镜像构建、健康检查和部署配置。
- 代价：
  - 首次 `docker compose up -d` 会构建后端镜像并下载依赖，比只启动 MySQL/Redis 慢。
  - 本地开发存在两条启动路径：完整 Compose 启动，或只启动依赖后用 IDE/Maven 启动后端，需要 README 明确区分。
- 后续可能需要调整的地方：
  - 后续接入 CI 后，可以把镜像构建和测试放入流水线。
  - 后续进入生产部署时，需要替换 Compose 示例密码，并引入更安全的密钥管理方式。
  - 后续如果 Actuator 健康检查需要容器级判断，可在运行镜像中补轻量健康探针工具或使用平台侧探针。
