# 生产环境 OpenAPI 暴露面加固实现说明

## 1. 本次改动解决了什么问题

本次改动解决了生产环境默认暴露 Swagger UI / OpenAPI 文档端点的问题。

在开发和测试阶段，`/v3/api-docs` 与 Swagger UI 能显著提升接口联调、学习演示和回归测试效率。但生产环境继续暴露这些端点，会让接口路径、请求响应模型、枚举字段、管理端入口等信息更容易被外部扫描和枚举。

调整后，`dev` / `test` 仍保留 OpenAPI 能力，`prod` 默认关闭 OpenAPI JSON 与 Swagger UI，并且 Security 不再公开放行这些路径。

## 2. 改动内容
- 新增了什么
  - 新增 `docs/ai/design/2026-05-23-prod-openapi-hardening-design.md`。
  - 新增 `docs/ai/adr/2026-05-23-prod-openapi-hardening.md`。
  - 新增 `OpenApiProductionSecurityTest`，使用 `prod` profile 验证生产文档路径的安全行为。
- 修改了什么
  - `application.yml` 显式声明 `springdoc.api-docs.enabled=true` 与 `springdoc.swagger-ui.enabled=true`，作为非生产默认值。
  - `application-prod.yml` 覆盖为 `springdoc.api-docs.enabled=false` 与 `springdoc.swagger-ui.enabled=false`。
  - `SecurityConfig` 读取 springdoc 开关，只在对应能力启用时才公开放行 `/v3/api-docs` 和 Swagger UI 路径。
  - `README.md` 标注 Swagger UI / OpenAPI JSON 是 dev/test 联调入口，prod 默认关闭。
- 删除了什么
  - 没有删除依赖或 Controller 注解。
  - 没有删除非生产环境的 OpenAPI 暴露能力。

## 3. 为什么这样设计
- 关键设计原因
  - 只关闭 Swagger UI 不够完整，因为 `/v3/api-docs` JSON 仍然会暴露机器可读的接口契约。
  - 只依赖 springdoc 关闭端点还不够清晰，因为 Security 白名单仍可能让文档路径作为公开资源进入后续处理链路。
  - 让 Security 读取 `springdoc.*.enabled`，可以减少“端点开关”和“安全白名单”两套配置漂移。
  - 保留 dev/test 的文档能力，符合当前学习型项目的联调和演示需求。
- 与项目当前阶段的匹配点
  - 当前仍是单体后端，按 profile 控制暴露面是最小可用闭环。
  - 不引入网关、管理端口或额外权限模型，避免为了一个文档端点过早复杂化。
  - 新增测试直接覆盖生产 profile 行为，后续升级 springdoc / Spring Security 时能及时发现回归。

## 4. 替代方案
- 方案 A：只关闭 `springdoc.swagger-ui.enabled`
  - 没有采用，因为 OpenAPI JSON 仍会暴露完整接口契约。
- 方案 B：生产保留文档端点，但要求 JWT 登录
  - 没有采用，因为文档端点不是生产业务能力；默认关闭更符合最小暴露原则。
- 方案 C：完全移除 springdoc 依赖
  - 没有采用，因为开发、测试、学习演示和接口回归仍需要 OpenAPI。
- 方案 D：引入网关、内网 IP 白名单或独立管理端口
  - 没有采用，因为当前项目还处于单体学习阶段，这些方案更适合后续云原生或多环境部署阶段。

## 5. 测试与验证
- 已执行：
  - `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn -q -Dtest=OpenApiProductionSecurityTest,SystemControllerTest test`
  - `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn -q test`
  - `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn -q -DskipTests package`
  - `git diff --check`
- 验证结果：
  - 定向测试通过。
  - 全量测试通过：53 个测试，0 failure，0 error，0 skipped。
  - 跳过测试打包通过。
  - diff 空白检查通过。
- 覆盖场景：
  - `test` profile 下 `/v3/api-docs` 仍返回 200，并包含系统接口文档。
  - `prod` profile 下未认证访问 `/v3/api-docs` 返回 401。
  - `prod` profile 下未认证访问 `/swagger-ui.html` 返回 401。
  - `prod` profile 下 `/actuator/health` 仍返回 200，但不返回 `components` 详情。

## 6. 已知限制
- 当前只是默认生产关闭，并未提供单独的“受控生产文档入口”。
- 如果生产临时需要查看文档，需要通过外部配置显式开启，或在受控的非生产环境复现问题。
- 当前没有引入网关、VPN、IP 白名单或独立 management 端口；这些更适合后续云原生部署阶段统一设计。
- 对携带合法 JWT 的生产文档路径，本次依赖 springdoc 端点关闭来避免返回文档内容；未认证请求会先被 Security 拦截为 401。

## 7. 对后续版本的影响
- 对简历可用版的价值
  - 能展示“开发便利”和“生产安全”按 profile 隔离的工程意识。
  - 能解释 Swagger UI、OpenAPI JSON、Security 白名单之间的区别和组合风险。
  - 新增生产 profile 测试，让安全配置不只停留在 YAML 注释层面。
- 对微服务 / 云原生演进的影响
  - 后续如果引入 API 网关，可以把 OpenAPI 聚合文档放到受控的内部入口。
  - 后续如果引入 Kubernetes，可以把生产 profile smoke test 纳入镜像发布流水线。
  - 后续如果拆分服务，当前“生产默认不暴露文档”的策略可以作为各服务共同基线。
