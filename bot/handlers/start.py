from __future__ import annotations

from aiogram import Router
from aiogram.filters import Command, CommandObject, CommandStart
from aiogram.types import Message

router = Router(name="start")
setup_router = Router(name="setup")

HELP = (
    "<b>PassVault bot</b> 🔐\n"
    "Your encrypted vault backups land in this chat (the newest is pinned). "
    "Only your master password can open them.\n\n"
    "/status — when was the last backup?\n"
    "/latest — send the newest backup file again\n"
    "/hint <i>text</i> — save a non-secret note, e.g. <code>/hint Netflix → work email</code>\n"
    "/hints — list notes (add a word to search: <code>/hints bank</code>)\n"
    "/delhint <i>n</i> — delete note number n\n"
    "/id — show this chat's ID\n\n"
    "⚠️ Never send passwords, PINs or codes here — bot chats aren't end-to-end encrypted. "
    "They belong in the PassVault app."
)


@router.message(CommandStart())
async def start(message: Message, command: CommandObject) -> None:
    if command.args:
        # "/start 483920" = the PassVault app pairing. While this server runs, the app can't see the
        # message itself, so give the chat ID to type in.
        await message.answer(
            "👋 Looks like you're connecting PassVault.\n\n"
            f"In the app tap <b>Enter chat ID instead</b> and type:\n<code>{message.chat.id}</code>"
        )
        return
    await message.answer(HELP)


@router.message(Command("help"))
async def help_(message: Message) -> None:
    await message.answer(HELP)


@router.message(Command("id"))
async def chat_id(message: Message) -> None:
    await message.answer(f"This chat's ID: <code>{message.chat.id}</code>")


@setup_router.message()
async def setup_mode(message: Message) -> None:
    """OWNER_CHAT_ID isn't set yet: tell the (first) user their ID and how to finish setup."""
    await message.answer(
        "Setup mode 🛠\n\n"
        f"Your chat ID is <code>{message.chat.id}</code>.\n"
        "Put <code>OWNER_CHAT_ID=" f"{message.chat.id}" "</code> in the bot's <code>.env</code> file and restart it. "
        "After that the bot answers only you."
    )
