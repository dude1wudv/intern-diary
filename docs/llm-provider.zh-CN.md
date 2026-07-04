# 后端 LLM 服务商配置

本项目只要求服务商兼容 OpenAI Chat Completions 风格接口。

## 环境变量

在 `intern-diary-server/.env` 中配置：

```env
OPENAI_API_KEY=your-llm-api-key
OPENAI_BASE_URL=https://api.openai.com/v1
OPENAI_MODEL=gpt-5.5
```

说明：

- `OPENAI_API_KEY`：服务商 API Key。
- `OPENAI_BASE_URL`：服务商 OpenAI-compatible API 根地址。
- `OPENAI_MODEL`：模型名。

## 为什么不在 Android 里填 LLM Key

Android APK 会安装到用户设备上，直接保存 LLM Key 更容易泄露。公开版本的设计是：

```text
Android App -> 你的后端 -> LLM 服务商
```

Android 只需要知道：

- 你的后端地址。
- 你的后端 API Token。

LLM Key、模型名、供应商地址都在后端改。

## 修改后是否需要重启

环境变量通常在进程启动时读取。修改 `.env` 后请重启后端进程或容器。

## 无 Key 模式

`OPENAI_API_KEY` 为空时，后端会返回安全占位输出，方便本地开发和演示。
