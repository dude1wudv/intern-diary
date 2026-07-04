# Contributing

感谢贡献。

## 开发流程

1. Fork 仓库并创建功能分支。
2. 后端改动运行 `pytest`。
3. Android 改动运行 `./gradlew assembleDebug`。
4. 不提交 `.env`、数据库、APK、签名密钥和本地构建产物。
5. 提交 PR，说明变更、测试结果和影响范围。

## Commit 风格

推荐 Conventional Commits：

```text
feat(android): add configurable server url
fix(server): handle missing api token
chore(docs): improve deployment guide
```
