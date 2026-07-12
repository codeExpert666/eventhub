# 阶段 0：项目骨架与工程基础

返回主路线图：[活动预约与票务平台建议路线图](./event-booking-roadmap.md)

## 目标

创建一个可运行、可测试、可本地一键启动的模块化单体后端工程，为后续业务开发打基础。

## 范围

- Spring Boot 后端基础工程。
- Maven profile：dev / test / prod。
- 统一响应体。
- 统一异常处理。
- 参数校验。
- 错误码规范。
- 基础日志配置。
- requestId / traceId 透传。
- OpenAPI 文档。
- Actuator 健康检查。
- Dockerfile。
- Docker Compose 启动 MySQL、Redis、应用。
- Flyway 初始化 migration。
- 基础 README。

## 非范围

- 注册登录。
- RBAC。
- 活动、订单、支付等业务逻辑。
- Kafka、微服务、Kubernetes。

## 推荐实现

### 目录结构

```text
eventhub
├── src
│   ├── main
│   │   ├── java/com/eventhub
│   │   │   ├── EventhubApplication.java
│   │   │   ├── common
│   │   │   │   ├── api（接口返回格式、分页格式、错误码协议相关的通用类）
│   │   │   │   └── exception（全局异常体系）
│   │   │   ├── modules
│   │   │   │   └── system（系统基础能力）
│   │   │   └── infra
│   │   │       ├── openapi（OpenAPI / Swagger 文档配置）
│   │   │       └── logging（日志、审计切面、requestId 注入等技术实现）
│   │   └── resources
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       └── db/migration
│   └── test
│       ├── java
│       └── resources
│           └── application-test.yml
├── Dockerfile
├── lombok.config
├── pom.xml
├── docs
│   ├── ai
│   │   ├── design
│   │   ├── implementation
│   │   └── adr
│   ├── interview
│   ├── roadmap
│   └── templates
├── docker-compose.yml
└── README.md
```

以下是每个模块内部的标准分层，对于 system 模块来说，不涉及的可直接舍弃：

```text
modules/system
├── controller
├── service
│   └── impl（简单 service 可不写接口）
├── domain（service 逻辑复杂时才考虑此分层）
├── mapper
├── entity
├── dto
│   ├── request
│   └── response
├── converter
├── enums
└── exception
```

### 统一响应体

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {},
  "requestId": "...",
  "timestamp": "2026-04-26T18:22:03.119366+08:00"
}
```

### 统一错误码

```text
SUCCESS
VALIDATION_ERROR
BUSINESS_ERROR
NOT_FOUND
INTERNAL_ERROR
```

### 基础接口

```text
GET /api/v1/system/ping
POST /api/v1/system/echo
GET /actuator/health
GET /actuator/info
GET /swagger-ui/index.html
GET /v3/api-docs
```

## 交付物

```text
src/main/java 基础包结构
application-dev.yml
application-test.yml
application-prod.yml
Dockerfile
docker-compose.yml
Flyway V1__init_backend_foundation.sql
README.md
相关设计、实现、ADR文档
```

## 验收标准

- `docker compose up -d` 后 MySQL、Redis、应用可以启动。
- 应用能连接 MySQL 和 Redis。
- Flyway 能自动执行初始化脚本。
- 健康检查接口返回正常。
- OpenAPI 页面可访问。
- 参数校验和异常返回格式统一。

## 测试点

- ping、echo 接口正常。
- 参数校验异常返回统一格式。
- 未知异常返回统一格式。
- Flyway migration 在测试环境可执行。

## 面试表达

> 项目从一开始就建立统一响应、统一异常、参数校验、分环境配置、OpenAPI、健康检查和 Docker Compose 本地环境，保证后续业务开发具有稳定工程基础。
