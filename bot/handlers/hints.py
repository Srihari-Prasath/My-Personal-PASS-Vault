"""Non-secret notes: which email/phone an account uses, where a hardware key is kept, etc."""
from __future__ import annotations

import html
from datetime import datetime

from aiogram import Bot, Router
from aiogram.filters import Command, CommandObject
from aiogram.types import Message

from config import Config
from db import HintsDB
from secrets_check import looks_like_secret

router = Router(name="hints")


@router.message(Command("hint"))
async def add_hint(message: Message, command: CommandObject, bot: Bot, db: HintsDB) -> None:
    text = (command.args or "").strip()
    if not text:
        await message.answer("Usage: <code>/hint Netflix → work email</code>")
        return
    reason = looks_like_secret(text)
    if reason:
        # Remove the message so the secret doesn't sit in the chat history
        try:
            await bot.delete_message(message.chat.id, message.message_id)
            removed = " I deleted your message."
        except Exception:
            removed = " Please delete your message."
        await message.answer(
            f"🚫 Not saved — {html.escape(reason)}.{removed}\n\n"
            "Hints must never contain passwords, PINs or codes (this chat isn't end-to-end encrypted). "
            "Save secrets in the PassVault app; keep only reminders here, e.g. <i>“SBI → registered on dad's phone”</i>."
        )
        return
    try:
        db.add(text)
    except ValueError as e:
        await message.answer(html.escape(str(e)))
        return
    await message.answer(f"📝 Saved hint #{db.count()}: {html.escape(text)}")


@router.message(Command("hints"))
async def list_hints(message: Message, command: CommandObject, db: HintsDB, cfg: Config) -> None:
    query = (command.args or "").strip()
    all_hints = db.all()
    if not all_hints:
        await message.answer("No hints yet. Add one: <code>/hint Netflix → work email</code>")
        return
    numbered = list(enumerate(all_hints, start=1))
    if query:
        q = query.lower()
        numbered = [(n, h) for n, h in numbered if q in h.text.lower()]
        if not numbered:
            await message.answer(f"No hints matching “{html.escape(query)}”.")
            return
    lines = [
        f"<b>{n}.</b> {html.escape(h.text)} <i>({datetime.fromtimestamp(h.created_at, cfg.tz):%d %b %Y})</i>"
        for n, h in numbered
    ]
    # Telegram messages max out at 4096 chars — send in chunks
    chunk: list[str] = []
    size = 0
    for line in lines:
        if size + len(line) > 3500 and chunk:
            await message.answer("\n".join(chunk))
            chunk, size = [], 0
        chunk.append(line)
        size += len(line) + 1
    if chunk:
        await message.answer("\n".join(chunk))


@router.message(Command("delhint"))
async def delete_hint(message: Message, command: CommandObject, db: HintsDB) -> None:
    arg = (command.args or "").strip()
    if not arg.isdigit():
        await message.answer("Usage: <code>/delhint 3</code> (numbers are shown in /hints)")
        return
    removed = db.delete_by_position(int(arg))
    if removed is None:
        await message.answer(f"There's no hint #{arg}. See /hints.")
        return
    await message.answer(f"🗑 Deleted: {html.escape(removed.text)}")
