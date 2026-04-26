# 后端基础工程设计文档

## 1. 背景
- 当前仓库还没有可运行的后端工程，`backend/` 目录为空，缺少统一的目录分层、基础配置、接口契约和本地依赖环境。
- 项目主题是活动预约与票务平台，后续会逐步演进注册登录、活动、订单、支付、通知等模块，因此需要先把单体后端的基础约束搭好，避免后续阶段边做业务边返工基础设施。

## 2. 目标
- 在 `backend/` 下建立一个可启动、可测试、可继续扩展的单体 Spring Boot 基础工程。
- 完成统一响应体、统一异常处理、参数校验、基础日志、OpenAPI、健康检查、分环境配置、MySQL/Redis 接入和 Flyway 基线迁移。
- 提供一组系统级示例接口，验证基础工程闭环已经打通。
- 成功标准：
  - `docker compose` 可以启动 MySQL 和 Redis。
  - 应用可以在本地启动。
  - `GET /actuator/health` 可以查看应用与依赖组件状态。
  - `GET /api/v1/system/ping`、`POST /api/v1/system/echo` 可用。
  - Flyway 能在数据库中执行基线迁移。

## 3. 非目标
- 不实现注册登录、JWT、RBAC、活动/场次/票种、订单、支付、通知等业务功能。
- 不引入消息队列、分布式锁、分布式事务、复杂缓存策略。
- 不构建多模块 Maven 聚合工程，也不提前做微服务拆分。

## 4. 影响范围
- 涉及模块：
  - `backend/` Maven 工程
  - `common` 公共层
  - `infra.openapi` 接口文档基础设施层
  - `infra.logging` 日志与请求追踪基础设施层
  - `modules.system` 系统示例模块
  - `infra` 基础设施资源目录
- 涉及表 / 缓存 / 外部接口：
  - MySQL：Flyway 基线表和基础系统表
  - Redis：仅做连接接入与健康检查，不承载业务缓存
  - OpenAPI：Swagger UI
  - Actuator：健康检查端点

## 5. 领域建模
- 核心实体：
  - `ApiResponse<T>`：统一响应包装对象，承载 `code`、`message`、`data`、`requestId`、`timestamp`
  - `ErrorCode`：系统级错误码枚举
  - `BusinessException`：业务/系统自定义异常
  - `PingInfo`：系统探活返回 VO
  - `EchoRequest`：参数校验与统一异常处理的请求 DTO
  - `EchoInfo`：回显示例返回 VO
  - `SystemBootstrapRecord`：数据库基线记录对象，对应首个基础系统表
- 实体关系：
  - `SystemController` 调用 `SystemService`
  - `SystemService` 负责组装 `PingInfo` 与 `EchoInfo`
  - `GlobalExceptionHandler` 负责把校验异常、业务异常和未知异常统一映射为 `ApiResponse`
- 关键状态：
  - 应用健康状态：`STARTING`、`UP`、`DOWN`
  - 请求处理状态：`RECEIVED`、`VALIDATED`、`HANDLED`、`SUCCEEDED`、`FAILED`
  - 本阶段不建立业务状态机，避免伪建模

## 6. API 设计
- 接口列表：
  - `GET /api/v1/system/ping`
  - `POST /api/v1/system/echo`
  - `GET /actuator/health`
  - `GET /swagger-ui/index.html`
- 请求参数：
  - `GET /api/v1/system/ping`
    - 无请求体
  - `POST /api/v1/system/echo`
    - `message`：必填，非空，长度 1-64
    - `tag`：可选，长度不超过 32
- 响应结构：
  - 业务接口统一返回：
    - `code`：成功或错误码
    - `message`：描述信息
    - `data`：具体业务数据
    - `requestId`：请求唯一标识
    - `timestamp`：响应时间
  - `GET /actuator/health` 保持 Spring Actuator 原生响应，不做统一包装
- 错误码 / 异常场景：
  - 参数校验失败：返回统一校验错误码
  - 业务异常：返回自定义错误码
  - 未知异常：返回通用内部错误码
  - 数据源或 Redis 不可达：体现在健康检查状态中

## 7. 数据设计
- 表结构调整：
  - 新增 Flyway `V1__init_backend_foundation.sql`
  - 新建表 `system_bootstrap_record`
    - `id BIGINT PRIMARY KEY AUTO_INCREMENT`
    - `environment VARCHAR(32) NOT NULL`
    - `note VARCHAR(128) NOT NULL`
    - `created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP`
