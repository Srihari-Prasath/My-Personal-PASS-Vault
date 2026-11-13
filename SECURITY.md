# Security policy

PassVault holds passwords, so I take security reports seriously.

## Reporting a vulnerability

**Please don't open a public issue for security problems.**

Use GitHub's private reporting instead: **Security → Report a vulnerability** on this repository.
I'll reply as soon as I can, and once it's fixed I'm happy to credit you in the release notes if you'd like.

Helpful details:
- Affected version (Settings → About in the app) and Android version
- What an attacker could do, and what they need (physical access, another installed app, network position…)
- Steps to reproduce or a small proof of concept

## Supported versions

| Version | Supported |
|---|---|
| Latest release | ✅ |
| Older releases | ❌ — please update first |

## Scope

In scope:
- The Android app (`android/`) — encryption, key handling, backups, autofill, anything that could leak vault data
- The `.pvlt` backup format and restore logic
- The optional bot server (`bot/`)

Out of scope:
- Attacks that need a rooted phone or malware running while the vault is unlocked
- Someone who already knows the master password
- Telegram's own infrastructure

## How PassVault protects data

The security model — key hierarchy, threat model and backup format — is described in
[docs/DESIGN.md](docs/DESIGN.md). In short: Argon2id + AES-256-GCM, SQLCipher, Android Keystore,
and backups that are encrypted on the phone before they ever reach Telegram.

PassVault hasn't had an independent security audit.
