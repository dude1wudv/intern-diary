import sys

from fastapi.testclient import TestClient

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


def _seed_day(root, date, body):
    day = root / "workdays" / date
    day.mkdir(parents=True)
    (day / "raw_text.md").write_text(f"{date} raw\n", encoding="utf-8")
    (day / "sorted_notes.md").write_text(body, encoding="utf-8")
    desc = day / "image_descriptions"
    desc.mkdir()
    (desc / "现场.md").write_text(f"{date} image\n", encoding="utf-8")


def test_report_templates_expose_default_contract(tmp_path, monkeypatch):
    monkeypatch.setenv("DATA_DIR", str(tmp_path))
    settings.cache_clear()

    r = TestClient(app).get("/api/report-templates", headers=H)

    assert r.status_code == 200
    templates = r.json()["templates"]
    assert {"weekly", "monthly", "internship_summary"} <= {t["type"] for t in templates}
    weekly = next(t for t in templates if t["type"] == "weekly")
    assert {"id", "type", "name", "variables"} <= set(weekly)
    assert {"姓名", "班级", "学号"} <= set(weekly["variables"])


def test_generate_report_rejects_bad_range_and_unknown_template(tmp_path, monkeypatch):
    monkeypatch.setenv("DATA_DIR", str(tmp_path))
    settings.cache_clear()
    c = TestClient(app)

    bad_range = c.post(
        "/api/actions/generate-report",
        headers=H,
        json={"type": "weekly", "start_date": "2026-07-07", "end_date": "2026-07-01"},
    )
    assert bad_range.status_code in {400, 422}

    missing_template = c.post(
        "/api/actions/generate-report",
        headers=H,
        json={
            "type": "weekly",
            "start_date": "2026-07-01",
            "end_date": "2026-07-03",
            "template_id": "does-not-exist",
        },
    )
    assert missing_template.status_code in {400, 404}
    assert "template" in missing_template.text.lower() or "模板" in missing_template.text


def test_generate_weekly_report_aggregates_range_and_exposes_report_api(tmp_path, monkeypatch):
    monkeypatch.setenv("DATA_DIR", str(tmp_path))
    monkeypatch.setenv("CODEX_ENABLED", "1")
    monkeypatch.setenv(
        "CODEX_COMMAND",
        _fake_codex(
            tmp_path,
            "# 周报\n\n2026-07-01 巡检\n\n2026-07-02 调试\n\n2026-07-03 复盘\n",
        ),
    )
    settings.cache_clear()
    for date, body in [
        ("2026-07-01", "# 2026-07-01\n巡检设备"),
        ("2026-07-02", "# 2026-07-02\n调试链路"),
        ("2026-07-03", "# 2026-07-03\n复盘记录"),
    ]:
        _seed_day(tmp_path, date, body)

    c = TestClient(app)
    r = c.post(
        "/api/actions/generate-report",
        headers=H,
        json={
            "type": "weekly",
            "start_date": "2026-07-01",
            "end_date": "2026-07-03",
            "word_count": 1000,
        },
    )

    assert r.status_code == 200
    body = r.json()
    assert body["status"] == "drafted"
    assert body["report_id"].startswith("weekly-2026-07-01_2026-07-03")
    assert isinstance(body["validation"], list)
    assert body["markdown"].index("2026-07-01") < body["markdown"].index("2026-07-03")

    report_id = body["report_id"]
    meta = c.get(f"/api/reports/{report_id}", headers=H)
    draft = c.get(f"/api/reports/{report_id}/draft", headers=H)
    docx = c.get(f"/api/reports/{report_id}/files/report.docx", headers=H)

    assert meta.status_code == 200
    assert meta.json()["report_id"] == report_id
    assert draft.status_code == 200
    assert "2026-07-02" in draft.text
    assert docx.status_code == 200
    assert docx.content


def test_missing_report_download_returns_404(tmp_path, monkeypatch):
    monkeypatch.setenv("DATA_DIR", str(tmp_path))
    settings.cache_clear()

    r = TestClient(app).get("/api/reports/not-there/files/report.docx", headers=H)

    assert r.status_code == 404
