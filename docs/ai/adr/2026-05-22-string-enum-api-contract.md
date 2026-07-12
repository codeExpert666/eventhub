# ADR：HTTP API 枚举入参使用字符串枚举名并禁止数字 ordinal

## 标题
HTTP API 枚举入参使用字符串枚举名并禁止 Jackson 数字 ordinal 反序列化

## 状态
- accepted

## 背景
当前用户状态更新接口通过 `@RequestBody UpdateUserStatusRequest` 接收 JSON，请求字段 `status` 是 `UserStatus` 枚举。Jackson 默认枚举反序列化支持字符串枚举名，也可能接受数字 ordinal，例如 `0` 对应枚举声明中的第一个常量。

数字 ordinal 对 Java 代码内部也许可用，但不适合作为 HTTP API 契约：

- 客户端无法从 `0` 看出业务含义。
- 数字和 Java 枚举声明顺序耦合。
- 后续新增或重排枚举常量时，旧数字可能指向不同业务状态。

同时，项目中 `@ModelAttribute` 查询参数和 `@RequestBody` 请求体走不同绑定链路，需要明确二者的设计边界。

## 决策
项目采用以下规则：

- HTTP JSON 请求体中的枚举字段使用字符串枚举名，例如 `"ENABLED"`、`"DISABLED"`。
- 全局开启 `spring.jackson.deserialization.fail-on-numbers-for-enums=true`，禁止数字 ordinal 反序列化枚举。
- 命令型 `@RequestBody` DTO 可以直接使用领域枚举字段，由 Jackson 负责字符串到枚举的转换。
- 查询型 `@ModelAttribute` DTO 如果需要更可控的业务错误提示，可以先用 `String + @Pattern` 承接原始查询参数，再转换为领域枚举。

## 备选方案
- 方案 1：接受 Jackson 默认数字 ordinal。
- 方案 2：所有 Web DTO 的枚举字段都改成 `String + @Pattern`。
- 方案 3：为每个枚举编写自定义反序列化器。
- 方案 4：开启大小写不敏感枚举绑定。

## 决策理由
- 选择字符串枚举名可以保持 API 可读、稳定，并与当前数据库字符串枚举存储方式一致。
- 禁止数字 ordinal 可以避免 Java 内部声明顺序泄漏为外部协议。
- `@RequestBody` 直接使用枚举字段，能让命令型接口的领域边界更清晰。
- `@ModelAttribute` 查询参数保留字符串承接能力，可以让非法值稳定进入 Bean Validation，返回更业务化的错误提示。
- 暂不使用自定义反序列化器，是因为当前只需要禁用数字 ordinal，Spring Boot 原生配置已能完成。
- 暂不开启大小写不敏感绑定，是为了让接口契约与 OpenAPI 示例、数据库枚举字符串保持一致。

## 影响
- 好处
  - API 契约更明确，客户端只能提交字符串枚举名。
  - 避免枚举顺序变化导致兼容性问题。
  - 请求体 DTO 和查询 DTO 的绑定职责更清晰。
  - 后续新增枚举请求字段默认继承相同安全约束。
- 代价
  - 传入 `0`、`1` 的旧客户端会从可能成功变为 400；当前项目尚未公开发布该数字契约，因此可接受。
  - 非法 `@RequestBody` 枚举值仍会进入请求体格式错误分支，当前不会返回字段级枚举错误。
- 后续可能需要调整的地方
  - 如果前端需要字段级非法枚举提示，可以在全局异常处理器中解析 Jackson `InvalidFormatException`，将字段路径映射到 `data.status`。
  - 如果后续引入多语言枚举编码，可以为枚举增加稳定 `code` 字段并用 `@JsonCreator` 显式解析，但仍不应使用 ordinal。
