# 架构说明

## 总体架构

```text
Android App
  ├─ 保存服务器地址/API Token
  ├─ 提交文字和图片
  └─ 展示 AI 助手与历史记录
        ↓ HTTPS + Bearer Token
FastAPI Backend
  ├─ 鉴权
  ├─ 按日期归档素材
  ├─ 调用 OpenAI-compatible LLM
  ├─ 渲染 Word
  └─ 提供网页控制台 /console
        ↓
Runtime Data Directory
```

## 配置边界

| 配置项 | 保存位置 | 说明 |
| --- | --- | --- |
| 服务器地址 | Android SharedPreferences | 用户可在设置页修改 |
| API Token | Android SharedPreferences + 后端 `.env` | 用于调用后端 |
| LLM Base URL | 后端 `.env` | 不进入 APK |
| LLM API Key | 后端 `.env` | 不进入 APK |
| LLM Model | 后端 `.env` | 可替换供应商模型 |

## 数据边界

运行数据默认在后端 `DATA_DIR` 下，公开仓库不包含任何运行数据或数据库。

## 失败降级

`OPENAI_API_KEY` 为空时，后端返回占位结果，便于本地开发和部署验证。
