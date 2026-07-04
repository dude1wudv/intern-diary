from dataclasses import dataclass
import json
import re
from pathlib import Path
from typing import Any

from fastapi import Header, HTTPException

from .config import settings

_WORKSPACE_RE = re.compile(r"^[A-Za-z0-9_-]{1,64}$")


@dataclass(frozen=True)
class UserContext:
    token: str
    workspace: str
    name: str = ""
    class_name: str = ""
    student_id: str = ""

    @property
    def profile_values(self) -> dict[str, str]:
        return {
            "{{姓名}}": self.name,
            "{{班级}}": self.class_name,
            "{{学号}}": self.student_id,
        }


def _load_users() -> dict[str, Any]:
    path = settings().data_dir / "users.json"
    if not path.exists():
        return {}
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        raise HTTPException(status_code=500, detail="bad users config") from None
    return data.get("tokens", data) if isinstance(data, dict) else {}


def _workspace(value: Any) -> str:
    workspace = str(value or "default").strip()
    if not _WORKSPACE_RE.fullmatch(workspace):
        raise HTTPException(status_code=500, detail="bad workspace config")
    return workspace


async def require_user(authorization: str = Header(default="")) -> UserContext:
    prefix = "Bearer "
    if not authorization.startswith(prefix):
        raise HTTPException(status_code=401, detail="unauthorized")
    token = authorization[len(prefix):]
    users = _load_users()
    if token in users:
        raw = users[token] or {}
        return UserContext(
            token=token,
            workspace=_workspace(raw.get("workspace")),
            name=str(raw.get("name", "")),
            class_name=str(raw.get("class", raw.get("class_name", ""))),
            student_id=str(raw.get("student_id", "")),
        )
    if token == settings().api_token:
        return UserContext(token=token, workspace="default")
    raise HTTPException(status_code=401, detail="unauthorized")
