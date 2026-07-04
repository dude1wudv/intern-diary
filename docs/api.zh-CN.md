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
- `file`
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

## 下载 Word

```http
GET /api/days/{date}/files/diary_final.docx
```

## 网页控制台

```http
GET /console
```
