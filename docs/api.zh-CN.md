# API 说明

所有 `/api/*` 接口都使用：

```http
Authorization: Bearer <API_TOKEN>
```

## 健康检查

```http
GET /health
```

## 当前用户

```http
GET /api/me
```

## 提交文字

```http
POST /api/entries/text
Content-Type: application/json

{
  "date": "2026-07-04",
  "content": "今天完成了接口联调"
}
```

## 上传图片

```http
POST /api/entries/image
Content-Type: multipart/form-data
```

字段：

- `date`
- `image`
- `note` 可选

## 获取日期状态

```http
GET /api/days/{date}
```

## 整理当天素材

```http
POST /api/actions/sort-day
Content-Type: application/json

{
  "date": "2026-07-04",
  "instruction": "突出技术收获"
}
```

## 生成正式日记

```http
POST /api/actions/generate-diary
Content-Type: application/json

{
  "date": "2026-07-04",
  "instruction": "语气正式一点"
}
```

## 报告模板

```http
GET /api/report-templates
```

返回周报、月报、实习总结等可用模板：

```json
{
  "templates": [
    {
      "id": "weekly-default",
      "type": "weekly",
      "name": "默认周报",
      "variables": ["姓名", "班级", "学号", "开始日期", "结束日期", "报告类型"]
    }
  ]
}
```

## 生成报告

```http
POST /api/actions/generate-report
Content-Type: application/json

{
  "type": "weekly",
  "start_date": "2026-07-01",
  "end_date": "2026-07-07",
  "template_id": "weekly-default",
  "word_count": 1000,
  "extra_instruction": "突出技术收获"
}
```

`type` 可选：`weekly`、`monthly`、`internship_summary`。`template_id` 和 `word_count` 可省略，后端会使用该类型默认模板和默认字数。

响应包含 `report_id`、`markdown`、`validation`，以及可用于后续下载的文件信息。

## 获取报告元数据

```http
GET /api/reports/{report_id}
```

## 下载报告 Markdown

```http
GET /api/reports/{report_id}/draft
```

## 下载报告 Word

```http
GET /api/reports/{report_id}/files/report.docx
```

## 下载 Word

```http
GET /api/days/{date}/files/diary_final.docx
```

## 网页控制台

```http
GET /console
```
