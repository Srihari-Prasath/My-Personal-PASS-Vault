"""
Reads backup status from the chat itself: the PassVault app pins its newest backup.
(Bots don't receive their own messages, so the pinned message is the source of truth.)
"""
from __future__ import annotations

import html
from dataclasses import dataclass
from datetime import datetime, timezone

from aiogram import Bot
from aiogram.types import Document


@dataclass(frozen=True)
class BackupInfo:
    message_id: int
    date: datetime
    caption: str
    document: Document

    @property
    def age_days(self) -> float:
        return (datetime.now(timezone.utc) - self.date).total_seconds() / 86400


async def latest_backup(bot: Bot, chat_id: int) -> BackupInfo | None:
    chat = await bot.get_chat(chat_id)
    pinned = chat.pinned_message
    if pinned is None or pinned.document is None:
        return None
    if not (pinned.document.file_name or "").endswith(".pvlt"):
        return None
    date = pinned.date if pinned.date.tzinfo else pinned.date.replace(tzinfo=timezone.utc)
    return BackupInfo(pinned.message_id, date, pinned.caption or "", pinned.document)


def human_age(days: float) -> str:
    if days < 1 / 24:
        return "just now"
    if days < 1:
        hours = int(days * 24)
        return f"{hours} hour{'s' if hours != 1 else ''} ago"
    if days < 2:
        return "yesterday"
    if days < 60:
        return f"{int(days)} days ago"
    months = int(days / 30)
    return f"{months} months ago"


def describe(info: BackupInfo, tz, stale_days: int) -> str:
    local = info.date.astimezone(tz).strftime("%d %b %Y, %H:%M")
    first_line = html.escape(info.caption.splitlines()[0]) if info.caption else "PassVault backup"
    size_kb = (info.document.file_size or 0) / 1024
    lines = [
        f"<b>Last backup:</b> {local} ({human_age(info.age_days)})",
        first_line,
        f"File: <code>{html.escape(info.document.file_name or '')}</code> · {size_kb:.0f} KB",
    ]
    if info.age_days > stale_days:
        lines.append(f"\n⚠️ Older than {stale_days} days — open PassVault and tap <b>Back up now</b>.")
    else:
        lines.append("\n✅ Up to date.")
    return "\n".join(lines)
