# 发布流程

## 版本号

使用语义化版本：

```text
vMAJOR.MINOR.PATCH
```

示例：

- `v2.0.0`：公开发布版本。
- `v2.0.1`：文档或小修复。
- `v2.1.0`：新增功能。

## 发布前检查

```bash
cd intern-diary-server
pytest

cd ../intern-diary-android
./gradlew assembleDebug
```

确认敏感文件没有进入 Git：

```bash
git status --short
```

不应出现：

- `.env`
- `data/`
- `*.sqlite3`
- `local.properties`
- `*.jks`
- `*.keystore`

## GitHub Release 资产

当前公开发布上传 Debug APK：

```text
intern-diary-debug-v2.0.apk
```

生产分发请自行生成签名 Release APK。

## Release Notes 模板

```markdown
## Highlights

- Android 设置页支持填写后端服务器地址。
- 后端 LLM 服务商通过环境变量配置。
- 提供中英文 README 和中文部署教程。

## Checks

- Backend: `pytest`
- Android: `./gradlew assembleDebug`
```
