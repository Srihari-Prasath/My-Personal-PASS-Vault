"""Scheduled reminders: stale backups (weekly) and a master-password memory check (monthly)."""
from __future__ import annotations

import logging

from aiogram import Bot
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.cron import CronTrigger

from backup_status import human_age, latest_backup
from config import Config

log = logging.getLogger(__name__)


async def weekly_backup_check(bot: Bot, cfg: Config) -> None:
    owner = cfg.owner_chat_id
    if owner is None:
        return
    try:
        info = await latest_backup(bot, owner)
        if info is None:
            await bot.send_message(
                owner,
                "⚠️ <b>No vault backup found.</b>\n"
                "If you lose your phone, your passwords are gone. Open PassVault → "
                "<b>Settings → Backup &amp; restore → Back up now</b>.",
            )
        elif info.age_days > cfg.stale_days:
            await bot.send_message(
                owner,
                f"⚠️ Your last vault backup was <b>{human_age(info.age_days)}</b>.\n"
                "Open PassVault and tap <b>Back up now</b> (it only takes a second).",
            )
        else:
            log.info("Weekly check: backup is fresh")
    except Exception:
        log.exception("Weekly backup check failed")


async def monthly_password_check(bot: Bot, cfg: Config) -> None:
    owner = cfg.owner_chat_id
    if owner is None:
        return
    try:
        await bot.send_message(
            owner,
            "🧠 <b>Monthly check:</b> do you still remember your PassVault master password?\n\n"
            "Open the app and unlock by typing it (not with fingerprint). "
            "If you can't, it can't be recovered — find the paper copy you made and change it in Settings.",
        )
    except Exception:
        log.exception("Monthly reminder failed")


def start_scheduler(bot: Bot, cfg: Config) -> AsyncIOScheduler:
    scheduler = AsyncIOScheduler(timezone=cfg.tz)
    scheduler.add_job(
        weekly_backup_check,
        CronTrigger(day_of_week=cfg.weekly_day, hour=cfg.weekly_hour, minute=cfg.weekly_minute, timezone=cfg.tz),
        args=[bot, cfg],
        id="weekly_backup_check",
        misfire_grace_time=6 * 3600,  # laptop asleep at 21:00? still remind when it wakes up
        coalesce=True,
    )
    scheduler.add_job(
        monthly_password_check,
        CronTrigger(day=cfg.monthly_day, hour=10, minute=0, timezone=cfg.tz),
        args=[bot, cfg],
        id="monthly_password_check",
        misfire_grace_time=24 * 3600,
        coalesce=True,
    )
    scheduler.start()
    for job in scheduler.get_jobs():
        log.info("Scheduled %s — next run %s", job.id, job.next_run_time)
    return scheduler
