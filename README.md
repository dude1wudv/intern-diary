# 实习日记助手（Intern Diary）

> 实习的时候顺手用手机采集素材（比如一句话，图片等），然后在后端整理内容，最后可以自动生成可下载的实习日记 Word 文档。

[English README](README.en.md)

## 项目简介

实习日记助手是一套“Android 客户端 + FastAPI 后端”的个人实习记录系统：

- 手机端记录当天文字、图片和补充说明。
- 后端按日期归档素材，并调用 OpenAI-compatible LLM 整理内容、生成日记草稿。
- 网页控制台可在浏览器中管理素材、预览草稿、下载 Word。
- Android 设置页支持填写自建后端地址和 API Token。
- LLM 服务商地址、模型和 API Key 可在后端配置。

## 用户对象

- 实习生：每天快速整理实习日记。
- 个人/小团队：自建轻量 AI 文档生成服务。
- 开发者：参考 Android + FastAPI + LLM 的端到端项目结构。

## 功能亮点

| 模块 | 能力 |
| --- | --- |
| Android App | 输入服务器地址/API Token、提交文字、上传图片、AI 助手、查看历史 |
| FastAPI 后端 | Bearer Token 鉴权、按日期存储、整理素材、生成日记/周报/月报/实习总结、Word 渲染 |
| 报告模板 | 内置周报、月报、实习总结模板，可通过 Report API 选择模板并导出 Markdown/Word |
| 网页控制台 | `/console` 管理当天素材、触发日记或报告生成、下载 Markdown/Word |
| LLM 配置 | 后端通过环境变量配置 `OPENAI_BASE_URL` / `OPENAI_API_KEY` / `OPENAI_MODEL` |
| 部署 | 支持本地运行、Docker、反向代理 HTTPS |

## 技术栈

- Android：Kotlin、AndroidX、Material Components、OkHttp、ViewBinding
- 后端：Python、FastAPI、Pydantic、httpx、python-docx、Uvicorn
- AI：OpenAI-compatible Chat Completions 接口
- 部署：Docker / docker compose / HTTPS 反向代理

## 快速开始

### 1. 启动后端

```bash
cd intern-diary-server
python -m venv .venv
. .venv/bin/activate          # Windows: .venv\Scripts\activate
pip install -e .[test]
cp .env.example .env
uvicorn app.main:app --host 0.0.0.0 --port 8088
```

打开健康检查：

```bash
curl http://127.0.0.1:8088/health
```

### 2. 配置后端 LLM

编辑 `intern-diary-server/.env`：

```env
API_TOKEN=replace-with-a-long-random-token
OPENAI_API_KEY=your-llm-api-key
OPENAI_BASE_URL=https://api.openai.com/v1
OPENAI_MODEL=gpt-5.5
VISION_ENABLED=0
CODEX_ENABLED=0
```

`OPENAI_BASE_URL` 可换成任意 OpenAI-compatible 服务商地址。不要把真实 Key 写进 Android 或提交到 Git。

### 3. 构建 Android APK

```bash
cd intern-diary-android
./gradlew assembleDebug
```

APK 输出位置：

```text
intern-diary-android/app/build/outputs/apk/debug/app-debug.apk
```

### 4. 手机端填写配置

安装 APK 后进入“设置”：

1. **服务器地址**：你的后端 HTTPS 地址，例如 `https://your-diary.example.com`。
   - Android 模拟器访问本机后端可用 `http://10.0.2.2:8088`。
2. **API Token**：后端 `.env` 中的 `API_TOKEN`。
3. 点击“测试连接”。

## 中文文档

- [部署教程](docs/deployment.zh-CN.md)
- [使用方法](docs/usage.zh-CN.md)
- [后端 LLM 服务商配置](docs/llm-provider.zh-CN.md)
- [架构说明](docs/architecture.zh-CN.md)
- [API 说明](docs/api.zh-CN.md)
- [Android 构建与打包](docs/android-build.zh-CN.md)
- [开发者指南](docs/developer-guide.zh-CN.md)
- [发布流程](docs/release-process.zh-CN.md)

## 仓库关键词

`android` · `kotlin` · `fastapi` · `internship` · `diary` · `llm` · `openai-compatible` · `python-docx` · `self-hosted` · `document-generation`

## 工作流程

1. Fork / clone 仓库。
2. 后端复制 `.env.example` 为 `.env` 并填写 Token 和 LLM 配置。
3. 本地运行 `pytest` 和 `./gradlew assembleDebug`。
4. 部署后端到服务器并配置 HTTPS。
5. 构建 APK，手机端填写服务器地址和 Token。
6. 发版时按 [发布流程](docs/release-process.zh-CN.md) 创建 Tag / GitHub Release 并上传 APK。

## 实习成果生成器

- 报告类型：`weekly` 周报、`monthly` 月报、`internship_summary` 实习总结。
- Report API：`GET /api/report-templates` 查询模板，`POST /api/actions/generate-report` 按日期范围生成报告。
- Web Console：打开 `/console`，在 Report 区选择类型、日期范围和模板，生成后可下载 Markdown / Word。
- Android：当天页“生成周报”入口会按当前日期所在周生成周报，并自动下载 Word 到系统下载目录。

## 测试

后端：

```bash
cd intern-diary-server
pytest
```

Android 编译检查：

```bash
cd intern-diary-android
./gradlew assembleDebug
```

## 安全边界

- 不提交 `.env`、真实 API Key、数据库、运行数据、APK 签名密钥。
- Android 只保存后端服务器地址和后端 API Token。
- LLM 服务商 Key 只在后端配置。
- 公开部署前必须替换默认 `API_TOKEN`。

## 版本

当前发布版本：`v2.0.0`。

更新记录见 [CHANGELOG.md](CHANGELOG.md)，规划见 [ROADMAP.md](ROADMAP.md)。

## License

本项目使用 [MIT License](LICENSE)。

