# docs/interview 轻量设计文档

## 1. 背景

当前项目是简历型学习项目，`docs/ai` 已经持续记录设计、实现和 ADR。随着阶段推进，过程文档会越来越多，它们适合追溯项目演进，但不适合直接用于简历整理和面试复盘。

因此需要新增一层阶段级复盘文档：保留历史证据链不动，同时把每个阶段沉淀成少量高密度材料。

## 2. 目标

- 建立 `docs/interview`，专门承载阶段级简历和面试复盘材料。
- 每个阶段只保留少数主文档，降低后续整理成本。
- 让阶段成果可以被快速转化为简历 bullet、项目介绍和面试回答。
- 保持 `docs/ai` 的完整性，不删除、不改写历史过程文档。

## 3. 非目标

- 不把 `docs/ai` 迁移到 `docs/interview`。
- 不为每一次小修改创建面试文档。
- 不在 `docs/interview` 里重复保存完整设计过程。
- 不把阶段复盘写成对外宣传稿，仍然保留真实的取舍、限制和后续演进点。

## 4. 信息架构

`docs/interview` 采用“总索引 + 阶段目录”的结构：

```text
docs/interview/
├── README.md
├── interview-docs-design.md
└── stage-n-topic/
    ├── README.md
    ├── design-summary.md
    ├── implementation-review.md
    ├── decisions-and-tradeoffs.md
    └── resume-talking-points.md
```

阶段目录命名使用 `stage-序号-主题`，例如 `stage-0-foundation`、`stage-1-auth-jwt-rbac`。

## 5. 文档职责

- `README.md`：说明阶段目标、材料清单和推荐阅读顺序。
- `design-summary.md`：从面试视角总结设计，而不是复刻原始设计文档。
- `implementation-review.md`：复盘实际完成内容、验证方式、边界和后续演进。
- `decisions-and-tradeoffs.md`：把关键 ADR 和实现取舍整理成可被追问的表达。
- `resume-talking-points.md`：沉淀简历 bullet、项目介绍、STAR 示例和问答。

## 6. 与 docs/ai 的关系

`docs/ai` 是过程层，负责回答“当时为什么这么做、怎么做、做完如何验证”。
`docs/interview` 是提炼层，负责回答“这段经历怎么讲、怎么证明、怎么体现能力”。

每篇阶段复盘文档可以引用 `docs/ai`，但不复制完整内容。这样既保留证据链，又避免面试材料继续膨胀。

## 7. 阶段复盘流程

1. 读取阶段 roadmap，确认阶段目标和非目标。
2. 读取阶段内的核心设计、实现说明和 ADR。
3. 提炼阶段成果、关键取舍、验证方式和遗留风险。
4. 写入阶段目录下的少数主文档。
5. 回到当前开发分支合并文档分支，让后续阶段能继承这套结构。

## 8. 风险与约束

- 风险：如果阶段复盘写得过细，`docs/interview` 会变成第二个 `docs/ai`。
- 约束：每个阶段优先控制在 5 篇以内。
- 风险：如果只写成果不写限制，面试追问时容易显得不真实。
- 约束：每个阶段必须保留“已知限制”和“后续演进点”。
