"""GPT-5.5 client helpers.

Dev fallback: when OPENAI_API_KEY is empty, every function returns a
deterministic placeholder so tests and local runs work fully offline.
Production path: real httpx calls to {openai_base_url}/chat/completions.

Never log or print the API key, bearer token, or full request bodies.
"""
import asyncio
import base64
import json
import os
import shlex
import subprocess
import tempfile
from pathlib import Path
from typing import Any, Dict, List, Optional

import httpx

from .config import settings

PROMPTS_DIR = Path(__file__).resolve().parent.parent / "prompts"

_DEFAULT_IMAGE_DESCRIPTION_PROMPT = (
    "你是一个专业实习日记系统的图片理解助手，只输出结构化 Markdown 描述，"
    "不编造事实，不写敏感信息，不出现模型痕迹用语。"
)
_DEFAULT_DAILY_SORT_PROMPT = (
    "你是一个专业实习日记系统的素材整理助手，只输出结构化 Markdown 整理稿，"
    "不编造事实，不写敏感信息，不出现模型痕迹用语。"
)
_DEFAULT_DIARY_GENERATION_PROMPT = (
    "你是一个专业实习日记系统的日记生成助手，只输出一个 JSON 对象，"
    "字段为 date,title,body_paragraphs,safety_notes，"
    "语气正式客观，不编造事实，不写敏感信息，不出现模型痕迹用语。"
)
_DEFAULT_REPORT_GENERATION_PROMPT = (
    "你是一个专业实习报告生成助手，只输出 Markdown 正文。"
    "根据日期范围内的真实素材生成周报、月报或实习总结，"
    "不编造事实，不写敏感信息，不出现模型痕迹用语。"
)


def _load_prompt(filename: str, default: str) -> str:
    path = PROMPTS_DIR / filename
    try:
        text = path.read_text(encoding="utf-8").strip()
        return text if text else default
    except OSError:
        return default


def _load_context() -> str:
    path = PROMPTS_DIR / "context.md"
    try:
        return path.read_text(encoding="utf-8").strip()
    except OSError:
        return ""


async def _chat(messages: List[Dict[str, Any]], *, json_mode: bool = False) -> str:
    """Shared helper: POST to {base_url}/chat/completions and return the
    assistant's message content. Raises on HTTP/transport errors with a
    clean, secret-free message.
    """
    s = settings()
    url = f"{s.openai_base_url.rstrip('/')}/chat/completions"
    payload: Dict[str, Any] = {
        "model": s.model,
        "messages": messages,
    }
    if json_mode:
        payload["response_format"] = {"type": "json_object"}
    headers = {"Authorization": f"Bearer {s.openai_api_key}"}
    try:
        async with httpx.AsyncClient(timeout=120.0) as client:
            resp = await client.post(url, headers=headers, json=payload)
            resp.raise_for_status()
            data = resp.json()
    except httpx.HTTPStatusError as exc:
        raise RuntimeError(
            f"model API returned HTTP {exc.response.status_code}"
        ) from None
    except httpx.HTTPError:
        raise RuntimeError("model API request failed") from None
    try:
        return data["choices"][0]["message"]["content"]
    except (KeyError, IndexError, TypeError):
        raise RuntimeError("model API returned an unexpected response shape") from None


def _image_data_url(image_path: Path) -> str:
    suffix = image_path.suffix.lstrip(".").lower() or "jpeg"
    if suffix == "jpg":
        suffix = "jpeg"
    encoded = base64.b64encode(image_path.read_bytes()).decode("ascii")
    return f"data:image/{suffix};base64,{encoded}"


def _codex_exec(prompt: str, image_path: Optional[Path] = None) -> str:
    s = settings()
    with tempfile.NamedTemporaryFile(delete=False, suffix=".txt") as f:
        out = Path(f.name)
    cmd = shlex.split(s.codex_command, posix=os.name != "nt") + [
        "exec",
        "--model",
        s.model,
        "--sandbox",
        "read-only",
        "--skip-git-repo-check",
        "--ephemeral",
        "--color",
        "never",
        "--output-last-message",
        str(out),
    ]
    if image_path:
        cmd += ["--image", str(image_path)]
    cmd.append("-")
    try:
        proc = subprocess.run(
            cmd,
            input=prompt,
            text=True,
            capture_output=True,
            timeout=s.codex_timeout_seconds,
            cwd=str((image_path.parent if image_path else settings().data_dir).resolve()),
        )
        if proc.returncode:
            raise RuntimeError("codex CLI failed")
        text = out.read_text(encoding="utf-8").strip()
        return text + ("\n" if text else "")
    except (OSError, subprocess.SubprocessError):
        raise RuntimeError("codex CLI request failed") from None
    finally:
        try:
            out.unlink()
        except OSError:
            pass


