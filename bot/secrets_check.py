"""
Heuristic guard so passwords / PINs / codes never end up stored as "hints".
Telegram bot chats are NOT end-to-end encrypted, so anything secret belongs in the PassVault app only.
False positives are fine (the user rephrases); false negatives are what we try hard to avoid.
"""
from __future__ import annotations

import re

_EMAIL = re.compile(r"^[\w.+-]+@[\w-]+(\.[\w-]+)+$")
_URL = re.compile(r"^(https?://)?([\w-]+\.)+[a-z]{2,}(/\S*)?$", re.IGNORECASE)
_KEYWORDS = re.compile(
    r"\b(password|passwd|pwd|pass|passcode|pin|otp|cvv|cvc|mpin|upi pin|secret|recovery code|backup code|seed|mnemonic)\b\s*(is|=|:|-)",
    re.IGNORECASE,
)
_PUNCT = ".,;:!?()[]{}\"'«»“”‘’"


def _classes(word: str) -> int:
    return sum(
        (
            any(c.islower() for c in word),
            any(c.isupper() for c in word),
            any(c.isdigit() for c in word),
            any(not c.isalnum() for c in word),
        )
    )


def looks_like_secret(text: str) -> str | None:
    """Returns a short reason if the text looks like it contains a secret, else None."""
    if _KEYWORDS.search(text):
        return "it mentions a password/PIN/code followed by a value"
    for raw in text.split():
        word = raw.strip(_PUNCT)
        if not word or _EMAIL.match(word) or _URL.match(word):
            continue
        if re.fullmatch(r"\d{4,8}", word) and not re.fullmatch(r"(19|20)\d{2}", word):
            return f"“{word[:2]}…” looks like a PIN or code"
        if re.fullmatch(r"(\d{4}[ -]?){3}\d{4}", word) or re.fullmatch(r"\d{13,19}", word):
            return "it looks like a card number"
        if len(word) >= 8 and _classes(word) >= 3:
            return f"“{word[:2]}…” looks like a password"
        if len(word) >= 20 and re.fullmatch(r"[A-Za-z0-9_\-+/=]+", word):
            return "it looks like a key or token"
    # Seed phrases: 12/24 lowercase words and nothing else
    words = text.lower().split()
    if len(words) in (12, 15, 18, 21, 24) and all(w.isalpha() for w in words):
        return "it looks like a wallet recovery phrase"
    return None
