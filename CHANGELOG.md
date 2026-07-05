# Changelog

## v2.1.1 - 2026-07-05

### Changed

- Android 上传图片前自动压缩为长边 1600px、JPEG 质量 85，减少大图上传失败。
- Android 图片上传改用临时文件请求体，避免一次性把大图读入上传请求内存。

### Fixed

- 后端图片上传增加 `MAX_IMAGE_UPLOAD_BYTES` 上限，超限返回 HTTP 413。
- 后端草稿、Word、图片描述读取接口对非法日期返回 HTTP 400，而不是 500。

## v2.1.0 - 2026-07-05

### Added

- 新增实习成果生成器：支持周报、月报、实习总结。
- 新增 Report API：模板列表、按日期范围生成报告、下载 Markdown/Word。
- Web Console 新增 Report 区，可选择报告类型、日期范围和模板。
- Android 当天页新增“生成周报”入口，并自动下载 Word。
- 新增报告模板注册表和报告生成回归测试。

### Changed

- Word 渲染复用现有模板，支持报告变量填充。
- 开发规划文档标记 A 主线已完成并校验。

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
