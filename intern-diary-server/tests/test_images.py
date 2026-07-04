from fastapi.testclient import TestClient
import sys

from app.config import settings
from app.main import app

H = {"Authorization": "Bearer dev-token"}


def _fake_codex(tmp_path, output):
    p = tmp_path / "fake_codex.py"
    p.write_text(
        "import sys\n"
        "out = sys.argv[sys.argv.index('--output-last-message') + 1]\n"
        f"open(out, 'w', encoding='utf-8').write({output!r})\n",
        encoding="utf-8",
    )
    return f"{sys.executable} {p}"


def test_image_upload_archives_with_deferred_vision(tmp_path, monkeypatch):
    # Vision is deferred by default (VISION_ENABLED unset -> off). The image
    # must still be archived, with a placeholder description, status "stored".
    monkeypatch.setenv("DATA_DIR", str(tmp_path))
    settings.cache_clear()
    c = TestClient(app)
    r = c.post(
        "/api/entries/image",
        headers=H,
        data={"date": "2026-07-01", "note": "现场照片"},
        files={"image": ("a.jpg", b"fake-jpg", "image/jpeg")},
    )
    assert r.status_code == 200
    assert r.json()["status"] == "stored"
    s = c.get("/api/days/2026-07-01", headers=H).json()
    assert s["image_count"] == 1
    assert s["described_image_count"] == 0
    day = tmp_path / "workdays" / "2026-07-01"
    imgs = list((day / "images").glob("*.jpg"))
    descs = list((day / "image_descriptions").glob("*.md"))
    assert len(imgs) == 1
    assert len(descs) == 1
    assert imgs[0].stem == descs[0].stem


def test_image_upload_describes_with_codex_cli(tmp_path, monkeypatch):
    monkeypatch.setenv("DATA_DIR", str(tmp_path))
    monkeypatch.setenv("VISION_ENABLED", "1")
    monkeypatch.setenv("CODEX_ENABLED", "1")
    monkeypatch.setenv("CODEX_COMMAND", _fake_codex(tmp_path, "# codex image md\n"))
    settings.cache_clear()
    c = TestClient(app)
    r = c.post(
        "/api/entries/image",
        headers=H,
        data={"date": "2026-07-01", "note": "现场照片"},
        files={"image": ("a.png", b"fake-png", "image/png")},
    )
    assert r.status_code == 200
    assert r.json()["status"] == "described"
    s = c.get("/api/days/2026-07-01", headers=H).json()
    assert s["described_image_count"] == 1
    desc = next((tmp_path / "workdays" / "2026-07-01" / "image_descriptions").glob("*.md"))
    assert desc.read_text(encoding="utf-8") == "# codex image md\n"
