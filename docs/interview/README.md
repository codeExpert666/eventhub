# 面试复盘文档

`docs/interview` 用于沉淀适合简历、面试和阶段复盘的高密度材料。

它不替代 `docs/ai`。`docs/ai` 继续保留每次协作过程中的设计、实现和 ADR 留痕；`docs/interview` 只做阶段级整理，把分散的过程文档提炼成少量可复用的表达材料。

## 文档定位

- 面向读者：项目作者、面试官视角下的自己、后续简历整理。
- 关注重点：阶段目标、工程取舍、实现闭环、风险意识、可讲述成果。
- 不做的事：不记录每次小改动，不替代代码注释，不重复粘贴 `docs/ai` 的完整过程。

## 目录约定

```text
docs/interview/
├── README.md
├── interview-docs-design.md
├── stage-0-foundation/
│   ├── README.md
│   ├── design-summary.md
│   ├── implementation-review.md
│   ├── decisions-and-tradeoffs.md
│   └── resume-talking-points.md
└── stage-1-auth-jwt-rbac/
    ├── README.md
    ├── design-summary.md
    ├── implementation-review.md
    ├── decisions-and-tradeoffs.md
    └── resume-talking-points.md
```

每个阶段原则上控制在 4 到 5 篇核心文档：

- `README.md`：阶段索引、阅读顺序、资料来源。
- `design-summary.md`：阶段设计概览，解释目标、范围、模块和关键流程。
- `implementation-review.md`：阶段实现复盘，说明完成了什么、如何验证、有哪些遗留边界。
- `decisions-and-tradeoffs.md`：关键技术决策与取舍，便于面试追问时展开。
- `resume-talking-points.md`：简历 bullet、项目介绍、STAR 示例和常见问答。

## 使用方式

整理简历时，优先阅读每个阶段的 `resume-talking-points.md`，再回到 `design-summary.md` 找技术深度。准备面试追问时，重点阅读 `decisions-and-tradeoffs.md` 和 `implementation-review.md`。

当需要证明某个结论的来源时，从阶段目录的 `README.md` 回链到 `docs/ai` 或 `docs/roadmap`。

## 维护规则

- 阶段级文档不使用日期前缀，避免它变成另一套流水账。
- 单阶段只保留少数主文档；如果内容明显膨胀，优先重写提炼，而不是新增文件。
- `docs/interview` 写结论和表达，`docs/ai` 保存过程和证据。
- 进入新阶段后，先创建阶段目录和 `README.md`，再逐步补齐设计、实现、决策和简历表达。
- 阶段复盘可以引用历史文档，但不要大段复制历史文档。
