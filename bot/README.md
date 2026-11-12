# PassVault — bot server (optional)

Backups and restores work **without** this: the Android app talks to the Telegram Bot API directly, and reminders run
inside the app. This small Python server only adds chat commands to my bot. It has to run on a machine that is always
on (a Raspberry Pi, a small VPS, or Google Cloud's free e2-micro in a US region).

## What it adds

| Command | What it does |
|---|---|
| `/status` | When the last backup was made (reads the pinned backup); warns if it's older than 7 days |
| `/latest` | Sends the newest backup file again at the bottom of the chat |
| `/hint Netflix → work email` | Saves a **non-secret** note |
| `/hints [word]` · `/delhint 3` | List / search / delete notes |
| `/id` · `/help` | Chat ID · command list |

Scheduled: a weekly "backup is old or missing" message and a monthly "do you still remember your master password?".

## Privacy rules
- Answers **only** `OWNER_CHAT_ID`; everyone else is ignored. It leaves any group it is added to.
- `/hint` refuses anything that looks like a password, PIN, OTP, card number, key or wallet recovery phrase, and deletes
  that message — bot chats are not end-to-end encrypted.
- Never sees vault contents: backups are encrypted on the phone before they reach Telegram.
- Never logs message text. Notes live in a local SQLite file.
- In @BotFather: `/setjoingroups` → **Disable**.

## Run it
Needs Python 3.10+.

```bash
cd bot
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
cp .env.example .env        # put BOT_TOKEN in it
.venv/bin/python bot.py     # first run = setup mode
```
1. Send any message to the bot — it replies with the chat ID.
2. Put it in `.env` as `OWNER_CHAT_ID=…` and restart.

To keep it running: `deploy/passvault-bot.service` (systemd, Raspberry Pi / VPS) or `deploy/com.passvault.bot.plist`
(macOS launchd, only while the Mac is awake).

## Connecting the app while the server runs
The server reads the bot's messages, so the app can't see the *Start* tap during pairing. The bot answers it with the
chat ID instead — in the app tap **Enter chat ID instead** and type it. Pairing before starting the server works
automatically.

## Tests
```bash
.venv/bin/python -m unittest discover -s tests -v
```
