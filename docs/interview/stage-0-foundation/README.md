# 阶段 0：项目骨架与工程基础

阶段 0 的核心目标是先建立一个可运行、可测试、可本地一键启动的 Spring Boot 单体后端基础工程，为后续注册登录、活动、订单、支付和通知模块提供稳定底座。

本阶段不追求业务复杂度，而是把后端项目最容易被忽略的工程约束提前落地：统一响应、统一异常、参数校验、错误码、requestId、分环境配置、OpenAPI、健康检查、Flyway、MySQL/Redis 接入、Dockerfile 和 Docker Compose。

## 文档清单

- [设计概览](./design-summary.md)：阶段 0 的目标、范围、模块分层、API 和关键流程。
- [实现复盘](./implementation-review.md)：实际完成内容、验证结果、已知限制和后续演进。
- [决策与取舍](./decisions-and-tradeoffs.md)：单体工程、Compose、统一响应、requestId 等关键决策。
- [简历表达](./resume-talking-points.md)：简历 bullet、项目介绍、STAR 示例和常见追问。

## 推荐阅读顺序

1. 先读 [设计概览](./design-summary.md)，建立阶段 0 的整体图。
2. 再读 [实现复盘](./implementation-review.md)，理解它如何从设计落到代码和验证。
3. 面试准备时重点读 [决策与取舍](./decisions-and-tradeoffs.md)。
4. 写简历或做自我介绍时直接使用 [简历表达](./resume-talking-points.md)。

## 资料来源

- [阶段 0 路线图](../../roadmap/stage-0-project-foundation.md)
- [后端基础工程设计文档](../../ai/design/2026-04-16-backend-foundation-design.md)
- [后端基础工程实现说明](../../ai/implementation/2026-04-16-backend-foundation-implementation.md)
- [基础阶段采用单体单模块 Spring Boot 工程 ADR](../../ai/adr/2026-04-16-backend-foundation-monolith.md)
- [阶段 0 基础工程缺口闭环设计](../../ai/design/2026-04-27-stage-0-foundation-gap-closure-design.md)
- [阶段 0 基础工程缺口闭环实现说明](../../ai/implementation/2026-04-27-stage-0-foundation-gap-closure-implementation.md)
- [dev/test/prod 与 Compose 默认启动后端 ADR](../../ai/adr/2026-04-27-stage-0-compose-dev-prod-profile.md)
- [requestId 响应注入重构设计](../../ai/design/2026-05-17-request-id-response-design.md)
- [requestId 响应注入重构实现说明](../../ai/implementation/2026-05-17-request-id-response-implementation.md)
- [requestId 由 HTTP 出口层注入统一响应体 ADR](../../ai/adr/2026-05-17-request-id-http-exit-advice.md)
