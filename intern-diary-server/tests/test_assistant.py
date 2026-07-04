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


def test_chat_requires_auth():
    c = TestClient(app)
    r = c.post("/api/assistant/chat", json={"messages": [{"role": "user", "content": "hi"}]})
    assert r.status_code == 401


def test_chat_dev_mode_returns_placeholder(tmp_path, monkeypatch):
    monkeypatch.setenv("DATA_DIR", str(tmp_path))
    settings.cache_clear()
    c = TestClient(app)
    r = c.post(
        "/api/assistant/chat",
        headers=H,
        json={"messages": [{"role": "user", "content": "你好"}]},
    )
    assert r.status_code == 200
    assert "reply" in r.json()


def test_diary_edit_preview_does_not_write_files(tmp_path, monkeypatch):
    monkeypatch.setenv("DATA_DIR", str(tmp_path))
    settings.cache_clear()
    c = TestClient(app)
    day = tmp_path / "workdays" / "2026-07-02"
    day.mkdir(parents=True)
    (day / "diary_draft.md").write_text("# 旧草稿\n\n旧内容", encoding="utf-8")

    r = c.post(
        "/api/assistant/diary-edit/preview",
        headers=H,
        json={"date": "2026-07-02", "instruction": "让语气更正式", "messages": []},
    )
    assert r.status_code == 200
    body = r.json()
    assert "preview_id" in body
    assert isinstance(body["changes"], list)
    # Dev-mode placeholder always proposes diary_draft.md — verify it's not
    # written to disk until confirm is called.
    assert (day / "diary_draft.md").read_text(encoding="utf-8") == "# 旧草稿\n\n旧内容"


def test_diary_edit_preview_requires_auth():
    c = TestClient(app)
    r = c.post(
        "/api/assistant/diary-edit/preview",
        json={"date": "2026-07-02", "instruction": "x", "messages": []},
    )
    assert r.status_code == 401


def test_diary_edit_confirm_writes_only_previewed_targets(tmp_path, monkeypatch):
    monkeypatch.setenv("DATA_DIR", str(tmp_path))
    settings.cache_clear()
    c = TestClient(app)
    day = tmp_path / "workdays" / "2026-07-02"
    day.mkdir(parents=True)
    (day / "diary_draft.md").write_text("# 旧草稿", encoding="utf-8")

    preview = c.post(
        "/api/assistant/diary-edit/preview",
        headers=H,
        json={"date": "2026-07-02", "instruction": "改一下", "messages": []},
    ).json()

    r = c.post(
        "/api/assistant/diary-edit/confirm",
        headers=H,
        json={"preview_id": preview["preview_id"]},
    )
    assert r.status_code == 200
    assert r.json()["changed_targets"] == ["diary_draft.md"]
    # Dev-mode placeholder echoes the existing draft content back unchanged.
    assert (day / "diary_draft.md").read_text(encoding="utf-8") == "# 旧草稿"


def test_diary_edit_confirm_rejects_unknown_preview_id():
    c = TestClient(app)
    r = c.post(
        "/api/assistant/diary-edit/confirm",
        headers=H,
        json={"preview_id": "prev_doesnotexist"},
    )
    assert r.status_code == 404


def test_diary_edit_confirm_is_single_use(tmp_path, monkeypatch):
    monkeypatch.setenv("DATA_DIR", str(tmp_path))
    settings.cache_clear()
    c = TestClient(app)
    day = tmp_path / "workdays" / "2026-07-02"
    day.mkdir(parents=True)

    preview = c.post(
        "/api/assistant/diary-edit/preview",
        headers=H,
        json={"date": "2026-07-02", "instruction": "改一下", "messages": []},
    ).json()

    first = c.post(
        "/api/assistant/diary-edit/confirm",
        headers=H,
        json={"preview_id": preview["preview_id"]},
    )
    assert first.status_code == 200

    second = c.post(
        "/api/assistant/diary-edit/confirm",
        headers=H,
        json={"preview_id": preview["preview_id"]},
    )
    assert second.status_code == 404


