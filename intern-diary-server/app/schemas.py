from typing import Optional

from pydantic import BaseModel, Field


class TextEntryIn(BaseModel):
    date: str = Field(pattern=r"^\d{4}-\d{2}-\d{2}$")
    content: str = Field(min_length=1, max_length=5000)
    exclude_from_diary: bool = False


class SortIn(BaseModel):
    date: str = Field(pattern=r"^\d{4}-\d{2}-\d{2}$")
    extra_instruction: str = ""


class GenerateIn(BaseModel):
    date: str = Field(pattern=r"^\d{4}-\d{2}-\d{2}$")
    word_count: int = 800
    extra_instruction: str = ""


class ReportGenerateIn(BaseModel):
    type: str = Field(pattern=r"^(weekly|monthly|internship_summary)$")
    start_date: str = Field(pattern=r"^\d{4}-\d{2}-\d{2}$")
    end_date: str = Field(pattern=r"^\d{4}-\d{2}-\d{2}$")
    template_id: Optional[str] = None
    word_count: Optional[int] = None
    extra_instruction: str = ""


class AssistantMessageIn(BaseModel):
    role: str = Field(pattern=r"^(user|assistant)$")
    content: str = Field(min_length=1, max_length=8000)


class AssistantChatIn(BaseModel):
    messages: list[AssistantMessageIn] = Field(min_length=1, max_length=50)


class DiaryEditPreviewIn(BaseModel):
    date: str = Field(pattern=r"^\d{4}-\d{2}-\d{2}$")
    instruction: str = Field(min_length=1, max_length=4000)
    messages: list[AssistantMessageIn] = Field(default_factory=list, max_length=50)
    targets: list[str] = Field(default_factory=list, max_length=3)


class DiaryEditConfirmIn(BaseModel):
    preview_id: str = Field(min_length=1, max_length=64)
