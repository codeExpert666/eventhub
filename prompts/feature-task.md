# 单个功能任务提示词模板

请使用 $backend-design-first skill 处理这个需求。

需求：
目标：让项目达到简历可用、可演示、可讲述的完成度

范围：
- 关键业务链路测试
- 测试数据准备
- README 运行说明
- Docker Compose 启动说明
- OpenAPI 使用说明
- `docs/interview` 项目讲述材料

非范围：微服务拆分、Kubernetes、Kafka

要求：明确测试层次、主链路覆盖、文档目录输出、面试表达重点与实施步骤

要求：
1. 先给出设计，不要直接开始改代码
2. 设计说明中必须包含：
   - 目标与非目标
   - 数据模型
   - API 设计
   - 状态流转
   - 并发 / 幂等 / 缓存 / 权限考虑
   - 测试策略
3. 然后再实现最小可用闭环
4. 改完后输出：
   - 本次解决的问题
   - 为什么这样设计
   - 替代方案
   - 风险与后续演进建议
5. 写文档前，先读取并遵循对应模板：
   - `docs/templates/design-template.md`
   - `docs/templates/implementation-note-template.md`
   - `docs/templates/adr-template.md`
6. 同步更新：
   - `docs/ai/design/`
   - `docs/ai/implementation/`
   - 如果有关键取舍，再补 `docs/ai/adr/`
7. 新增或调整模块传输对象时，必须遵循 `AGENTS.md` 的模块 DTO 组织规则：
   - 请求放在 `dto/request`，响应放在 `dto/response`
   - 查询型请求使用 `*QueryRequest`，仍属于 `dto/request`
   - 不新增模块根部 `vo` 或同级 `dto/query`
   - 设计文档中列出请求 DTO、响应 DTO 及其包路径
