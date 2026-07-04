# Intern Diary

> Capture internship notes on Android, process them on a FastAPI backend, and generate downloadable Word diary documents.

[中文说明](README.md)

## Overview

Intern Diary is an Android + FastAPI self-hosted diary assistant:

- The Android app records daily text, photos, and notes.
- The backend stores content by date and calls an OpenAI-compatible LLM to sort notes and generate diary drafts.
- The web console at `/console` lets users manage entries, preview drafts, and download Word files.
- The Android settings screen lets users configure the backend server URL and API token.
- LLM provider URL, model, and API key are backend-only settings and are not embedded in the APK.

## Tech stack

- Android: Kotlin, AndroidX, Material Components, OkHttp, ViewBinding
- Backend: Python, FastAPI, Pydantic, httpx, python-docx, Uvicorn
- AI: OpenAI-compatible Chat Completions API
- Deployment: Docker / docker compose / HTTPS reverse proxy

## Quick start

### Backend

```bash
cd intern-diary-server
python -m venv .venv
. .venv/bin/activate          # Windows: .venv\Scripts\activate
pip install -e .[test]
cp .env.example .env
uvicorn app.main:app --host 0.0.0.0 --port 8088
```

Edit `intern-diary-server/.env`:

```env
API_TOKEN=replace-with-a-long-random-token
OPENAI_API_KEY=your-llm-api-key
OPENAI_BASE_URL=https://api.openai.com/v1
OPENAI_MODEL=gpt-5.5
VISION_ENABLED=0
CODEX_ENABLED=0
```

### Android

```bash
cd intern-diary-android
./gradlew assembleDebug
```

Install the APK and open Settings:

1. Server URL, for example `https://your-diary.example.com` or `http://10.0.2.2:8088` for an emulator.
2. API Token, same as backend `API_TOKEN`.
3. Tap Test connection.

## Documentation

- Chinese deployment guide: `docs/deployment.zh-CN.md`
- Chinese usage guide: `docs/usage.zh-CN.md`
- LLM provider configuration: `docs/llm-provider.zh-CN.md`
- Architecture: `docs/architecture.zh-CN.md`
- API reference: `docs/api.zh-CN.md`
- Android build guide: `docs/android-build.zh-CN.md`
- Developer guide: `docs/developer-guide.zh-CN.md`
- Release process: `docs/release-process.zh-CN.md`

## Security

- Never commit `.env`, API keys, runtime data, databases, APK signing keys, or generated build outputs.
- Android stores only the backend URL and backend API token.
- LLM credentials stay on the backend.
- Replace the default `API_TOKEN` before exposing the backend.

## Version

Current release: `v2.0.0`.

See [CHANGELOG.md](CHANGELOG.md).

## License

This project is licensed under the [MIT License](LICENSE).

