from datetime import datetime, timezone

from .paths import DEFAULT_USER, day_dir


def audit(date: str, user=DEFAULT_USER, action: str = "", detail: str = "") -> None:
    if isinstance(user, str):
        action, detail, user = user, action, DEFAULT_USER
    line = f"{datetime.now(timezone.utc).isoformat()}\t{action}\t{detail}\n"
    with (day_dir(date, user) / "audit.log").open("a", encoding="utf-8") as f:
        f.write(line)

