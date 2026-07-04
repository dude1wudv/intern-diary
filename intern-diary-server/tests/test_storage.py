from app.audit import audit
from app.config import settings
from app.paths import day_dir


def test_day_dir_rejects_bad_date():
    try:
        day_dir("../x")
    except ValueError:
        return
    raise AssertionError("bad date accepted")


def test_audit_writes(tmp_path, monkeypatch):
    monkeypatch.setenv("DATA_DIR", str(tmp_path))
    settings.cache_clear()
    audit("2026-07-01", "unit-test", "ok")
    text = (tmp_path / "workdays" / "2026-07-01" / "audit.log").read_text(encoding="utf-8")
    assert "unit-test" in text
