# 开发者指南

## 目录结构

```text
intern-diary-android/   Android Kotlin 客户端
intern-diary-server/    FastAPI 后端
templates/              Word 模板
docs/                   文档
```

## 后端开发

```bash
cd intern-diary-server
python -m venv .venv
. .venv/bin/activate
pip install -e .[test]
pytest
```

核心模块：

- `app/main.py`：HTTP API 和网页控制台。
- `app/auth.py`：Bearer Token 鉴权。
- `app/model_client.py`：LLM 调用和无 Key 占位输出。
- `app/word_renderer.py`：日记和报告 Word 渲染、报告模板加载。
- `app/paths.py`：运行数据路径，包括按 `report_id` 保存的报告目录。
- `app/schemas.py`：日记、AI 助手和 Report API 入参模型。

Report API：

- `GET /api/report-templates`：读取 `templates/report_templates.json`。
- `POST /api/actions/generate-report`：按 `type`、`start_date`、`end_date` 生成报告草稿和 Word。
- `GET /api/reports/{report_id}`：读取报告元数据。
- `GET /api/reports/{report_id}/draft`：下载 Markdown。
- `GET /api/reports/{report_id}/files/report.docx`：下载 Word。

## Android 开发

```bash
cd intern-diary-android
./gradlew assembleDebug
```

核心模块：

- `SettingsStore.kt`：保存服务器地址、API Token、主题。
- `SettingsSheet.kt`：设置页。
- `ApiClient.kt`：HTTP 请求，包括生成周报和下载报告 Word。
- `TodayActivity.kt`：主页面交互，包括“生成周报”入口。

## 提交前检查

```bash
cd intern-diary-server && pytest
cd ../intern-diary-android && ./gradlew assembleDebug
```

## 分支建议

- `main`：稳定分支。
- `feature/<name>`：功能开发。
- `fix/<name>`：问题修复。

提交信息建议使用 Conventional Commits：

```text
feat(android): add configurable server url
fix(server): reject invalid edit target
chore(ci): add backend and android checks
```
