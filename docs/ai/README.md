# AI 协作文档目录说明

这个目录不是为了“堆文档”，而是为了把学习价值固化下来。

## 推荐子目录

- `design/`
  - 某个需求开始前的设计说明
- `implementation/`
  - 某次代码实现后的说明
- `adr/`
  - 关键技术决策记录

## 命名建议

统一使用：

```text
YYYY-MM-DD-主题.md
```

例如：

- `2026-04-06-user-auth-design.md`
- `2026-04-07-user-auth-implementation.md`
- `2026-04-10-inventory-lock-strategy.md`

## 写作原则

1. 文档服务于后续复盘和面试表达
2. 设计文档要讲清楚“为什么”
3. 实现文档要讲清楚“怎么做”和“还没做什么”
4. ADR 要讲清楚取舍与决策理由


## 模板约定

写文档时优先参考：

- 设计文档：`docs/templates/design-template.md`
- 实现说明：`docs/templates/implementation-note-template.md`
- ADR：`docs/templates/adr-template.md`
