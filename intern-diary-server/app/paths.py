import re
from pathlib import Path

from .auth import UserContext
from .config import settings

DEFAULT_USER = UserContext(token="", workspace="default")


def user_root(user: UserContext = DEFAULT_USER) -> Path:
    if user.workspace == "default":
        return settings().data_dir
    return settings().data_dir / "users" / user.workspace


def day_dir(date: str, user: UserContext = DEFAULT_USER) -> Path:
    if not re.match(r"^\d{4}-\d{2}-\d{2}$", date):
        raise ValueError("date must be YYYY-MM-DD")
    p = user_root(user) / "workdays" / date
    for child in [p, p / "images", p / "image_descriptions", p / "exports"]:
        child.mkdir(parents=True, exist_ok=True)
    return p


def day_path(date: str, user: UserContext = DEFAULT_USER) -> Path:
    if not re.match(r"^\d{4}-\d{2}-\d{2}$", date):
        raise ValueError("date must be YYYY-MM-DD")
    return user_root(user) / "workdays" / date


def report_dir(report_id: str, user: UserContext = DEFAULT_USER, *, create: bool = True) -> Path:
    if not re.match(r"^[A-Za-z0-9_-]+$", report_id):
        raise ValueError("bad report id")
    p = user_root(user) / "reports" / report_id
    if create:
        p.mkdir(parents=True, exist_ok=True)
    return p
