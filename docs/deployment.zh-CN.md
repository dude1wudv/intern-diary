# 部署教程

本文档面向“克隆仓库后自建服务”的用户。

## 1. 准备环境

后端最低要求：

- Python 3.9+
- 可选：Docker 24+
- 可选：Nginx / Caddy / Traefik 等 HTTPS 反向代理

Android 构建要求：

- JDK 17+
- Android SDK
- Android Studio 或仓库内 Gradle Wrapper

## 2. 本地后端运行

```bash
cd intern-diary-server
python -m venv .venv
. .venv/bin/activate
pip install -e .[test]
cp .env.example .env
```

编辑 `.env`：

```env
API_TOKEN=replace-with-a-long-random-token
OPENAI_API_KEY=your-llm-api-key
OPENAI_BASE_URL=https://api.openai.com/v1
OPENAI_MODEL=gpt-5.5
VISION_ENABLED=0
CODEX_ENABLED=0
```

启动：

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8088
```

验证：

```bash
curl http://127.0.0.1:8088/health
```

## 3. Docker 运行

```bash
cd intern-diary-server
docker build -t intern-diary:local .
cp .env.example .env
# 编辑 .env
docker run --rm \
  --env-file .env \
  -p 127.0.0.1:8088:8088 \
  -v "$PWD/data:/data" \
  intern-diary:local
```

## 4. 生产部署建议

推荐结构：

```text
Internet
  ↓ HTTPS
Caddy/Nginx/Traefik
  ↓ 127.0.0.1:8088
intern-diary backend
  ↓
/data runtime files
```

关键点：

- 后端端口只绑定内网或 `127.0.0.1`。
- 对外只暴露 HTTPS 域名。
- `.env` 权限限制为部署用户可读。
- 运行数据目录定期备份。
- `API_TOKEN` 使用足够长的随机字符串。

## 5. 手机端连接

部署完成后，Android App 设置页填写：

- 服务器地址：`https://your-diary.example.com`
- API Token：`.env` 中的 `API_TOKEN`

如果使用 Android 模拟器访问本机后端：

```text
http://10.0.2.2:8088
```

## 6. 常见问题

### 连接失败

检查：

1. `/health` 是否返回成功。
2. 手机是否能访问服务器域名。
3. 服务器地址不要带最后的 `/`，App 会自动去掉尾部斜杠。
4. HTTPS 证书是否有效。

### 401 / 403

检查 Android 设置页中的 API Token 是否和后端 `.env` 的 `API_TOKEN` 一致。

### LLM 没有真实输出

如果 `OPENAI_API_KEY` 为空，后端会走安全占位输出。填写 Key、Base URL、Model 后重启后端。
