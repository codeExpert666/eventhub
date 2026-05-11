# JwtProperties 使用 Lombok 收敛样板代码实现说明

## 1. 本次改动解决了什么问题
- `JwtProperties` 原先手写了 3 组 getter/setter，代码量较多，但不承载额外业务规则。
- 项目已经引入 Lombok，本次将这些 JavaBean 访问器交给 Lombok 生成，让配置类更聚焦 JWT 配置字段、默认值和校验规则。

## 2. 改动内容
- 新增了什么
  - 新增设计文档：`docs/ai/design/2026-05-12-jwt-properties-lombok-design.md`。
  - 新增本实现说明。
- 修改了什么
  - `JwtProperties` 新增 `@Getter` 和 `@Setter`。
  - 保留 `@ConfigurationProperties(prefix = "eventhub.security.jwt")`、`@Validated`、字段默认值和 Bean Validation 注解。
  - 增加注释说明配置属性对象为什么允许 setter，以及为什么不能简单使用 `@Data`。
- 删除了什么
  - 删除 `JwtProperties` 中手写的 `getIssuer`、`setIssuer`、`getSecret`、`setSecret`、`getAccessTokenTtl`、`setAccessTokenTtl`。

## 3. 为什么这样设计
- Spring Boot 当前使用 JavaBean 风格绑定 `@ConfigurationProperties`，需要 setter 写入配置值，因此 `@Setter` 在这个基础设施配置类中是合理的。
- 业务组件仍通过 getter 读取配置，Lombok 生成的方法签名与原手写方法一致，不改变调用方代码。
- 不使用 `@Data`，因为 `JwtProperties` 包含 `secret`，生成 `toString()` 会增加密钥被日志误输出的风险。
- 这与既有 Lombok ADR 的原则一致：优先使用小范围、低风险的 Lombok 注解，不把 Lombok 作为通用数据建模捷径。

## 4. 替代方案
- 方案 A：继续保留手写 getter/setter。
  - 优点是显式，缺点是样板代码继续占据配置类主要篇幅。
- 方案 B：使用 `@Data`。
  - 没有采用，因为它会额外生成 `toString`、`equals`、`hashCode`，对包含密钥字段的配置类不合适。
- 方案 C：改成构造器绑定的不可变配置类。
  - 没有采用，因为本次只是小范围重构样板代码；构造器绑定会改变配置绑定方式，需要额外验证更多启动和 profile 场景。

## 5. 测试与验证
- 已执行：
  - `mvn -q -Dtest=AuthIntegrationTest test`
- 验证结果：
  - 测试通过。
  - 该测试启动 Spring Boot 测试上下文，覆盖了 JWT/Auth 相关配置加载、登录、token 和鉴权链路。
- 未执行：
  - 未执行完整 `mvn test`，因为本次改动范围集中在 JWT 配置属性类，已优先执行相关集成测试。

## 6. 已知限制
- `@Setter` 仍会为所有字段生成 setter，这对配置绑定是必要的，但不应扩散为业务领域对象的默认写法。
- IDE 需要启用 Lombok annotation processing，否则可能出现编辑器误报；Maven 编译结果是最终准绳。
- 当前没有新增静态检查来限制 `@Data` 或滥用 `@Setter`，后续可以结合 Checkstyle 或 ArchUnit 补规则。

## 7. 对后续版本的影响
- 对简历可用版的价值：
  - 让 JWT 配置类更简洁，也展示了对 Lombok 使用边界和安全风险的判断。
- 对微服务 / 云原生演进的影响：
  - 配置属性类仍保持标准 Spring Boot 绑定方式，后续迁移到环境变量、配置中心或 Kubernetes Secret 时无需改变业务调用方。
