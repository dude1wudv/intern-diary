# API 文档：报告生成

> 字段先作为实现契约；实际实现时如需改名，先改本文档再改代码。

## GET /api/report-templates

- 用途：列出可用报告模板。
- 关联模块：[[modules/M1-模板系统/_M1-模板系统.md]]

### 响应

```json
{
  "templates": [
    {"id":"weekly-default", "type":"weekly", "name":"默认周报", "variables":["姓名","班级","学号"]}
  ]
}
```

## POST /api/actions/generate-report

- 用途：按类型和日期范围生成周报/月报/实习总结。
- 关联模块：[[modules/M2-日报周报月报生成/周报月报生成.md]]、[[modules/M3-实习总结生成/_M3-实习总结生成.md]]

### 请求

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| type | body | string | 是 | `weekly` / `monthly` / `internship_summary` |
| start_date | body | string | 是 | `YYYY-MM-DD` |
| end_date | body | string | 是 | `YYYY-MM-DD`，不得早于 start_date |
| template_id | body | string | 否 | 不传则用类型默认模板 |
| word_count | body | int | 否 | 默认 weekly 1000，monthly 1500，summary 3000 |
| extra_instruction | body | string | 否 | 用户补充要求 |

### 响应

```json
{
  "status": "drafted",
  "report_id": "weekly-2026-07-01_2026-07-07-ab12",
  "markdown": "# ...",
  "validation": []
}
```

## GET /api/reports/{report_id}

- 用途：获取报告元数据和校验结果。

## GET /api/reports/{report_id}/draft

- 用途：下载报告 Markdown。

## GET /api/reports/{report_id}/files/report.docx

- 用途：下载报告 Word。

## 错误码

| 码 | 含义 | 处理建议 |
|---|---|---|
| 400 | 日期范围或 type 无效 | 前端提示用户修改。 |
| 404 | 模板或报告不存在 | 刷新模板/报告列表。 |
| 422 | 请求字段格式错误 | 前端校验。 |
| 502 | LLM 生成失败 | 保留素材，允许重试。 |
