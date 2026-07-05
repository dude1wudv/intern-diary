from fastapi.testclient import TestClient

from app.config import settings
from app.main import app

H = {"Authorization": "Bearer dev-token"}


def test_text_upload_and_status(tmp_path, monkeypatch):
    monkeypatch.setenv("DATA_DIR", str(tmp_path))
    settings.cache_clear()
    c = TestClient(app)
    r = c.post("/api/entries/text", headers=H, json={"date": "2026-07-01", "content": "学习设备巡检"})
    assert r.status_code == 200
    s = c.get("/api/days/2026-07-01", headers=H).json()
    assert s["raw_text_exists"] is True


def test_invalid_date_read_endpoints_return_400(tmp_path, monkeypatch):
    monkeypatch.setenv("DATA_DIR", str(tmp_path))
    settings.cache_clear()
    c = TestClient(app)
    for path in [
        "/api/days/not-a-date/draft",
        "/api/days/not-a-date/files/diary_final.docx",
        "/api/days/not-a-date/image-descriptions",
    ]:
        assert c.get(path, headers=H).status_code == 400