- 索引设计：
  - 主键索引：满足主键访问
  - 普通索引 `idx_system_bootstrap_record_environment(environment)`：支持后续环境初始化排查
- 唯一约束：
  - 本阶段不加唯一约束，避免把“单次初始化”语义硬编码进系统表
- 数据一致性考虑：
  - 此表仅用于基础工程基线记录，不参与高并发业务事务
  - 后续业务表在对应阶段单独设计事务边界与约束

## 8. 关键流程
- 正常流程：
  - 本地先通过 Docker Compose 启动 MySQL 与 Redis
  - 应用启动后连接 MySQL，Flyway 执行基线迁移
  - `GET /api/v1/system/ping` 返回统一响应
  - `POST /api/v1/system/echo` 在参数合法时返回统一响应
  - `GET /actuator/health` 返回应用、MySQL、Redis 状态
- 异常流程：
  - `POST /api/v1/system/echo` 参数不合法时，控制器参数校验抛出异常
  - 全局异常处理器统一转换为标准错误响应
  - MySQL/Redis 未就绪时，应用启动或健康状态会显式暴露问题
- 状态流转：
  - 应用状态：`STARTING -> UP`，或 `STARTING -> DOWN`
  - 请求状态：`RECEIVED -> VALIDATED -> HANDLED -> SUCCEEDED`
  - 请求状态：`RECEIVED -> VALIDATION_FAILED`
  - 请求状态：`RECEIVED -> VALIDATED -> FAILED`

## 9. 并发 / 幂等 / 缓存
- 是否有超卖风险：
  - 本阶段没有库存和订单写操作，不存在超卖风险
- 如何防重复提交：
  - 本阶段不实现幂等中间件
  - 设计上先引入 `requestId`，为后续日志追踪、幂等键设计和调用链排查打基础
- 缓存放在哪里，为什么：
  - Redis 在本阶段仅作为基础设施接入和健康检查对象
  - 不对 `ping/echo` 做缓存，避免无价值复杂度
  - 后续阶段再按业务场景决定本地缓存、Redis 缓存和 TTL 策略

## 10. 权限与安全
- 哪些角色能访问：
  - 本阶段所有基础接口默认不做身份认证，面向本地开发环境开放
- 鉴权与鉴别约束：
  - 保留 `/api/v1` 统一前缀，便于后续接入 Spring Security
  - 通过统一异常与错误码结构，为后续 401/403 响应预留兼容空间
  - 不在当前阶段引入账号体系，避免范围失控

## 11. 测试策略
- 单元测试：
  - 针对系统示例接口做轻量 Web 层测试
  - 验证统一响应与参数校验失败结构
- 集成测试：
  - 使用 `@SpringBootTest` 与 `MockMvc`
  - 使用 `test` profile，采用 H2 替代 MySQL
  - 在测试环境关闭 Redis 健康强依赖，避免测试必须依赖外部容器
- 接口验证：
  - 手工访问 `ping`、`echo`、`actuator/health`、`swagger-ui`
  - 验证 OpenAPI 页面是否展示系统接口
- 异常场景验证：
  - `message` 为空
  - `message` 超长
  - 基础设施未启动时的错误表现说明

## 12. 风险与替代方案
- 当前方案的风险：
  - 当前基线表业务价值较弱，但它能让 Flyway、命名规范和数据库接入在最小范围内先落地
  - 不引入安全框架意味着基础接口只适合开发阶段使用，后续必须在认证阶段及时收口
  - 测试环境使用 H2，与 MySQL 在方言上仍存在差异
- 备选方案：
  - 方案 A：直接创建完整用户/活动业务表作为首个迁移
  - 方案 B：完全不创建业务外的应用表，只保留 Flyway 历史表
  - 方案 C：直接使用 PostgreSQL 或仅使用内存数据库起步
- 为什么不选备选方案：
  - 不选方案 A：会把业务阶段前置，偏离当前“先搭工程底座”的目标
  - 不选方案 B：虽然更轻，但学习与验证价值偏弱，无法形成首个可见的 schema 基线
  - 不选方案 C：项目路线图已明确 MySQL/Redis，且后续票务场景更贴近这组技术栈
