from fastapi.testclient import TestClient

from app.config import settings
from app.main import app

client = TestClient(app)


def test_health_public():
    assert client.get("/health").json() == {"ok": True}


def test_auth_required():
    assert client.get("/api/me").status_code == 401


def test_auth_ok():
    r = client.get("/api/me", headers={"Authorization": "Bearer dev-token"})
    assert r.status_code == 200
    assert r.json()["workspace"] == "default"


def test_token_profile_workspace_mapping(tmp_path, monkeypatch):
    monkeypatch.setenv("DATA_DIR", str(tmp_path))
    settings.cache_clear()
    (tmp_path / "users.json").write_text(
        '{"tokens":{"tok-a":{"workspace":"alice","name":"Alice"},"tok-b":{"workspace":"bob"}}}',
        encoding="utf-8",
    )
    c = TestClient(app)
    assert c.get("/api/me", headers={"Authorization": "Bearer tok-a"}).json()["profile"]["name"] == "Alice"
    c.post("/api/entries/text", headers={"Authorization": "Bearer tok-a"}, json={"date": "2026-07-01", "content": "a"})
    c.post("/api/entries/text", headers={"Authorization": "Bearer tok-b"}, json={"date": "2026-07-01", "content": "b"})
    assert (tmp_path / "users" / "alice" / "workdays" / "2026-07-01" / "raw_text.md").exists()
    assert (tmp_path / "users" / "bob" / "workdays" / "2026-07-01" / "raw_text.md").exists()
    assert not (tmp_path / "workdays" / "2026-07-01" / "raw_text.md").exists()
