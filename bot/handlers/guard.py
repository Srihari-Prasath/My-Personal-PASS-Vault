"""The bot is private: it leaves any group/channel it gets added to and never answers there."""
from __future__ import annotations

import logging

from aiogram import Bot, F, Router
from aiogram.enums import ChatType
from aiogram.types import ChatMemberUpdated, Message

log = logging.getLogger(__name__)
router = Router(name="guard")


@router.my_chat_member(F.chat.type != ChatType.PRIVATE)
async def added_to_group(event: ChatMemberUpdated, bot: Bot) -> None:
    log.warning("Added to a non-private chat — leaving")
    try:
        await bot.leave_chat(event.chat.id)
    except Exception:  # already gone / no rights
        pass


@router.message(F.chat.type != ChatType.PRIVATE)
async def group_message(message: Message, bot: Bot) -> None:
    try:
        await bot.leave_chat(message.chat.id)
    except Exception:
        pass