async def describe_image(image_path: Path, context: str) -> str:
    system_prompt = _load_prompt("image_description.md", _DEFAULT_IMAGE_DESCRIPTION_PROMPT)
    instruction = (
        f"图片文件名：{image_path.name}\n"
        f"用户补充说明：{context or '（无）'}\n"
        "请按系统提示中的结构，输出这张图片的 Markdown 描述。"
    )
    if settings().codex_enabled:
        return await asyncio.to_thread(_codex_exec, f"{system_prompt}\n\n{instruction}", image_path)

    if not settings().openai_api_key:
        return f"""# 图片描述：{image_path.name}

## 基本信息
- 用户补充说明：{context}

## 画面内容
已保存图片，开发模式未调用视觉模型。

## 与实习任务的可能关系
需要用户确认。

## 可写入正式日记的表述
今天结合现场材料进行了实践学习。

## 不建议写入的内容
无。

## 置信度与待确认点
开发模式未调用视觉模型。
"""

    messages = [
        {"role": "system", "content": system_prompt},
        {
            "role": "user",
            "content": [
                {"type": "text", "text": instruction},
                {"type": "image_url", "image_url": {"url": _image_data_url(image_path)}},
            ],
        },
    ]
    return await _chat(messages)


async def sort_day_text(source: str, extra: str = "") -> str:
    if settings().codex_enabled:
        system_prompt = _load_prompt("daily_sort.md", _DEFAULT_DAILY_SORT_PROMPT)
        context = _load_context()
        parts = [system_prompt]
        if context:
            parts.append(f"## 通用背景\n{context}")
        parts.append(f"## 今日素材\n{source}")
        if extra:
            parts.append(f"## 用户补充说明\n{extra}")
        return await asyncio.to_thread(_codex_exec, "\n\n".join(parts))

    if not settings().openai_api_key:
        return (
            "# 今日素材整理\n\n## 今日主要工作\n"
            + source[:500]
            + "\n\n## 实践过程\n\n## 学习收获\n\n## 遇到的问题\n\n## 解决方式\n\n"
            "## 可用于正式日记的素材\n\n## 不建议写入正式日记的素材\n\n## 信息缺口\n"
        )

    system_prompt = _load_prompt("daily_sort.md", _DEFAULT_DAILY_SORT_PROMPT)
    context = _load_context()
    user_parts = []
    if context:
        user_parts.append(f"## 通用背景\n{context}")
    user_parts.append(f"## 今日素材\n{source}")
    if extra:
        user_parts.append(f"## 用户补充说明\n{extra}")
    messages = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": "\n\n".join(user_parts)},
    ]
    return await _chat(messages)


_DEFAULT_ASSISTANT_SYSTEM = (
    "你是一个智能助手。回答用户的问题，语气自然、简洁。"
    "不出现“作为 AI”等模型痕迹用语。"
)

_DEFAULT_DIARY_EDIT_SYSTEM = (
    "你是一个专业实习日记系统的 AI 编辑助手。"
    "当用户要求修改日记相关文件时，你必须返回一个合法 JSON 对象，结构如下：\n"
    '{"reply":"你的说明（中文，简洁）","changes":[{"target":"文件名","before_summary":"修改前概要","after_summary":"修改后概要","new_content":"完整的新文件内容"}]}\n'
    "只允许修改：sorted_notes.md、diary_draft.md、image_descriptions/img_*.md。\n"
    "禁止修改：raw_text.md、原始图片文件、diary_final.docx。\n"
    "如无需修改，changes 返回空数组。"
)


