from __future__ import annotations

from aiogram import Bot, Router
from aiogram.filters import Command
from aiogram.types import Message

from backup_status import describe, latest_backup
from config import Config

router = Router(name="status")

NO_BACKUP = (
    "No backup found yet.\n\n"
    "In PassVault: <b>Settings → Backup &amp; restore → Back up now</b>. "
    "The newest backup gets pinned in this chat."
)


@router.message(Command("status"))
async def status(message: Message, bot: Bot, cfg: Config) -> None:
    info = await latest_backup(bot, message.chat.id)
    await message.answer(NO_BACKUP if info is None else describe(info, cfg.tz, cfg.stale_days))


@router.message(Command("latest"))
async def latest(message: Message, bot: Bot) -> None:
    """Re-sends the pinned backup at the bottom of the chat (handy to save it to Files / Drive)."""
    info = await latest_backup(bot, message.chat.id)
    if info is None:
        await message.answer(NO_BACKUP)
        return
    await bot.send_document(
        message.chat.id,
        document=info.document.file_id,
        caption=(info.caption or "PassVault backup") + "\n\n(Copy of the pinned backup)",
        parse_mode=None,
    )
