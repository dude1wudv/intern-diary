import sqlite3

from .auth import UserContext
from .paths import user_root

SCHEMA = """
create table if not exists entries(
  id text primary key,
  date text not null,
  type text not null,
  created_at text not null,
  content text,
  filename text,
  note text,
  status text not null default 'stored',
  exclude_from_diary integer not null default 0
);
"""


def conn(user: UserContext) -> sqlite3.Connection:
    db = user_root(user) / "intern_diary.sqlite3"
    db.parent.mkdir(parents=True, exist_ok=True)
    c = sqlite3.connect(db)
    c.row_factory = sqlite3.Row
    c.executescript(SCHEMA)
    return c
