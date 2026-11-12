"""Tiny SQLite store for NON-secret hints ("Netflix → work email"). Single user, so plain sqlite3 is plenty."""
from __future__ import annotations

import sqlite3
import time
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Hint:
    id: int
    text: str
    created_at: int


class HintsDB:
    MAX_HINTS = 500
    MAX_LEN = 300

    def __init__(self, path: Path):
        self._conn = sqlite3.connect(path)
        self._conn.execute(
            "CREATE TABLE IF NOT EXISTS hints ("
            " id INTEGER PRIMARY KEY AUTOINCREMENT,"
            " text TEXT NOT NULL,"
            " created_at INTEGER NOT NULL)"
        )
        self._conn.commit()

    def add(self, text: str) -> Hint:
        text = text.strip()[: self.MAX_LEN]
        if self.count() >= self.MAX_HINTS:
            raise ValueError(f"You already have {self.MAX_HINTS} hints — delete some first")
        now = int(time.time())
        cur = self._conn.execute("INSERT INTO hints (text, created_at) VALUES (?, ?)", (text, now))
        self._conn.commit()
        return Hint(cur.lastrowid, text, now)

    def all(self) -> list[Hint]:
        rows = self._conn.execute("SELECT id, text, created_at FROM hints ORDER BY created_at, id").fetchall()
        return [Hint(*r) for r in rows]

    def search(self, query: str) -> list[Hint]:
        like = f"%{query.strip()}%"
        rows = self._conn.execute(
            "SELECT id, text, created_at FROM hints WHERE text LIKE ? ORDER BY created_at, id", (like,)
        ).fetchall()
        return [Hint(*r) for r in rows]

    def delete_by_position(self, position: int) -> Hint | None:
        """Delete the n-th hint as numbered in /hints (1-based)."""
        hints = self.all()
        if not 1 <= position <= len(hints):
            return None
        hint = hints[position - 1]
        self._conn.execute("DELETE FROM hints WHERE id = ?", (hint.id,))
        self._conn.commit()
        return hint

    def count(self) -> int:
        return self._conn.execute("SELECT COUNT(*) FROM hints").fetchone()[0]

    def close(self) -> None:
        self._conn.close()
