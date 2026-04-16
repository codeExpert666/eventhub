# 后端基础工程实现说明

## 1. 本次改动解决了什么问题

本次改动解决了仓库中没有可运行后端工程的问题，建立了一个面向后续业务阶段的单体 Spring Boot 基础工程，并打通了以下基础能力：

- 后端脚手架与标准目录分层
- 统一响应体
- 统一异常处理
- 参数校验
- 基础日志与 `requestId`
- 分环境配置
- OpenAPI
- 健康检查
- MySQL / Redis 接入方式
- Flyway 基线迁移
- Docker Compose 本地依赖
- README 启动说明

## 2. 改动内容
- 新增了 `backend/` Maven 工程，采用 `common / config / modules.system` 的单体分层结构
- 新增了统一响应对象 `ApiResponse`、错误码 `ErrorCode`、异常类 `BusinessException`、全局异常处理器 `GlobalExceptionHandler`
- 新增了 `RequestIdFilter` 与 `logback-spring.xml`，让日志、响应体和响应头共享同一个请求标识
- 新增了系统示例接口 `GET /api/v1/system/ping` 和 `POST /api/v1/system/echo`
- 新增了 `application.yml`、`application-local.yml`、`application-test.yml`、`application-prod.yml`
- 新增了 OpenAPI 配置和 Actuator 健康检查暴露
- 新增了 Flyway 脚本 `V1__init_backend_foundation.sql`
- 新增了 `docker-compose.yml`
- 新增了根目录 `README.md`
- 新增了 `SystemControllerTest` 作为最小闭环验证，并在 review 后补充了健康检查与 OpenAPI 可用性校验
- 在 review 后补充了非法 JSON 的统一异常处理，以及 `X-Request-Id` 的格式校验与兜底重生成
- 在 review 后收紧了 `prod` profile 配置，要求显式提供数据库和 Redis 的关键环境变量，避免误用本地默认值

## 3. 为什么这样设计
- 当前项目阶段的重点是先建立“稳定的工程约束”，而不是提前堆业务抽象，所以选择单体单模块结构，优先让目录分层、配置、日志、文档和本地运行方式稳定下来。
- 统一响应、统一异常和参数校验是后续所有业务接口都会复用的横切能力，越早固化，后续返工越少。
- Redis 和 MySQL 在本阶段只做基础接入，不提前承载业务逻辑，避免为了“看起来完整”而引入伪复杂度。
- 保留一个 `modules.system` 示例模块，而不是只放配置文件，是为了证明工程不是空壳，接口、校验、异常和测试链路已经真实可用。

## 4. 替代方案
- 方案 A：直接拆成多模块 Maven 工程
- 方案 B：直接上微服务骨架
- 方案 C：只做配置接入，不提供示例接口
- 为什么没有采用：
  - 不选方案 A：当前业务还未展开，多模块会增加构建、依赖管理和目录复杂度，但收益有限
  - 不选方案 B：项目路线明确是“先单体再演进”，此时直接上微服务会让学习重心偏离业务建模和基础工程能力
  - 不选方案 C：如果没有最小接口闭环，就无法证明统一响应、异常和校验真的打通

## 5. 测试与验证
- 跑了哪些测试：
  - `cd backend && mvn test`
- 手工验证了哪些场景：
  - `docker compose config`
- 结果如何：
  - `mvn test` 通过，7 个测试全部成功
  - Flyway 在测试环境下成功执行基线迁移
  - `docker compose config` 通过，说明 MySQL / Redis 的 Compose 文件语法正确
  - 额外尝试了 `mvn spring-boot:run -Dspring-boot.run.profiles=test`，发现 `spring-boot:run` 不会把 `test` scope 的 H2 带入运行时类路径，因此该方式不适合作为测试 profile 的手工启动验证
  - review 期间补充验证了“非法 JSON 请求体返回统一错误响应”“非法 `X-Request-Id` 不会原样透传”“健康检查端点可用”“OpenAPI 文档已暴露系统接口”

## 6. 已知限制
- 当前只提供系统级示例接口，没有真实业务模块
- 统一响应体目前由业务控制器显式返回，没有做全局自动包装
- 测试环境使用 H2，和 MySQL 仍存在方言差异
- 尚未接入认证、鉴权、幂等、防重复提交、缓存策略和可观测性体系
- 当前仍未接入 Spring Security，因此所有基础接口仅适合本地开发环境使用，不能直接作为公网暴露配置
- `prod` profile 已改为关键环境变量缺失即启动失败，这有助于避免误连本地依赖，但也要求部署脚本必须完整注入配置

## 7. 对后续版本的影响
- 对简历可用版的价值：
  - 已经具备一个完整、可解释、可验证的后端基础工程起点，后续业务阶段可以在这个底座上逐步堆叠
- 对微服务 / 云原生演进的影响：
  - 当前目录边界已经具备按业务模块继续拆分的基础
  - MySQL、Redis、Flyway、Actuator、OpenAPI 的接入方式后续都可以平滑迁移到容器化部署、配置中心和服务拆分场景