def test_diary_edit_preview_filters_out_disallowed_targets_from_model(tmp_path, monkeypatch):
    # Even if the model tries to propose editing raw_text.md or an arbitrary
    # path, the preview response must drop it — this is the security boundary
    # from docs/modules/手机端AI助手/_后端AI接口.md.
    monkeypatch.setenv("DATA_DIR", str(tmp_path))
    monkeypatch.setenv("CODEX_ENABLED", "1")
    malicious = (
        '{"reply":"done","changes":['
        '{"target":"raw_text.md","before_summary":"a","after_summary":"b","new_content":"hacked"},'
        '{"target":"../../etc/passwd","before_summary":"a","after_summary":"b","new_content":"hacked"},'
        '{"target":"diary_draft.md","before_summary":"a","after_summary":"b","new_content":"ok content"}'
        ']}'
    )
    monkeypatch.setenv("CODEX_COMMAND", _fake_codex(tmp_path, malicious))
    settings.cache_clear()

    c = TestClient(app)
    day = tmp_path / "workdays" / "2026-07-02"
    day.mkdir(parents=True)

    r = c.post(
        "/api/assistant/diary-edit/preview",
        headers=H,
        json={"date": "2026-07-02", "instruction": "改一下", "messages": []},
    )
    assert r.status_code == 200
    targets = [c["target"] for c in r.json()["changes"]]
    assert targets == ["diary_draft.md"]
    # The disallowed files must not exist anywhere on disk.
    assert not (day / "raw_text.md").exists()
    assert not (tmp_path / "etc" / "passwd").exists()


def test_diary_edit_preview_returns_contents_and_honors_targets(tmp_path, monkeypatch):
    monkeypatch.setenv("DATA_DIR", str(tmp_path))
    monkeypatch.setenv("CODEX_ENABLED", "1")
    model_json = (
        '{"reply":"done","changes":['
        '{"target":"sorted_notes.md","before_summary":"old sorted","after_summary":"new sorted","new_content":"sorted new"},'
        '{"target":"diary_draft.md","before_summary":"old draft","after_summary":"new draft","new_content":"draft new"}'
        ']}'
    )
    monkeypatch.setenv("CODEX_COMMAND", _fake_codex(tmp_path, model_json))
    settings.cache_clear()

    c = TestClient(app)
    day = tmp_path / "workdays" / "2026-07-02"
    day.mkdir(parents=True)
    (day / "sorted_notes.md").write_text("sorted old", encoding="utf-8")
    (day / "diary_draft.md").write_text("draft old", encoding="utf-8")

    r = c.post(
        "/api/assistant/diary-edit/preview",
        headers=H,
        json={
            "date": "2026-07-02",
            "instruction": "只改草稿",
            "messages": [],
            "targets": ["diary_draft.md"],
        },
    )

    assert r.status_code == 200
    changes = r.json()["changes"]
    assert [x["target"] for x in changes] == ["diary_draft.md"]
    assert changes[0]["before_content"] == "draft old"
    assert changes[0]["new_content"] == "draft new"


def test_diary_edit_confirm_rejects_disallowed_target_even_if_injected(tmp_path, monkeypatch):
    # Defense in depth: confirm must re-validate targets, not just trust the
    # stored preview. Simulate a preview dict with a disallowed target by
    # going through the real store (import main directly).
    from app import main as main_module

    monkeypatch.setenv("DATA_DIR", str(tmp_path))
    settings.cache_clear()
    c = TestClient(app)
    day = tmp_path / "workdays" / "2026-07-02"
    day.mkdir(parents=True)

    main_module._diary_edit_previews["prev_injected"] = {
        "workspace": "default",
        "date": "2026-07-02",
        "changes": [
            {
                "target": "raw_text.md",
                "before_summary": "a",
                "after_summary": "b",
                "new_content": "hacked",
            }
        ],
    }
    r = c.post(
        "/api/assistant/diary-edit/confirm",
        headers=H,
        json={"preview_id": "prev_injected"},
    )
    assert r.status_code == 400
    assert not (day / "raw_text.md").exists()
