# BusinessException 默认文案构造设计

## 1. 背景
- 当前 `BusinessException` 只支持 `(ErrorCode, String)` 构造方式。
- 当业务层仅想复用 `ErrorCode.defaultMessage` 时，需要重复写出 `errorCode.getDefaultMessage()`，增加样板代码，也容易造成同类异常文案不一致。

## 2. 目标
- 为 `BusinessException` 增加一个只接收 `ErrorCode` 的构造方法。
- 让业务代码在不需要覆盖提示文案时，可以直接使用错误码默认描述构造异常。
- 保持现有统一异常处理链路和响应结构不变。

## 3. 非目标
- 不调整 `GlobalExceptionHandler` 的处理逻辑。
- 不细化现有 `ErrorCode` 枚举颗粒度。
- 不引入新的异常层次结构或国际化文案体系。

## 4. 影响范围
- 涉及模块：`backend/common/exception`、`backend/common/error`
- 表 / 缓存 / 外部接口：无
- 受影响调用方：后续抛出 `BusinessException` 的业务服务代码

## 5. 领域建模
- 核心实体：`BusinessException`
- 实体关系：`BusinessException` 持有一个 `ErrorCode`，异常消息可来自错误码默认文案或调用方自定义文案。
- 关键状态：无额外状态流转，本次仅补充构造语义。

## 6. API 设计
- 对外接口：无 HTTP API 变更
- 代码级契约：
  - 新增 `BusinessException(ErrorCode errorCode)`，默认使用 `errorCode.getDefaultMessage()`
  - 保留 `BusinessException(ErrorCode errorCode, String message)`，用于覆盖默认文案
- 错误场景：
  - 当 `errorCode` 为空时，构造阶段直接抛出 `NullPointerException`

## 7. 数据设计
- 表结构调整：无
- 索引设计：无
- 唯一约束：无
- 数据一致性考虑：无持久化改动

## 8. 关键流程
- 正常流程：
  - 业务代码仅传入 `ErrorCode`
  - `BusinessException` 从 `ErrorCode` 读取默认文案并构造异常
  - `GlobalExceptionHandler` 读取异常中的错误码和消息，生成统一响应
- 异常流程：
  - 调用方传入 `null` 错误码
  - 构造器立即失败，避免产生缺少错误码的业务异常
- 状态流转：无

## 9. 并发 / 幂等 / 缓存
- 并发：无新增共享可变状态，不引入并发风险
- 幂等：无影响
- 缓存：无影响

## 10. 权限与安全
- 角色访问：无变化
- 鉴权与鉴别约束：无变化
- 安全考虑：空值保护可以避免异常处理阶段出现更隐蔽的空指针问题

## 11. 测试策略
- 单元测试：
  - 验证只传 `ErrorCode` 时会使用默认文案
  - 验证传入自定义文案时仍优先使用调用方消息
  - 验证两个构造方法都会拒绝 `null` 错误码
- 集成测试：本次不新增，现有异常处理集成链路不变
- 接口验证：无需新增 HTTP 验证
- 异常场景验证：覆盖空值输入

## 12. 风险与替代方案
- 当前方案风险：
  - 如果后续业务过度依赖默认文案，可能导致返回信息过于粗粒度。
- 备选方案：
  - 方案 A：维持只有 `(ErrorCode, String)` 构造方法
  - 方案 B：改为在 `GlobalExceptionHandler` 中兜底补默认文案
- 为什么不选备选方案：
  - 不选方案 A：调用方需要重复样板代码，使用体验较差
  - 不选方案 B：会让异常对象本身的语义不完整，也不利于单元测试直接验证异常内容
