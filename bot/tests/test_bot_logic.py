"""Tests for the parts that don't need Telegram: secret detection and hint storage."""
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from db import HintsDB  # noqa: E402
from secrets_check import looks_like_secret  # noqa: E402


class SecretDetectionTest(unittest.TestCase):
    def assertSecret(self, text):
        self.assertIsNotNone(looks_like_secret(text), f"should be refused: {text!r}")

    def assertOk(self, text):
        self.assertIsNone(looks_like_secret(text), f"should be allowed: {text!r}")

    def test_refuses_passwords(self):
        self.assertSecret("instagram Hunter2024!")
        self.assertSecret("gmail pw Tr0ub4dor&3")
        self.assertSecret("password: sunshine")
        self.assertSecret("my pin is 4321")
        self.assertSecret("UPI PIN - 1234")
        self.assertSecret("otp 482913")

    def test_refuses_pins_cards_tokens(self):
        self.assertSecret("SBI 1234")
        self.assertSecret("card 4111 1111 1111 1111")
        self.assertSecret("4111111111111111")
        # Obviously fake values (real-looking provider keys would trip GitHub's secret scanning)
        self.assertSecret("api key notarealkeyjustfortestingonly1234")
        self.assertSecret("000000000:FAKE-test-token-not-real-xxxxxxxx")

    def test_refuses_seed_phrases(self):
        self.assertSecret("abandon ability able about above absent absorb abstract absurd abuse access accident")

    def test_allows_normal_hints(self):
        self.assertOk("Netflix → work email")
        self.assertOk("SBI registered on dad's phone")
        self.assertOk("Amazon uses someone.alt@gmail.com")
        self.assertOk("Instagram 2FA codes are in PassVault since 2024")
        self.assertOk("Hardware key is in the blue drawer")
        self.assertOk("Bank login at https://onlinesbi.sbi/retail")
        self.assertOk("Phone +91 9876543210 for HDFC alerts")


class HintsDBTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.db = HintsDB(Path(self.tmp.name) / "t.sqlite3")

    def tearDown(self):
        self.db.close()
        self.tmp.cleanup()

    def test_add_list_search_delete(self):
        self.db.add("Netflix → work email")
        self.db.add("SBI → dad's phone")
        self.db.add("Gmail → recovery phone is old SIM")
        self.assertEqual(3, self.db.count())
        self.assertEqual(["Netflix → work email", "SBI → dad's phone", "Gmail → recovery phone is old SIM"],
                         [h.text for h in self.db.all()])
        self.assertEqual(1, len(self.db.search("sbi")))

        removed = self.db.delete_by_position(2)
        self.assertEqual("SBI → dad's phone", removed.text)
        self.assertEqual(2, self.db.count())
        self.assertIsNone(self.db.delete_by_position(9))
        self.assertIsNone(self.db.delete_by_position(0))

    def test_long_hint_is_truncated(self):
        hint = self.db.add("x" * 1000)
        self.assertEqual(HintsDB.MAX_LEN, len(hint.text))


if __name__ == "__main__":
    unittest.main()
