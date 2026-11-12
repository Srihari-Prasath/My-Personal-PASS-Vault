"""
PassVault bot server (optional — backups and restores work without it).

    python bot.py

Adds: /status, /latest, non-secret /hint notes, weekly stale-backup reminder, monthly master-password check.
Privacy: answers only OWNER_CHAT_ID, leaves groups, never logs message text, never sees plaintext vault data.
"""
from __future__ import annotations

import asyncio
import logging
import sys

from aiogram import Bot, Dispatcher
from aiogram.client.default import DefaultBotProperties
from aiogram.enums import ParseMode
from aiogram.types import BotCommand, BotCommandScopeChat, BotCommandScopeDefault

import config
from db import HintsDB
from handlers import build_routers
from jobs import start_scheduler

COMMANDS = [
    BotCommand(command="status", description="When was the last backup?"),
    BotCommand(command="latest", description="Send the newest backup file again"),
    BotCommand(command="hint", description="Save a non-secret note"),
    BotCommand(command="hints", description="List / search notes"),
    BotCommand(command="delhint", description="Delete a note by number"),
    BotCommand(command="id", description="Show this chat's ID"),
    BotCommand(command="help", description="What this bot does"),
]


async def main() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )
    # aiogram's event log is fine (ids + timings only), but keep HTTP client chatter quiet
    logging.getLogger("aiohttp").setLevel(logging.WARNING)

    try:
        cfg = config.load()
    except config.ConfigError as e:
        print(f"Config error: {e}", file=sys.stderr)
        sys.exit(2)

    bot = Bot(cfg.bot_token, default=DefaultBotProperties(parse_mode=ParseMode.HTML))
    db = HintsDB(cfg.db_path)
    dp = Dispatcher(cfg=cfg, db=db)  # injected into handlers by parameter name
    dp.include_routers(*build_routers(cfg))

    me = await bot.get_me()
    if cfg.owner_chat_id is None:
        logging.warning("OWNER_CHAT_ID not set — SETUP MODE. Send any message to @%s to get your chat ID.", me.username)
        await bot.set_my_commands([], scope=BotCommandScopeDefault())
    else:
        # Command menu only in the owner's chat; nothing for anyone else
        await bot.set_my_commands([], scope=BotCommandScopeDefault())
        await bot.set_my_commands(COMMANDS, scope=BotCommandScopeChat(chat_id=cfg.owner_chat_id))
        start_scheduler(bot, cfg)
        logging.info("@%s running for owner chat %s", me.username, cfg.owner_chat_id)

    try:
        # Drop anything that queued up while the bot was offline (e.g. an old /start pairing code)
        await bot.delete_webhook(drop_pending_updates=True)
        await dp.start_polling(bot, allowed_updates=["message", "my_chat_member"])
    finally:
        db.close()
        await bot.session.close()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
