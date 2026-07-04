# Security Policy

## 支持版本

当前只维护最新公开版本。

## 报告安全问题

请不要在公开 Issue 中贴真实 API Key、Token、服务器地址、数据库内容或日志全文。

报告时请提供：

- 影响范围。
- 复现步骤。
- 期望行为和实际行为。
- 已脱敏的配置片段或日志。

## 默认安全边界

- Android 不保存 LLM API Key。
- 后端 API 使用 Bearer Token。
- `.env`、运行数据、数据库、签名密钥不应提交到 Git。
