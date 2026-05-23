# 阶段 0 设计概览

## 1. 阶段目标

阶段 0 负责搭建活动预约与票务平台的后端工程底座。它的成功标准不是实现业务功能，而是让后续业务模块可以在统一、可运行、可验证的工程约束上继续增长。

核心目标：

- 建立 Spring Boot 单体后端工程。
- 固化 `common / infra / modules` 的基础分层。
- 提供统一响应体、统一异常处理、参数校验和错误码规范。
- 建立 requestId 机制，让响应头、响应体和日志能够关联同一次请求。
- 接入 MySQL、Redis、Flyway、OpenAPI、Actuator。
- 提供 Dockerfile 与 Docker Compose，让本地环境可以一键启动。
- 用系统示例接口验证 Controller、Service、校验、异常和文档链路。

## 2. 范围与非范围

阶段 0 的范围：

- 工程骨架与基础目录。
- `dev / test / prod` 分环境配置。
- `GET /api/v1/system/ping` 和 `POST /api/v1/system/echo` 示例接口。
- 统一成功与失败响应。
- 全局异常映射。
- Flyway V1 基线迁移。
- Docker Compose 启动 MySQL、Redis 和后端应用。

阶段 0 的非范围：

- 注册登录、JWT、RBAC。
- 活动、场次、票种、订单和支付业务。
- 库存扣减、幂等、防重复提交和超卖控制。
- Kafka、微服务、Kubernetes、配置中心和完整可观测性体系。

## 3. 模块分层

阶段 0 采用单体单模块 Spring Boot 工程，按职责划分基础包：

```text
com.eventhub
├── common
│   ├── api
│   ├── constant
│   └── exception
├── infra
│   ├── logging
│   ├── openapi
│   └── web
└── modules
    └── system
        ├── controller
        ├── dto/request
        ├── service
        └── vo
```

这个分层体现了一个约束：业务模块只处理业务入口和业务逻辑，统一协议、异常处理、日志追踪、OpenAPI 等横切能力放在 `common` 和 `infra`。

## 4. API 设计

阶段 0 暴露少量基础接口，用来验证工程闭环：

- `GET /api/v1/system/ping`：验证应用启动、统一响应体和基础服务信息。
- `POST /api/v1/system/echo`：验证请求体绑定、Bean Validation 和统一异常处理。
- `GET /actuator/health`：验证应用与依赖组件健康状态。
- `GET /actuator/info`：验证应用基础信息端点。
- `GET /swagger-ui/index.html`：验证 OpenAPI 页面。
- `GET /v3/api-docs`：验证 OpenAPI JSON。

业务接口统一返回：

```json
{
  "code": "COMMON-000",
  "message": "OK",
  "data": {},
  "requestId": "request-id",
  "timestamp": "2026-04-26T18:22:03.119366+08:00"
}
```

Actuator 与 OpenAPI 端点保持框架原生响应，不强行包装，避免破坏工具生态。

## 5. 数据与配置设计

数据库只建立最小基线：

- Flyway 脚本：`V1__init_backend_foundation.sql`
- 基础表：`system_bootstrap_record`
- 目的：验证数据库连接、迁移流程和命名规范，而不是提前设计业务表。

配置分层采用：

- `application-dev.yml`：本地开发。
- `application-test.yml`：测试环境，使用 H2 降低外部依赖。
- `application-prod.yml`：容器/生产语义，关键数据库和 Redis 配置必须显式注入。

Docker Compose 中，`backend` 通过服务名 `mysql`、`redis` 访问依赖，不使用 `localhost`，提前建立容器网络下的正确配置习惯。

## 6. 关键流程

本地完整启动流程：

1. 执行 `docker compose up -d`。
2. Compose 启动 MySQL 和 Redis，并等待健康检查通过。
3. Compose 构建并启动后端应用容器。
4. 后端使用 `prod` profile，通过环境变量读取 MySQL 和 Redis 地址。
5. Spring Boot 启动后执行 Flyway 基线迁移。
6. 通过 health、ping、OpenAPI 等接口验证基础能力。

请求处理流程：

1. `RequestIdFilter` 生成或复用 `X-Request-Id`。
2. requestId 写入 MDC、request attribute 和响应头。
3. Controller 返回 `ApiResponse.success(...)` 或全局异常处理器返回 `ApiResponse.failure(...)`。
4. `ApiResponseRequestIdAdvice` 在 HTTP 响应写出前为统一响应体补充 requestId。
5. 请求结束后清理 MDC，避免线程复用导致日志上下文污染。

## 7. 设计价值

阶段 0 的价值在于它把后续所有业务都会依赖的底座能力前置：

- 后续用户、活动、订单接口可以直接复用统一响应和异常治理。
- 后续 RBAC 可以沿用 `/api/v1` 前缀、错误码和 401/403 响应结构。
- 后续订单、库存、支付等高风险链路可以借助 requestId 做日志排查。
- 后续微服务和云原生演进可以在 Dockerfile、Compose、Actuator、OpenAPI 的基础上继续扩展。
