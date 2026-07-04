# GitHub 调研：日记扩充与 Codex subagent 实践

> 本文为 AI 查证结论，非用户原话。调研时间：2026-07-05。

## 同类日记项目可吸收点

| 项目 | 观察 | 对 diary-open-source 的取舍 |
|---|---|---|
| Diarum | 自托管日记，强调 PWA/离线/AI/RAG 周报月报。 | 吸收“周报/月报/实习报告”方向；第一阶段不做完整 PWA。 |
| Memos | 快速捕获、轻量自托管、API 完整。 | 后续做“碎片素材箱”；A 主线暂不铺开。 |
| DailyTxT | 加密、多用户、标签、搜索、统计、模板。 | 后续 D 路线吸收标签/搜索/模板；第一阶段只做模板与报告。 |
| Journiv | prompt-based journaling、media uploads、analytics、advanced search。 | 后续做提示词式引导填写；第一阶段只做报告生成提示词。 |
| Memex | 本地优先 AI journal，多 Agent 整理碎片成洞察。 | 后续做“实习经历问答/长期反思”；第一阶段做周/月/总结聚合。 |
| Moodiary / June / StoryPad | 多媒体、情绪、地点、WebDAV、回忆、语音等完整移动体验。 | 不直接合并代码；作为中后期 Android 体验参考。 |

## Codex / subagent 热门项目调研

| 项目 | GitHub 热度快照 | 可借鉴做法 |
|---|---:|---|
| bmad-code-org/BMAD-METHOD | 约 50k stars | 用 PM/Architect/Dev/QA 等角色和结构化 agile 工作流，把“先规划再实现”固定成流程。 |
| wshobson/agents | 约 37k stars | 把 agents / skills / commands 做成可安装 marketplace；强调单一来源、按 harness 生成原生 artifact。 |
| SuperClaude-Org/SuperClaude_Framework | 约 23k stars | 用命令、persona、规划/研究/测试/复盘命令体系组织复杂工作。 |
| VoltAgent/awesome-codex-subagents | 约 5.5k stars | Codex 原生 `.toml` subagent 集合；按职责分类，区分 read-only 与 workspace-write agent。 |

## 对本项目的落地规则

1. **Codex subagent 不是自动触发**：实现任务里必须明确写“spawn N agents / one agent per slice / wait for all”。
2. **主线程只做 PM + 集成**：保留需求、边界、集成、验收；把大文件阅读、测试日志、局部实现交给 subagent。
3. **每个 subagent 有唯一写入集**：Android、Backend API、Word renderer、测试文档分开，不让两个 agent 改同一文件。
4. **最多 4 个并行 worker / wave**：超过 4 个会增加冲突和汇总成本。
5. **本仓库模型约束**：虽然外部项目/官方文档会按成本选择不同模型，本仓库统一要求主线程和所有 subagent 使用 `gpt-5.5`。
6. **先读后写**：每个 worker 先返回“将改哪些文件 + 风险”，再执行；大文件由 scout 用 line range 摘要。

## 来源

- OpenAI Codex Subagents：`https://developers.openai.com/codex/subagents`
- OpenAI Codex Subagent concepts：`https://developers.openai.com/codex/concepts/subagents`
- OpenAI Codex Skills：`https://developers.openai.com/codex/skills`
- OpenAI AGENTS.md：`https://developers.openai.com/codex/guides/agents-md`
- `https://github.com/bmad-code-org/BMAD-METHOD`
- `https://github.com/wshobson/agents`
- `https://github.com/SuperClaude-Org/SuperClaude_Framework`
- `https://github.com/VoltAgent/awesome-codex-subagents`
