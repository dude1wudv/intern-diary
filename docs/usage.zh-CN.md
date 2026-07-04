# 使用方法

## 1. 首次配置

打开 Android App，进入右上角“设置”：

1. 填写服务器地址。
2. 填写 API Token。
3. 点击“测试连接”。
4. 保存。

## 2. 记录当天素材

在当天页面可以：

- 输入文字记录。
- 从相册选择图片。
- 给图片补充说明。
- 切换日期查看历史。

建议每天至少记录：

- 做了什么任务。
- 遇到什么问题。
- 学到什么方法。
- 明天准备做什么。

## 3. 整理素材

点击整理相关操作后，后端会把当天原始素材整理成更清晰的结构化记录。

如果没有配置 LLM，系统会返回占位内容，不会中断流程。

## 4. 生成正式日记

生成正式日记后，可在网页控制台下载 Word 文件。

网页控制台地址：

```text
https://your-diary.example.com/console
```

浏览器中输入同一个 API Token 即可使用。

## 5. 生成实习成果报告

后端支持按日期范围生成周报、月报和实习总结：

- 报告模板来自 `GET /api/report-templates`，默认包含 `weekly-default`、`monthly-default`、`internship-summary-default`。
- 网页控制台 `/console` 的 Report 区可选择报告类型、开始日期、结束日期和模板，生成后下载 Markdown 或 Word。
- Android 当天页的“生成周报”会按当前日期所在周调用 `POST /api/actions/generate-report`，并把 Word 保存到下载目录。

## 6. AI 助手

AI 助手支持普通聊天和日记修改辅助。所有模型调用都由后端完成，手机端不保存 LLM API Key。

## 7. 数据保存位置

后端默认保存到 `DATA_DIR`，本地运行时通常是：

```text
intern-diary-server/data/
```

Docker 部署时建议挂载到持久化目录，例如 `/data`。
