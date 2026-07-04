# Subagent 并行策略

## 为什么用 subagent

- 大文件阅读、测试日志、UI 影响面分析会污染主线程上下文。
- 后端、Android、Word renderer、文档/测试写入集天然可拆。
- 主线程负责需求边界、接口契约、集成和验收，不抢 worker 的局部工作。

## 使用规则

1. 每波最多 4 个 subagent。
2. 每个 agent 必须有唯一写入集；冲突文件归一个 owner。
3. 所有 agent 使用 `gpt-5.5`。
4. Scout 默认 read-only，只返回路径、行号、风险和建议，不写文件。
5. Worker 必须返回：状态、改了哪些文件、测试证据、冲突/风险。
6. 主线程合并后跑共享验证；worker 不自行宣布全项目完成。

## 本项目推荐自定义 agent

| Agent | 类型 | 建议 sandbox | 适用任务 |
|---|---|---|---|
| diary-backend-worker | worker | workspace-write | FastAPI API、schemas、LLM prompt、存储路径。 |
| diary-android-worker | worker | workspace-write | Kotlin UI、OkHttp client、layout/string。 |
| diary-renderer-worker | worker | workspace-write | python-docx、模板、变量校验。 |
| diary-qa-scout | explorer | read-only | 测试矩阵、失败日志、构建命令、回归风险。 |
| diary-docs-worker | worker | workspace-write | README、docs、API 说明、使用教程。 |

## 通用派发提示词

```text
按 docs/changes/2026-07-05-项目扩充规划/04-开发追踪.md 执行 Wave <N>。
Spawn one subagent per slice below, wait for all of them, then summarize results.
所有 subagent 使用 gpt-5.5。
每个 subagent 只能写自己的 Write set；如需要改共享文件，返回“需要主线程协调”，不要擅自修改。
返回格式：status / files changed / test evidence / risks。
```

## Worker Packet 模板

```text
Goal:
  <一句话目标>
Read:
  <允许读取的文件/目录；大文件只读相关行>
Write:
  <唯一写入集>
Do not touch:
  <明确禁止文件>
Dependencies:
  <依赖哪个模块/接口契约>
Verification:
  <本 worker 负责的最小检查>
Return:
  - status
  - files changed
  - test evidence
  - conflicts or risks
```

## Wave 1 具体包

### Backend Worker
```text
Goal: 实现报告生成后端骨架：日期范围聚合、report schema、生成 API、下载 API。
Read: intern-diary-server/app/main.py 相关 endpoint、schemas.py、paths.py、model_client.py、tests/test_generation.py。
Write: intern-diary-server/app/main.py, intern-diary-server/app/schemas.py, intern-diary-server/app/paths.py, intern-diary-server/app/model_client.py。
Do not touch: intern-diary-server/app/word_renderer.py, Android 文件, docs。
Dependencies: docs/changes/2026-07-05-项目扩充规划/07-API文档.md。
Verification: pytest tests/test_generation.py tests/test_entries.py。
```

### Renderer Worker
```text
Goal: 支持报告模板注册、变量填充、周报/月报/总结 Word 渲染。
Read: intern-diary-server/app/word_renderer.py, templates/, tests/test_word.py。
Write: intern-diary-server/app/word_renderer.py, templates/report_templates.json, templates/*.docx 或文档化内置模板。
Do not touch: main.py, schemas.py, Android 文件。
Dependencies: report metadata 草案。
Verification: pytest tests/test_word.py。
```

### Test Worker
```text
Goal: 为模板、日期范围聚合、报告 API 写最小后端测试。
Read: intern-diary-server/tests/, docs/changes/2026-07-05-项目扩充规划/08-测试用例.md。
Write: intern-diary-server/tests/test_reports.py。
Do not touch: app/ 生产代码, Android 文件。
Dependencies: Backend Worker 返回字段后可修正断言。
Verification: pytest tests/test_reports.py。
```
