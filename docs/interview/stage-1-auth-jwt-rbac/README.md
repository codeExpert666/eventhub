# 阶段 1：注册登录、JWT 与 RBAC

阶段 1 的核心目标是让 EventHub 从“可运行的后端骨架”进入第一个真实业务基础设施：身份认证、登录态识别和角色权限边界。

本阶段围绕注册、登录、JWT access token、当前用户接口、USER / ADMIN 角色模型、Spring Security 访问控制和管理员用户接口完成最小可用闭环。它为后续活动管理、订单创建、支付回调、操作日志和用户维度审计提供统一的当前用户上下文。

## 文档清单

- [设计概览](./design-summary.md)：从面试视角总结阶段 1 的目标、模块边界、数据模型、API 和安全流程。
- [实现复盘](./implementation-review.md)：复盘实际完成内容、测试验证、已知限制和后续演进。
- [决策与取舍](./decisions-and-tradeoffs.md)：整理 JWT、RBAC、Spring Security、数据库约束和生产暴露面的关键取舍。
- [简历表达](./resume-talking-points.md)：沉淀简历 bullet、项目介绍、STAR 示例和常见追问。

## 推荐阅读顺序

1. 先读 [设计概览](./design-summary.md)，建立认证、鉴权、用户角色模型的整体图。
2. 再读 [实现复盘](./implementation-review.md)，理解它如何落到代码、SQL、测试和当前限制。
3. 面试追问时重点读 [决策与取舍](./decisions-and-tradeoffs.md)。
4. 写简历或准备自我介绍时直接使用 [简历表达](./resume-talking-points.md)。

## 阶段关键词

- 注册与登录
- BCrypt 密码哈希
- JWT access token
- 最小 JWT claims
- 当前认证主体
- Spring Security Filter Chain
- USER / ADMIN RBAC
- 401 认证失败与 403 授权失败
- 用户禁用后旧 token 失效
- 管理员用户分页、筛选与状态管理
- 生产环境 OpenAPI 暴露面收敛

## 资料来源

- [阶段 1 路线图](../../roadmap/stage-1-auth-jwt-rbac.md)
- [阶段 1 认证、JWT 与 RBAC 设计](../../ai/design/2026-04-27-stage-1-auth-jwt-rbac-design.md)
- [阶段 1 认证、JWT 与 RBAC 实现说明](../../ai/implementation/2026-04-27-stage-1-auth-jwt-rbac-implementation.md)
- [阶段 1 使用自定义 JWT Filter 并在认证时校验用户状态 ADR](../../ai/adr/2026-04-27-stage-1-jwt-security-boundary.md)
- [Auth Security 边界重构设计](../../ai/design/2026-04-29-auth-security-refactor-design.md)
- [Auth Security 边界重构实现说明](../../ai/implementation/2026-04-29-auth-security-refactor-implementation.md)
- [拆分 auth 安全基础设施并采用最小 JWT ADR](../../ai/adr/2026-04-29-auth-security-boundary-refactor.md)
- [Security Matcher 显式化设计](../../ai/design/2026-05-11-explicit-security-matchers-design.md)
- [Security Matcher 显式化实现说明](../../ai/implementation/2026-05-11-explicit-security-matchers-implementation.md)
- [安全认证企业级分层重构设计](../../ai/design/2026-05-16-auth-security-enterprise-layering-design.md)
- [安全认证企业级分层重构实现说明](../../ai/implementation/2026-05-16-auth-security-enterprise-layering-implementation.md)
- [安全认证代码按 infra.security 与 modules.auth 分层 ADR](../../ai/adr/2026-05-16-auth-security-enterprise-layering.md)
- [UserEntity 持久化模型收敛设计](../../ai/design/2026-05-14-user-entity-persistence-model-design.md)
- [RoleEntity 持久化模型重构设计](../../ai/design/2026-05-14-role-entity-persistence-model-design.md)
- [用户角色绑定影响行数校验设计](../../ai/design/2026-05-15-user-role-binding-affected-rows-design.md)
- [管理员用户分页查询设计](../../ai/design/2026-05-18-admin-user-pagination-design.md)
- [管理员用户分页查询实现说明](../../ai/implementation/2026-05-18-admin-user-pagination-implementation.md)
- [生产环境 OpenAPI 暴露面加固 ADR](../../ai/adr/2026-05-23-prod-openapi-hardening.md)
