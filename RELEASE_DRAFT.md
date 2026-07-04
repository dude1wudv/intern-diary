# Intern Diary v2.1.0

## Highlights

- 新增实习成果生成器：周报、月报、实习总结。
- 新增 Report API：模板列表、日期范围生成、Markdown/Word 下载。
- Web Console 新增 Report 区，可选择类型、日期范围和模板。
- Android 新增“生成周报”入口，生成后自动保存 Word。
- 增加 weekly/monthly/summary、缺天、LLM 失败等后端回归测试。

## Assets

- `intern-diary-debug-v2.1.0.apk`

## Verification

- Backend: `python -m pytest` -> 37 passed
- Android: `.\gradlew.bat :app:assembleDebug` -> BUILD SUCCESSFUL
