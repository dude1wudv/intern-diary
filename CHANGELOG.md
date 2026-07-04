# Changelog

## v2.0.0 - 2026-07-04

### Added

- Android 设置页新增服务器地址输入，可与 API Token 一起保存和测试连接。
- 后端新增 `.env.example`，公开说明 LLM 服务商配置方式。
- 新增中文默认 README、英文 README、部署教程、使用方法、开发者指南、发布流程。
- 新增 GitHub CI 工作流和社区模板。

### Changed

- 公开版本不再写死个人服务器域名。
- 默认关闭 `VISION_ENABLED` 和 `CODEX_ENABLED`，克隆用户可自行配置后端能力。

### Security

- 排除运行数据、SQLite、APK 构建产物、本地 SDK 配置、虚拟环境、密钥文件。
