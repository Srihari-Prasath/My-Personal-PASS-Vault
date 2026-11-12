"""Settings from .env (never commit the real .env — it holds the bot token)."""
from __future__ import annotations

import os
import re
from dataclasses import dataclass
from pathlib import Path
from zoneinfo import ZoneInfo

from dotenv import load_dotenv

BASE_DIR = Path(__file__).resolve().parent
TOKEN_SHAPE = re.compile(r"^\d{5,15}:[A-Za-z0-9_-]{30,50}$")
DAYS = {"mon", "tue", "wed", "thu", "fri", "sat", "sun"}


class ConfigError(Exception):
    pass


@dataclass(frozen=True)
class Config:
    bot_token: str
    owner_chat_id: int | None  # None = setup mode: the bot only tells you your chat ID
    tz: ZoneInfo
    stale_days: int
    weekly_day: str
    weekly_hour: int
    weekly_minute: int
    monthly_day: int
    db_path: Path


def _time(value: str, name: str) -> tuple[int, int]:
    m = re.fullmatch(r"(\d{1,2}):(\d{2})", value.strip())
    if not m or int(m[1]) > 23 or int(m[2]) > 59:
        raise ConfigError(f"{name} must look like 21:00, got {value!r}")
    return int(m[1]), int(m[2])


def load() -> Config:
    load_dotenv(BASE_DIR / ".env")

    token = os.environ.get("BOT_TOKEN", "").strip()
    if not TOKEN_SHAPE.match(token):
        raise ConfigError("BOT_TOKEN is missing or doesn't look like a bot token (123456789:AAH…). Copy it from @BotFather into .env")

    owner_raw = os.environ.get("OWNER_CHAT_ID", "").strip()
    owner = None
    if owner_raw:
        if not re.fullmatch(r"-?\d+", owner_raw):
            raise ConfigError("OWNER_CHAT_ID must be a number")
        owner = int(owner_raw)

    day = os.environ.get("WEEKLY_REMINDER_DAY", "sun").strip().lower()[:3]
    if day not in DAYS:
        raise ConfigError(f"WEEKLY_REMINDER_DAY must be one of {sorted(DAYS)}")
    hour, minute = _time(os.environ.get("WEEKLY_REMINDER_TIME", "21:00"), "WEEKLY_REMINDER_TIME")

    monthly_day = int(os.environ.get("MONTHLY_REMINDER_DAY", "1"))
    if not 1 <= monthly_day <= 28:
        raise ConfigError("MONTHLY_REMINDER_DAY must be 1–28")

    return Config(
        bot_token=token,
        owner_chat_id=owner,
        tz=ZoneInfo(os.environ.get("TIMEZONE", "Asia/Kolkata")),
        stale_days=int(os.environ.get("STALE_AFTER_DAYS", "7")),
        weekly_day=day,
        weekly_hour=hour,
        weekly_minute=minute,
        monthly_day=monthly_day,
        db_path=BASE_DIR / os.environ.get("DB_FILE", "passvault_bot.sqlite3"),
    )
