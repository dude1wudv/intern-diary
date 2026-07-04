from functools import lru_cache
from pathlib import Path
from pydantic import BaseModel, Field
import os


class Settings(BaseModel):
    data_dir: Path = Field(default_factory=lambda: Path(os.getenv("DATA_DIR", "./data")))
    template_path: Path = Field(
        default_factory=lambda: Path(
            os.getenv("TEMPLATE_PATH", "../templates/diary_template_working.docx")
        )
    )
    api_token: str = Field(default_factory=lambda: os.getenv("API_TOKEN", "dev-token"))
    openai_api_key: str = Field(default_factory=lambda: os.getenv("OPENAI_API_KEY", ""))
    openai_base_url: str = Field(
        default_factory=lambda: os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1")
    )
    model: str = Field(default_factory=lambda: os.getenv("OPENAI_MODEL", "gpt-5.5"))
    # Image (vision) description is deferred: the current proxy returns 502 for
    # image content. Keep off until a vision-capable upstream is wired up; when
    # off, uploaded images are archived with a placeholder description instead
    # of failing the upload. See design doc section "视觉识图（暂缓）".
    vision_enabled: bool = Field(
        default_factory=lambda: os.getenv("VISION_ENABLED", "0").lower() in {"1", "true", "yes"}
    )
    codex_enabled: bool = Field(
        default_factory=lambda: os.getenv("CODEX_ENABLED", "0").lower() in {"1", "true", "yes"}
    )
    codex_command: str = Field(default_factory=lambda: os.getenv("CODEX_COMMAND", "codex"))
    codex_timeout_seconds: int = Field(
        default_factory=lambda: int(os.getenv("CODEX_TIMEOUT_SECONDS", "180"))
    )


@lru_cache
def settings() -> Settings:
    s = Settings()
    s.data_dir.mkdir(parents=True, exist_ok=True)
    return s