async def assistant_chat(messages: List[Dict[str, Any]]) -> str:
    """Plain chat relay — no diary file context."""
    if settings().codex_enabled:
        prompt = _DEFAULT_ASSISTANT_SYSTEM + "\n\n"
        prompt += "\n".join(
            f"{'用户' if m['role']=='user' else '助手'}：{m['content']}" for m in messages
        )
        return await asyncio.to_thread(_codex_exec, prompt)
    if not settings().openai_api_key:
        return "开发模式：AI 回复占位。"
    sys_msg = [{"role": "system", "content": _DEFAULT_ASSISTANT_SYSTEM}]
    return await _chat(sys_msg + messages)


async def assistant_diary_preview(
    date: str,
    instruction: str,
    context_files: Dict[str, str],
    history: List[Dict[str, Any]],
    targets: Optional[List[str]] = None,
) -> Dict[str, Any]:
    """Read diary context, produce a preview JSON. Does NOT write any file."""
    context_block = ""
    for name, content in context_files.items():
        context_block += f"\n\n=== {name} ===\n{content[:4000]}"

    user_content = (
        f"日期：{date}\n"
        f"本次允许修改范围：{', '.join(targets or ['全部允许文件'])}\n"
        f"可修改的文件内容如下：{context_block}\n\n"
        f"用户修改要求：{instruction}"
    )

    if settings().codex_enabled:
        prompt = (
            _DEFAULT_DIARY_EDIT_SYSTEM
            + "\n\n"
            + user_content
        )
        raw = await asyncio.to_thread(_codex_exec, prompt)
        return _parse_preview_json(raw, date)

    if not settings().openai_api_key:
        return {
            "reply": "开发模式：已生成修改预览（占位）。",
            "changes": [
                {
                    "target": "diary_draft.md",
                    "before_summary": "当前草稿",
                    "after_summary": "按要求修改后的草稿（占位）",
                    "new_content": context_files.get("diary_draft.md", "# 日记草稿\n\n（占位内容）\n"),
                }
            ],
        }

    sys_msg = [{"role": "system", "content": _DEFAULT_DIARY_EDIT_SYSTEM}]
    messages: List[Dict[str, Any]] = list(history) + [{"role": "user", "content": user_content}]
    raw = await _chat(sys_msg + messages, json_mode=True)
    return _parse_preview_json(raw, date)


def _parse_preview_json(raw: str, date: str) -> Dict[str, Any]:
    try:
        data = json.loads(raw)
        return {
            "reply": str(data.get("reply", "")),
            "changes": [
                {
                    "target": str(c.get("target", "")),
                    "before_summary": str(c.get("before_summary", "")),
                    "after_summary": str(c.get("after_summary", "")),
                    "new_content": str(c.get("new_content", "")),
                }
                for c in data.get("changes", [])
                if isinstance(c, dict)
            ],
        }
    except (json.JSONDecodeError, AttributeError):
        return {"reply": raw, "changes": []}


async def generate_diary_json(date: str, source: str, word_count: int, extra: str = "") -> Dict[str, Any]:
    if settings().codex_enabled:
        system_prompt = _load_prompt("diary_generation.md", _DEFAULT_DIARY_GENERATION_PROMPT)
        context = _load_context()
        parts = [system_prompt, f"日期：{date}", f"目标字数：约 {word_count} 字"]
        if context:
            parts.append(f"## 通用背景\n{context}")
        parts.append(f"## 素材\n{source}")
        if extra:
            parts.append(f"## 用户补充说明\n{extra}")
        raw = await asyncio.to_thread(_codex_exec, "\n\n".join(parts))
        try:
            data = json.loads(raw)
            return {
                "date": data.get("date", date),
                "title": data.get("title", "专业实习日记"),
                "body_paragraphs": data.get("body_paragraphs", []),
                "safety_notes": data.get("safety_notes", []),
            }
        except (json.JSONDecodeError, AttributeError):
            return {
                "date": date,
                "title": "专业实习日记",
                "body_paragraphs": [raw],
                "safety_notes": ["模型返回内容不是有效 JSON，已原文保留，请人工检查。"],
            }

    if not settings().openai_api_key:
        return {
            "date": date,
            "title": "专业实习日记",
            "body_paragraphs": [
                "今天在指导老师的安排下，我结合实习现场记录，对相关工作流程进行了学习和梳理。",
                "在实践过程中，我重点关注操作步骤、注意事项以及问题处理方式，并将零散素材整理为较完整的学习记录。",
                "通过今天的实习，我进一步认识到规范记录、主动观察和及时复盘对提升专业实践能力的重要性。",
            ],
            "safety_notes": ["开发模式未调用模型，已使用安全占位草稿。"],
        }

    system_prompt = _load_prompt("diary_generation.md", _DEFAULT_DIARY_GENERATION_PROMPT)
    context = _load_context()
    user_parts = [f"日期：{date}", f"目标字数：约 {word_count} 字"]
    if context:
        user_parts.append(f"## 通用背景\n{context}")
    user_parts.append(f"## 素材\n{source}")
    if extra:
        user_parts.append(f"## 用户补充说明\n{extra}")
    messages = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": "\n\n".join(user_parts)},
    ]
    raw = await _chat(messages, json_mode=True)
    try:
        data = json.loads(raw)
        return {
            "date": data.get("date", date),
            "title": data.get("title", "专业实习日记"),
            "body_paragraphs": data.get("body_paragraphs", []),
            "safety_notes": data.get("safety_notes", []),
        }
    except (json.JSONDecodeError, AttributeError):
        return {
            "date": date,
            "title": "专业实习日记",
            "body_paragraphs": [raw],
            "safety_notes": ["模型返回内容不是有效 JSON，已原文保留，请人工检查。"],
        }


