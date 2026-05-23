# ADR：生产环境默认关闭 OpenAPI 与 Swagger UI

## 标题
生产环境默认关闭 OpenAPI 与 Swagger UI

## 状态
- accepted

## 背景
当前项目通过 springdoc 提供 OpenAPI JSON 和 Swagger UI，方便本地开发、接口联调和学习演示。升级到 Spring Boot 3.5 / springdoc 2.8 后，springdoc 明确提示生产环境可关闭文档端点。

对活动预约与票务平台来说，生产环境公开接口文档会暴露 API 路径、请求响应模型、枚举值、管理端入口等信息。虽然这些信息不等同于漏洞，但会降低攻击者枚举系统能力的成本，也不符合生产最小暴露原则。

## 决策
生产环境采用默认关闭策略：

- `prod` profile 设置 `springdoc.api-docs.enabled=false`。
- `prod` profile 设置 `springdoc.swagger-ui.enabled=false`。
- Spring Security 根据 springdoc 开关决定是否公开放行文档路径。
- `dev` / `test` 继续启用 OpenAPI 与 Swagger UI，保持学习、联调和自动化测试体验。

## 备选方案
- 方案 1：所有环境继续公开 OpenAPI 与 Swagger UI。
- 方案 2：生产只关闭 Swagger UI，保留 `/v3/api-docs`。
- 方案 3：生产保留 OpenAPI 与 Swagger UI，但要求 JWT 登录。
- 方案 4：从项目中完全移除 springdoc 依赖。

## 决策理由
- 方案 1 不符合生产最小暴露原则。
- 方案 2 仍会暴露机器可读的接口契约，安全收益不足。
- 方案 3 虽然比公开访问更安全，但接口文档不是生产业务能力；默认关闭更稳妥，也避免把文档访问纳入正式权限模型。
- 方案 4 会影响开发、测试和简历演示阶段的接口可视化能力，当前收益不如按环境关闭。
- 当前方案改动小、行为清晰、便于测试，并且保留外部配置覆盖空间。

## 影响
- 好处：
  - 生产默认不暴露接口契约和 Swagger UI 静态资源。
  - 非生产环境仍保持 OpenAPI 联调能力。
  - Security 白名单与 springdoc 开关联动，降低配置漂移风险。
- 代价：
  - 生产临时排障时不能直接依赖 Swagger UI，需要通过受控环境、日志、监控或临时外部配置处理。
  - 增加一组生产 profile 集成测试，测试启动配置需要显式覆盖生产数据库和 Redis 参数。
- 后续可能需要调整的地方：
  - 如果未来引入网关或管理后台，可以把文档只暴露到内网、VPN 或独立管理端口。
  - 如果引入 CI/CD 环境，可补充镜像启动级别的生产 profile smoke test。
