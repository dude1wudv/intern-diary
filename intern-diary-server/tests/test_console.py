from fastapi.testclient import TestClient

from app.config import settings
from app.main import app

AUTH = {"Authorization": "Bearer dev-token"}


def test_console_reads_raw_text_and_sorted_notes(tmp_path, monkeypatch):
    monkeypatch.setenv("DATA_DIR", str(tmp_path))
    settings.cache_clear()
    c = TestClient(app)
    day = tmp_path / "workdays" / "2026-07-02"
    day.mkdir(parents=True)
    (day / "raw_text.md").write_text("raw body", encoding="utf-8")
    (day / "sorted_notes.md").write_text("sorted body", encoding="utf-8")

    raw = c.get("/api/days/2026-07-02/raw-text", headers=AUTH)
    sorted_notes = c.get("/api/days/2026-07-02/sorted-notes", headers=AUTH)

    assert raw.status_code == 200
    assert raw.text == "raw body"
    assert sorted_notes.status_code == 200
    assert sorted_notes.text == "sorted body"


def test_console_read_endpoints_require_auth():
    c = TestClient(app)
    assert c.get("/api/days/2026-07-02/raw-text").status_code == 401
    assert c.get("/api/days/2026-07-02/sorted-notes").status_code == 401


def test_console_read_endpoint_returns_404_for_missing_file(tmp_path, monkeypatch):
    monkeypatch.setenv("DATA_DIR", str(tmp_path))
    settings.cache_clear()

    c = TestClient(app)
    r = c.get("/api/days/2026-07-02/raw-text", headers=AUTH)

    assert r.status_code == 404


def test_console_page_contains_app_shell():
    c = TestClient(app)
    r = c.get("/console")

    assert r.status_code == 200
    assert "text/html" in r.headers["content-type"]
    assert "Diary Console" in r.text
    assert "localStorage" in r.text
    assert "/api/days/" in r.text


def test_console_page_renders_markdown_in_scrollable_viewer():
    c = TestClient(app)
    r = c.get("/console")

    assert "function renderMarkdown" in r.text
    assert "innerHTML = renderMarkdown" in r.text
    assert "preview-scroll" in r.text
    assert "overflow:auto" in r.text
    assert '<pre id="content"' not in r.text
    assert "sidebar" in r.text
    assert "activity-panel" in r.text
