from pathlib import Path
from typing import Dict, List

from docx import Document
from docx.oxml import OxmlElement
from docx.text.paragraph import Paragraph

from .config import settings


def _copy_run_format(paragraph, like) -> None:
    if not like or not paragraph.runs or not like.runs:
        return
    src = like.runs[0].font
    dst = paragraph.runs[0].font
    dst.name = src.name
    dst.size = src.size
    dst.bold = src.bold
    dst.italic = src.italic
    dst.underline = src.underline


def _set_text(paragraph, text: str, like=None) -> None:
    for run in list(paragraph.runs):
        run.text = ""
    if paragraph.runs:
        paragraph.runs[0].text = text
    else:
        paragraph.add_run(text)
    _copy_run_format(paragraph, like)


def _insert_after(paragraph, text: str, like=None):
    new = OxmlElement("w:p")
    paragraph._p.addnext(new)
    p = Paragraph(new, paragraph._parent)
    p.style = paragraph.style
    if like is not None:
        p.style = like.style
    _set_text(p, text, like)
    return p


def _replace(paragraph, values: Dict[str, str]) -> bool:
    text = paragraph.text
    if not any(k in text for k in values):
        return False
    for k, v in values.items():
        text = text.replace(k, v)
    _set_text(paragraph, text)
    return True


def _delete(paragraph) -> None:
    paragraph._element.getparent().remove(paragraph._element)


def render_docx(date: str, title: str, body: List[str], values: Dict[str, str], out: Path) -> Path:
    doc = Document(str(settings().template_path))
    template_samples = [
        p for p in doc.paragraphs
        if p.text in {"标题1", "标题2", "标题3", "标题4"} or p.text.startswith("这是正文的示例")
    ]
    title_like = next((p for p in doc.paragraphs if p.text == "标题1"), None)
    body_like = next((p for p in doc.paragraphs if p.text.startswith("这是正文的示例")), None)
    name = values.get("{{姓名}}", "")
    klass = values.get("{{班级}}", "")
    student_id = values.get("{{学号}}", "")
    merged = dict(values)
    merged.update(
        {
            "{{日期}}": date,
            "{{日记标题}}": title,
            "{{正文}}": "\n".join(body),
            "张三": name,
            "微电子XXXX": klass,
            "XXXXXXXXXX": student_id,
            "XXXX年XX月XX日": date,
        }
    )
    used_title_placeholder = used_body_placeholder = False
    for p in doc.paragraphs:
        before = p.text
        if "{{日记标题}}" in before:
            _set_text(p, before.replace("{{日记标题}}", title), title_like)
            used_title_placeholder = True
            continue
        if "{{正文}}" in before:
            paragraphs = body or [""]
            _set_text(p, before.replace("{{正文}}", paragraphs[0]), body_like)
            anchor = p
            for text in paragraphs[1:]:
                anchor = _insert_after(anchor, text, body_like)
            used_body_placeholder = True
            continue
        changed = _replace(p, merged)
        used_title_placeholder = used_title_placeholder or "{{日记标题}}" in before
        used_body_placeholder = used_body_placeholder or "{{正文}}" in before
        if changed:
            continue
        if before == "标题1":
            _set_text(p, title, title_like)
            used_title_placeholder = True
        elif before.startswith("这是正文的示例") and not used_body_placeholder:
            _set_text(p, "\n".join(body), body_like)
            used_body_placeholder = True
    if used_title_placeholder and used_body_placeholder:
        for p in list(template_samples):
            if p.text not in {"标题1", "标题2", "标题3", "标题4"} and not p.text.startswith("这是正文的示例"):
                continue
            _delete(p)
    out.parent.mkdir(parents=True, exist_ok=True)
    doc.save(str(out))
    return out
