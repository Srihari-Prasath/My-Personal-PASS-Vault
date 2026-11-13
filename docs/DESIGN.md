# PassVault — Design

This document explains how PassVault works inside: the security model, how data is stored, how backups and
restores work, and why I made the trade-offs I made.

**Contents**
1. [Principles](#1-principles)
2. [Threat model](#2-threat-model)
3. [Keys and encryption](#3-keys-and-encryption)
4. [The Android app](#4-the-android-app)
5. [Telegram backups](#5-telegram-backups)
6. [Bot server (optional)](#6-bot-server-optional)
7. [Extras](#7-extras)
8. [Google Password Manager](#8-google-password-manager)
9. [Testing](#9-testing)
10. [Before trusting it with real passwords](#10-before-trusting-it-with-real-passwords)

---

## 1. Principles

1. **Plaintext never leaves the phone.** Telegram, the bot and any file I export only ever contain encrypted data.
2. **One master password.** It opens everything and cannot be recovered — by the app or by me.
3. **Fingerprint is a shortcut, not a replacement.** The master password always works, and the app asks for it every
   7 days and after a reboot so I don't forget it.
4. **No homemade crypto.** Only standard building blocks: Argon2id, AES-256-GCM, Android Keystore, SQLCipher.
5. **Fail closed.** If decryption fails or data was tampered with, show an error — never partial or garbage data.

---

## 2. Threat model

| Threat | Covered | How |
|---|:---:|---|
| Phone lost or stolen (locked) | ✅ | Vault encrypted at rest, keys in Keystore, auto-lock |
| Someone picks up the unlocked phone | ✅ | App's own lock, auto-lock after 30 s in background, optional confirm-to-reveal |
| Telegram account or bot hacked | ✅ | Backups encrypted with a key derived from the master password |
| Bot token leaked | ✅ | Only encrypted files can be reached with it |
| Screenshots, screen recording, Recents preview | ✅ | `FLAG_SECURE` |
| Other apps reading the clipboard | ✅ mostly | Marked sensitive (Android 13+), cleared after 30 s |
| Google cloud backup copying the database | ✅ | `allowBackup=false` + data-extraction rules exclude everything |
| Autofill phishing by a look-alike app | ✅ | Apps matched by exact package; websites trusted only from known browsers |
| Being forced to unlock | ✅ | Decoy vault (separate password, separate database) |
| Forgetting the master password | ❌ | Impossible by design → emergency kit on paper |
| Rooted phone with malware while unlocked | ❌ | Out of scope |

---

## 3. Keys and encryption

### 3.1 Key hierarchy

```
Master password ──Argon2id(salt A)──► Password key ──┐
                                                     ├──► unwraps ──► Vault key (random 256-bit) ──► SQLCipher DB
Keystore AES key (fingerprint-bound) ────────────────┘

Master password ──Argon2id(salt B)──► Backup key ──► AES-256-GCM ──► .pvlt backup file
                                     (also stored wrapped by the vault key)
```

- **Vault key** — random, generated once with `SecureRandom`, used as the SQLCipher raw key. Stored only in wrapped form:
  by the password key (master-password unlock) and by a Keystore key (fingerprint unlock).
- **Password key** — `Argon2id(master, salt A, 45 MiB, 2 passes, 1 lane) → 32 bytes`. The parameters are stored next to
  the salt, so they can be raised later.
- **Backup key** — a separate Argon2id derivation with its own salt. A backup therefore opens on **any** phone with just
  the master password. It is also stored wrapped by the vault key, so backups work after a fingerprint unlock too.
- **Keystore key** — AES/GCM, `setUserAuthenticationRequired(true)`, `setInvalidatedByBiometricEnrollment(true)`,
  StrongBox when available, used through `BiometricPrompt` + `CryptoObject`. Enrolling a new fingerprint invalidates it;
  the app then falls back to the master password and lets me turn fingerprint on again.

### 3.2 On disk

| File | Contents |
|---|---|
| `vault.db` | SQLCipher-encrypted Room database |
| `vault2.db` | Decoy vault (only if set up), its own key |
| `vault_meta.json` | Version, salts, Argon2 parameters, wrapped keys — no plaintext secrets |
| `telegram_token.bin` | Bot token, encrypted with a separate Keystore key (no fingerprint needed, so background uploads work) |
| DataStore | Non-secret settings: timeouts, theme, chat ID, last backup time |

### 3.3 Memory and lock
- The vault key lives only in `VaultSession` while unlocked; locking wipes it (`fill(0)`) and closes the database.
- Auto-lock after the chosen time in the background (immediately / 30 s / 1 / 5 / 15 min). It is briefly held while
  I'm in a file picker, Telegram or the phone's PIN screen on purpose.
- Wrong attempts: Argon2id makes each guess slow, and after 5 wrong tries there is a growing delay (30 s → 1 min → 5 min).

### 3.4 Decoy vault
A second password opens a separate database. Its key is wrapped by `Argon2id(decoy password, salt A)` — the **same**
salt as the real password — so one derivation per unlock tries both and a real and a decoy unlock take the same time.
The decoy key is also wrapped by the real vault key, so changing the master password keeps the decoy entries (the app
then asks me to set the decoy password again).

While the decoy is open: nothing is uploaded to Telegram, the bot can't be changed, fingerprint settings don't touch
the real keys, and the decoy setting is hidden. Fingerprint always opens the real vault.

---

## 4. The Android app

### 4.1 Stack
Kotlin · Jetpack Compose (Material 3) · Navigation Compose · Hilt · Coroutines/Flow · Room + SQLCipher · DataStore ·
WorkManager · OkHttp · CameraX + ZXing · Bouncy Castle (Argon2id) · `androidx.biometric`. Min SDK 28.

### 4.2 Packages

```
com.personal.passvault
├── core/crypto/    Argon2Kdf · AesGcm · KeystoreManager · VaultKeyManager · BackupCodec
├── core/session/   VaultSession (vault key in memory, auto-lock)
├── core/security/  ClipboardHelper
├── data/db/        Room entities, DAOs, DatabaseProvider (SQLCipher), migrations
├── data/repo/      EntryRepository (history, import, autofill queries) · SettingsRepository
├── data/backup/    BackupManager (build, upload, download, merge/replace)
├── data/telegram/  TelegramClient (Bot API) · TokenStore
├── data/icons/     installed-app icons · website icons · IconLoader
├── data/qr/        QR decode/encode
├── data/security/  BreachChecker (HIBP k-anonymity)
├── domain/         models · payloads · TOTP · CSV import · generator · strength
├── autofill/       Autofill service, unlock + save activities
├── work/           BackupUploadWorker · ReminderWorker
├── widget/         home-screen widget + generator sheet
└── ui/             setup · unlock · vault · detail · edit · scan · generator · security · import · kit · backup · settings
```

### 4.3 Data model (inside SQLCipher)

```
entries          id · type · title · username · email · password · url · notes · tags · favorite
                 createdAt · updatedAt · passwordChangedAt · payload (JSON: 2FA / card / Wi-Fi)
password_history id · entryId · password · usedFrom · replacedAt · note
recovery_codes   id · entryId · code · used · usedAt · position · setReplacedAt (null = current set)
linked_targets   id · entryId · kind (app|web) · packageName · domain · label · position
web_icons        domain · png · fetchedAt · failed
```

Entry types: **Login, Recovery codes, 2FA codes, Card, Wi-Fi, Secure note.**

### 4.4 Password history
- Any save where the password actually changed writes the old one to history first, with the period it was in use.
- Restoring an old password moves the current one into history — nothing is ever lost.
- Recovery codes: a completely new set archives the old set ("Older code sets") instead of deleting it.
- Reuse warnings: a password I used before on the same entry, or one another entry uses.

### 4.5 Icons
- **Apps:** real launcher icons via `PackageManager` (visible through a `<queries>` launcher filter, not
  `QUERY_ALL_PACKAGES`).
- **Websites:** fetched straight from the site (`apple-touch-icon` → `<link rel=icon>` → `/favicon.ico`), never via a
  third-party icon service, then stored inside the encrypted database.
- Priority: installed app → website icon → gradient letter avatar.

### 4.6 UI and motion
- Spring physics everywhere; one shared motion spec in `ui/theme/Motion.kt`; respects "Remove animations".
- Push/pop with parallax, predictive back, shared-element avatar morph from list to detail.
- Swipe rows (copy / favourite) with haptic ticks, long-press menu over a blurred background, collapsing headers,
  animated password reveal, live clipboard countdown pill, shake on a wrong password.
- Performance: immutable UI state, stable list keys, icons decoded off the main thread and cached in memory.

### 4.7 Hardening
`FLAG_SECURE` · no Google backup · HTTPS only · no analytics or crash reporters · release builds strip logging ·
sensitive clipboard flag · Keystore-backed keys · only `INTERNET`, `USE_BIOMETRIC`, `CAMERA` (asked when scanning) and
`POST_NOTIFICATIONS`.

---

## 5. Telegram backups

### 5.1 Why a bot
I wanted backups that live outside the phone without creating another cloud account. A Telegram bot I own is
free, always available, and only ever receives files that are already encrypted.

### 5.2 Connecting
1. Create a bot with @BotFather and paste its token into the app (verified with `getMe`).
2. The app shows a `https://t.me/<bot>?start=<code>` button; I press **Start** in Telegram.
3. The app sees `/start <code>` through `getUpdates`, stores the chat ID and sends a confirmation message.
4. Fallback: type the chat ID manually (needed when the bot server is running, since it consumes updates).

### 5.3 File format (`.pvlt`)

```
magic "PVLT" (4) · version (1) · Argon2 memory (4) · passes (4) · lanes (1) · salt (16) · nonce (12)
ciphertext + GCM tag = AES-256-GCM( gzip(JSON) ), AAD = all header bytes
```
The header is authenticated, so changing even the salt or KDF parameters makes the file fail to open. Absurd KDF
parameters are refused before any work is done.

### 5.4 When backups happen
- **Automatically:** every change encrypts a fresh backup while the vault is unlocked and stores it as a pending file;
  `BackupUploadWorker` uploads it a minute later when online (exponential retry). Several quick edits become one backup.
- **Manually:** *Back up now* or *Export encrypted backup* to any file.
- The newest backup message is pinned (the previous one unpinned) so a new phone can always find it.

### 5.5 Restoring
- **New phone:** *I have a backup* → connect the bot → download the pinned backup (or pick a file) → master password →
  the vault is created with that password and filled from the backup.
- **Existing vault:** **Merge** (the newer version of each entry wins; any password that would be lost goes into
  history) or **Replace** (the vault becomes exactly the backup).
- After changing the master password, a new backup is made at once; older backups still need the old password.

---

## 6. Bot server (optional)

Backups and restores work without it — the app talks to the Bot API directly. The Python server in [`bot/`](../bot/)
adds chat commands and must run on an always-on machine.

| Command | What it does |
|---|---|
| `/status` | Date of the pinned backup; warns if older than 7 days |
| `/latest` | Sends the pinned backup again |
| `/hint` · `/hints` · `/delhint` | Non-secret notes; anything that looks like a password, PIN, OTP, card or token is refused and deleted |
| `/id` · `/help` | Chat ID · command list |

Rules: answers only `OWNER_CHAT_ID`, leaves any group, never logs message text, command menu only in my chat.
Without `OWNER_CHAT_ID` it runs in setup mode and just replies with the chat ID.

The reminders the bot used to send (stale backup, master-password check) now live **in the app** as local notifications,
so nothing needs to be hosted for them.

---

## 7. Extras

### 7.1 2FA codes and QR scanning
- RFC 6238 TOTP (SHA1/256/512, 6–8 digits), `otpauth://` parsing, either as its own entry or attached to a login.
- CameraX + ZXing scanner (on-device), or "scan from a screenshot"; also reads `WIFI:` QR codes.

### 7.2 Cards and Wi-Fi
- Cards: live formatting, network detection, Luhn check, flip view (CVV on the back), PIN; expiry warnings in the
  security check.
- Wi-Fi: password stored in the normal password field, so it gets history and reuse checks; share via QR.

### 7.3 Security check
Score ring plus weak, reused and over-a-year-old passwords, low recovery codes and expiring cards.
The **breach check** uses *Have I Been Pwned* with k-anonymity (`Add-Padding: true`): only the first 5 characters of each
password's SHA-1 hash are sent, and it runs only after an explanation dialog each time.

### 7.4 CSV import
Google Password Manager / Chrome (`android://…@package` URLs become real app links), Bitwarden and generic CSVs.
Preview first, one transaction, one backup afterwards, then an offer to delete the plain-text CSV.

### 7.5 Autofill (optional)
Apps are matched by exact package name; websites only when the request comes from a known browser. When the vault is
locked, the dropdown shows *Unlock PassVault*. After a manual sign-in Android offers *Save to PassVault?* — an existing
entry gets the new password and the old one goes into history. The dropdown never shows passwords.

### 7.6 Confirm to reveal
Optional: fingerprint or phone PIN before showing or copying passwords, history, codes, card details, secure notes or
the Wi-Fi QR. One confirmation covers 30 seconds; locking resets it.

### 7.7 Emergency kit
A one-page A4 PDF drawn on the phone (`PdfDocument`) and sent straight to Android printing: restore steps with my bot's
name, a box to hand-write the master password (never printed), selected recovery codes and optional 2FA keys.

### 7.8 Reminders and widget
- Local notifications (WorkManager, every 12 h, throttled): vault not backed up or uploads failing, master password not
  typed for 30 days, monthly sync from Google.
- Widget: *Lock now* and a password generator that works without unlocking.

---

## 8. Google Password Manager

Android allows only one autofill service, and I keep Google for that because it syncs to my laptop.
Android doesn't let apps read Google Password Manager, and an app can't save logins for *other* apps or sites into it
(that needs a privileged permission only browsers get). So the sync is an export:

passwords.google.com → Export → PassVault → *Import / sync from Google*. Rows are matched by username plus website or app
(subdomains count), falling back to the entry name. **New** → added, **changed** → password updated (old one into
history), **same** → skipped. A monthly reminder keeps it regular.

**Later:** PassVault as an Android credential provider (passwords and passkeys shown next to Google's), and encrypted
Credential Exchange import/export with Google Password Manager, including passkeys.

---

## 9. Testing

**Unit tests** (`android/app/src/test`, `bot/tests`)
- Argon2id + AES-GCM round trips; wrong password and every flipped byte are rejected
- `.pvlt`: opens with the master password only; tampered header or ciphertext rejected; absurd parameters refused
- RFC 6238 test vectors (SHA1, SHA256, SHA512), `otpauth://` parsing, Base32
- CSV formats (Google, Bitwarden), Google-sync matching, Wi-Fi QR, card networks and Luhn
- Bot: secret detection and hint storage

**On a real device**
- Keystore + fingerprint wrap/unwrap, SQLCipher opens only with the right key, auto-lock timing.

**Disaster drill**
1. Add a few test entries → back up to Telegram.
2. Uninstall the app.
3. Reinstall → *I have a backup* → bot + master password → everything is back, history included.
4. A wrong master password is refused.
5. A `.pvlt` file with one changed byte is refused.

---

## 10. Before trusting it with real passwords

- [ ] Disaster drill passed on my phone.
- [ ] Master password written on paper and kept at home — not in photos or notes.
- [ ] Emergency kit printed, with my most important recovery codes.
- [ ] First real backup visible and pinned in Telegram.
- [ ] Telegram account has 2-Step Verification on.
