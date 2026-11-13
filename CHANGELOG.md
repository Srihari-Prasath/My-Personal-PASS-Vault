# Changelog

All notable changes to PassVault. Versions follow [Semantic Versioning](https://semver.org).

## [1.0.0] - 2026-11-13

First public release.

### Vault
- Encrypted vault on the phone: SQLCipher database, Argon2id key derivation, AES-256-GCM, Android Keystore
- Master password + optional fingerprint unlock, auto-lock, screenshot blocking, clipboard auto-clear
- Entry types: logins, recovery codes, 2FA codes, cards, Wi-Fi, secure notes
- Full password history with restore; real app icons and website icons

### Backups
- Encrypted `.pvlt` backups to your own Telegram bot — automatic after changes, or on demand
- Restore on a new phone with the bot and the master password; merge or replace on an existing vault
- Export / import encrypted backup files

### Extras
- Built-in authenticator (TOTP) with QR scanning from the camera or a screenshot
- Security check: weak, reused and old passwords, low recovery codes, expiring cards, optional breach check
- Import / sync from Google Password Manager, Chrome and Bitwarden CSV exports
- Optional autofill service, home-screen widget, emergency kit PDF
- Confirm-before-reveal, decoy vault, backup and master-password reminders
