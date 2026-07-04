# Intern Diary v2.0.0

## Highlights

- Android 设置页支持填写后端服务器地址和 API Token。
- LLM 服务商配置保留在后端：`OPENAI_BASE_URL` / `OPENAI_API_KEY` / `OPENAI_MODEL`。
- 提供中文默认 README、英文 README、中文部署教程和使用方法。
- 发布 Debug APK，方便直接安装测试。

## Assets

- `intern-diary-debug-v2.0.apk`

## Verification

- Backend: `python -m pytest -q` -> 30 passed
- Android: `./gradlew.bat assembleDebug --no-daemon` -> BUILD SUCCESSFUL