async def generate_report_markdown(
    report_type: str,
    start_date: str,
    end_date: str,
    source: str,
    word_count: int,
    extra: str = "",
) -> str:
    title = {
        "weekly": "实习周报",
        "monthly": "实习月报",
        "internship_summary": "实习总结",
    }.get(report_type, "实习报告")
    summary_required = "必须明确包含：任务、成果、收获、不足、展望。" if report_type == "internship_summary" else ""
    if settings().codex_enabled:
        system_prompt = _load_prompt("report_generation.md", _DEFAULT_REPORT_GENERATION_PROMPT)
        context = _load_context()
        parts = [system_prompt, f"报告类型：{title}", f"日期范围：{start_date} 至 {end_date}", f"目标字数：约 {word_count} 字"]
        if summary_required:
            parts.append(summary_required)
        if context:
            parts.append(f"## 通用背景\n{context}")
        parts.append(f"## 日期范围素材\n{source or '（无）'}")
        if extra:
            parts.append(f"## 用户补充说明\n{extra}")
        return await asyncio.to_thread(_codex_exec, "\n\n".join(parts))

    if not settings().openai_api_key:
        if report_type == "internship_summary":
            return (
                f"# {title}（{start_date} 至 {end_date}）\n\n"
                "## 实习任务\n\n"
                + (source[:1000] if source else "本周期暂无可聚合素材，请补充每日记录后重新生成。")
                + "\n\n## 实习成果\n\n结合日期范围内记录，对已完成工作和阶段性成果进行归纳。\n"
                "\n## 实习收获\n\n通过连续实习记录，进一步理解专业流程、协作方式和复盘方法。\n"
                "\n## 不足反思\n\n后续需要继续补充现场记录，完善问题定位和改进过程描述。\n"
                "\n## 后续展望\n\n下一阶段将围绕未完成任务持续实践，并按要求完善总结材料。\n"
            )
        return (
            f"# {title}（{start_date} 至 {end_date}）\n\n"
            "## 实习内容概述\n\n"
            + (source[:1000] if source else "本周期暂无可聚合素材，请补充每日记录后重新生成。")
            + "\n\n## 学习收获\n\n结合本周期记录，对实习流程和专业实践进行了阶段性复盘。\n"
            "\n## 问题与改进\n\n后续将继续补充现场记录，并按要求完善报告内容。\n"
        )

    system_prompt = _load_prompt("report_generation.md", _DEFAULT_REPORT_GENERATION_PROMPT)
    context = _load_context()
    user_parts = [f"报告类型：{title}", f"日期范围：{start_date} 至 {end_date}", f"目标字数：约 {word_count} 字"]
    if summary_required:
        user_parts.append(summary_required)
    if context:
        user_parts.append(f"## 通用背景\n{context}")
    user_parts.append(f"## 日期范围素材\n{source or '（无）'}")
    if extra:
        user_parts.append(f"## 用户补充说明\n{extra}")
    messages = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": "\n\n".join(user_parts)},
    ]
    return await _chat(messages)
