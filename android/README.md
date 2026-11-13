# PassVault — Android app

The Android app: Kotlin, Jetpack Compose, Hilt, Room + SQLCipher. See the [main README](../README.md) for the overview
and [docs/DESIGN.md](../docs/DESIGN.md) for how it works inside.

## Requirements
- Android Studio with **AGP 9**, **Kotlin 2.2**, Gradle 9.1 (wrapper included), **JDK 21**
- A phone or emulator on **Android 9 (API 28)** or newer — fingerprint unlock needs a strong biometric

## Build and run
1. Android Studio → **Open** → this `android/` folder.
2. Run the `app` configuration.
3. To judge smoothness, install a **release** build:
   ```bash
   ./gradlew :app:installRelease
   ```
   Release builds are minified and signed with the debug key so they can be installed on my own phone.
   Use a real signing config before distributing anything.

## Release signing
Release builds use my own key when one is configured, and fall back to the debug key otherwise.

- **Local:** create `android/keystore.properties` (ignored by git):
  ```properties
  storeFile=release.jks
  storePassword=…
  keyAlias=passvault
  keyPassword=…
  ```
- **GitHub Actions:** the release workflow reads the repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
  `KEY_ALIAS` and `KEY_PASSWORD`.
- The version comes from the git tag (`v1.2.3` → versionName `1.2.3`, versionCode `10203`).

Keep `release.jks` and its passwords safe and backed up — without the same key, future versions can't be installed
over the old one.

## Tests
```bash
./gradlew :app:testDebugUnitTest
```

| Test class | Covers |
|---|---|
| `CryptoTest` | Argon2id, AES-GCM round trips and tamper detection, password generator, domain parsing |
| `BackupCodecTest` | `.pvlt` format: opens with the master password only, rejects tampering and bad parameters |
| `FeaturesTest` | RFC 6238 TOTP vectors, `otpauth://`, Base32, Wi-Fi QR, cards, CSV import, Google-sync matching |

## Code map

| Area | Where |
|---|---|
| Key derivation, encryption, Keystore, vault/backup/decoy keys | `core/crypto/` |
| Auto-lock and the in-memory vault key | `core/session/VaultSession.kt` |
| Encrypted database, migrations | `data/db/` |
| Password history, bulk import, autofill queries | `data/repo/EntryRepository.kt` |
| Telegram backup and restore | `data/backup/`, `data/telegram/`, `work/BackupUploadWorker.kt` |
| App and website icons | `data/icons/` |
| 2FA, CSV import, generator | `domain/` |
| Autofill service | `autofill/` |
| Reminders | `work/ReminderWorker.kt` |
| Screens, theme, motion | `ui/` |

## Notes
- Screenshots of the app are blocked on purpose (`FLAG_SECURE`).
- The master password can't be recovered — not from the phone and not from a backup.
- Database schema changes need a Room migration (see `MIGRATION_1_2` in `data/db/VaultDatabase.kt`) so existing
  vaults keep their data.
