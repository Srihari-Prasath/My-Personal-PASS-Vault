"""Routers. `guard` must be included first: it handles groups before anything else."""
from __future__ import annotations

from aiogram import F, Router

from config import Config
from handlers import guard, hints, start, status


def build_routers(cfg: Config) -> list[Router]:
    if cfg.owner_chat_id is None:
        # Setup mode: only tell people their chat ID
        return [guard.router, start.setup_router]
    owner_only = [start.router, status.router, hints.router]
    for r in owner_only:
        # Everyone except the owner is silently ignored
        r.message.filter(F.chat.id == cfg.owner_chat_id)
    return [guard.router, *owner_only]
