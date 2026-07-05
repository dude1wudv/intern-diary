# Intern Diary v2.1.1

## Highlights

- Android 上传图片前自动压缩为长边 1600px、JPEG 质量 85。
- Android 上传大图改用临时文件请求体，避免一次性读取大图到请求内存。
- 后端图片上传增加大小上限，超限返回 HTTP 413。
- 后端非法日期读取接口返回 HTTP 400，避免 500。

## Assets

- `intern-diary-debug-v2.1.1.apk`

## Verification

- Backend: `python -m pytest` -> 39 passed
- Android: `.\gradlew.bat :app:assembleDebug` -> BUILD SUCCESSFUL
