package com.personal.passvault

import com.personal.passvault.domain.Base32
import com.personal.passvault.domain.CardNetwork
import com.personal.passvault.domain.CsvImport
import com.personal.passvault.domain.CsvFormatException
import com.personal.passvault.domain.Totp
import com.personal.passvault.domain.TotpConfig
import com.personal.passvault.domain.WifiQr
import com.personal.passvault.domain.formatCardNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2FA codes, Wi-Fi QR, cards, CSV import and Google-sync matching. */
class FeaturesTest {

    // ---------------- TOTP: RFC 6238 Appendix B test vectors ----------------

    private fun cfg(asciiSecret: String, algo: String) =
        TotpConfig(secret = Base32.encode(asciiSecret.toByteArray()), digits = 8, period = 30, algorithm = algo)

    @Test
    fun totp_rfc6238_sha1() {
        val c = cfg("12345678901234567890", "SHA1")
        assertEquals("94287082", Totp.code(c, 59_000L))
        assertEquals("07081804", Totp.code(c, 1_111_111_109_000L))
        assertEquals("14050471", Totp.code(c, 1_111_111_111_000L))
        assertEquals("89005924", Totp.code(c, 1_234_567_890_000L))
        assertEquals("69279037", Totp.code(c, 2_000_000_000_000L))
        assertEquals("65353130", Totp.code(c, 20_000_000_000_000L))
    }

    @Test
    fun totp_rfc6238_sha256_sha512() {
        assertEquals("46119246", Totp.code(cfg("12345678901234567890123456789012", "SHA256"), 59_000L))
        assertEquals(
            "90693936",
            Totp.code(cfg("1234567890123456789012345678901234567890123456789012345678901234", "SHA512"), 59_000L),
        )
    }

    @Test
    fun totp_sixDigitsAndCountdown() {
        val c = TotpConfig(secret = "JBSWY3DPEHPK3PXP")
        assertEquals(6, Totp.code(c, 0L).length)
        assertEquals(30, Totp.secondsLeft(c, 0L))
        assertEquals(1, Totp.secondsLeft(c, 29_500L))
        assertEquals("123 456", Totp.pretty("123456"))
    }

    @Test
    fun base32_roundTrip_andLenientInput() {
        val bytes = "hello world!".toByteArray()
        val enc = Base32.encode(bytes)
        assertTrue(Base32.decode(enc).contentEquals(bytes))
        assertTrue(Base32.decode(enc.lowercase().chunked(4).joinToString(" ")).contentEquals(bytes))
    }

    @Test
    fun otpauth_parsing() {
        val t = Totp.parse("otpauth://totp/GitHub:octo%40example.com?secret=JBSWY3DPEHPK3PXP&issuer=GitHub&digits=6&period=30")
        assertNotNull(t)
        assertEquals("GitHub", t!!.issuer)
        assertEquals("octo@example.com", t.account)
        assertEquals("JBSWY3DPEHPK3PXP", t.secret)

        val noIssuerParam = Totp.parse("otpauth://totp/Google:me@gmail.com?secret=JBSWY3DPEHPK3PXP")
        assertEquals("Google", noIssuerParam!!.issuer)

        assertEquals("SHA256", Totp.parse("otpauth://totp/X?secret=JBSWY3DPEHPK3PXP&algorithm=sha256")!!.algorithm)
        assertNull(Totp.parse("otpauth://hotp/X?secret=JBSWY3DPEHPK3PXP&counter=1")) // HOTP not supported
        assertNull(Totp.parse("otpauth://totp/X?secret=not-base32!!"))
        assertNotNull(Totp.parse("jbsw y3dp ehpk 3pxp")) // bare key with spaces
        assertNull(Totp.parse("hello"))
    }

    // ---------------- Wi-Fi QR ----------------

    @Test
    fun wifiQr_roundTrip_withEscapes() {
        val text = WifiQr.format("My;Net", "p:a\\ss,word", "WPA", hidden = true)
        val parsed = WifiQr.parse(text)!!
        assertEquals("My;Net", parsed.ssid)
        assertEquals("p:a\\ss,word", parsed.password)
        assertEquals("WPA", parsed.security)
        assertTrue(parsed.hidden)
    }

    @Test
    fun wifiQr_openNetwork_andJunk() {
        val p = WifiQr.parse("WIFI:T:nopass;S:Cafe Guest;;")!!
        assertEquals("Cafe Guest", p.ssid)
        assertEquals("nopass", p.security)
        assertNull(WifiQr.parse("https://example.com"))
    }

    // ---------------- Cards ----------------

    @Test
    fun cards_networkLuhnFormat() {
        assertEquals(CardNetwork.VISA, CardNetwork.detect("4111111111111111"))
        assertEquals(CardNetwork.MASTERCARD, CardNetwork.detect("5555555555554444"))
        assertEquals(CardNetwork.AMEX, CardNetwork.detect("378282246310005"))
        assertEquals(CardNetwork.RUPAY, CardNetwork.detect("6521500000000000"))
        assertTrue(CardNetwork.luhnValid("4111 1111 1111 1111"))
        assertFalse(CardNetwork.luhnValid("4111 1111 1111 1112"))
        assertEquals("•••• •••• •••• 1111", formatCardNumber("4111111111111111", masked = true))
        assertEquals("3782 822463 10005", formatCardNumber("378282246310005", masked = false))
    }

    // ---------------- CSV import ----------------

    @Test
    fun csv_googlePasswordManager() {
        val csv = "name,url,username,password,note\n" +
            "Instagram,https://www.instagram.com/accounts/login/,octo,\"pa,ss\"\"word\",\n" +
            "com.whatsapp,android://abc123==@com.whatsapp/,+911234567890,secret,\"line1\nline2\"\n" +
            ",https://mail.google.com/,me@gmail.com,pw,\n"
        val r = CsvImport.import(csv)
        assertEquals("Google Password Manager / Chrome", r.source)
        assertEquals(3, r.logins.size)
        val ig = r.logins[0]
        assertEquals("instagram.com", ig.domain)
        assertEquals("pa,ss\"word", ig.password)
        val wa = r.logins[1]
        assertEquals("com.whatsapp", wa.packageName)
        assertEquals("line1\nline2", wa.notes)
        val gmail = r.logins[2]
        assertEquals("Mail", gmail.title)
        assertEquals("me@gmail.com", gmail.email)
        assertEquals("", gmail.username)
    }

    @Test
    fun csv_bitwarden_skipsNonLogins_readsTotp() {
        val csv = "folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp\r\n" +
            ",1,login,GitHub,,,0,https://github.com,octo,hunter2,otpauth://totp/GitHub:octo?secret=JBSWY3DPEHPK3PXP\r\n" +
            ",,note,My note,secret text,,0,,,,\r\n"
        val r = CsvImport.import(csv)
        assertEquals("Bitwarden", r.source)
        assertEquals(1, r.logins.size)
        assertEquals(1, r.skippedRows)
        assertNotNull(r.logins[0].totp)
        assertEquals("github.com", r.logins[0].domain)
    }

    // ---------------- Sync from Google: matching ----------------

    private fun imported(title: String, user: String, pw: String, domain: String? = null, pkg: String? = null) =
        com.personal.passvault.domain.ImportedLogin(title, user, "", pw, "", domain, pkg, null)

    private fun existing(id: String, title: String, user: String, pw: String, domains: List<String> = emptyList(), pkgs: List<String> = emptyList()) =
        com.personal.passvault.domain.ExistingLogin(id, title, user, pw, domains, pkgs)

    @Test
    fun matcher_newSameChanged() {
        val vault = listOf(
            existing("ig", "Instagram", "octo", "old-pw", domains = listOf("instagram.com")),
            existing("gm", "Gmail", "me@gmail.com", "same-pw", domains = listOf("google.com")),
            existing("wa", "WhatsApp", "+91123", "wa-pw", pkgs = listOf("com.whatsapp")),
        )
        val file = listOf(
            imported("instagram.com", "OCTO", "new-pw", domain = "instagram.com"),         // changed (case-insensitive user)
            imported("accounts.google.com", "me@gmail.com", "same-pw", domain = "accounts.google.com"), // same (subdomain)
            imported("com.whatsapp", "+91123", "wa-pw-2", pkg = "com.whatsapp"),           // changed via app package
            imported("GitHub", "octo", "gh", domain = "github.com"),                        // new
            imported("Instagram", "someone-else", "x", domain = "instagram.com"),           // new (different user)
        )
        val m = com.personal.passvault.domain.ImportMatcher.match(file, vault)
        assertEquals(com.personal.passvault.domain.MatchKind.CHANGED, m[0].kind); assertEquals("ig", m[0].entryId)
        assertEquals(com.personal.passvault.domain.MatchKind.SAME, m[1].kind)
        assertEquals(com.personal.passvault.domain.MatchKind.CHANGED, m[2].kind); assertEquals("wa", m[2].entryId)
        assertEquals(com.personal.passvault.domain.MatchKind.NEW, m[3].kind)
        assertEquals(com.personal.passvault.domain.MatchKind.NEW, m[4].kind)
    }

    @Test
    fun matcher_sameEntryIsUpdatedOnlyOnce() {
        val vault = listOf(existing("ig", "Instagram", "octo", "old", domains = listOf("instagram.com")))
        val file = listOf(
            imported("Instagram", "octo", "first", domain = "instagram.com"),
            imported("Instagram", "octo", "second", domain = "www.instagram.com"),
        )
        val m = com.personal.passvault.domain.ImportMatcher.match(file, vault)
        assertEquals(com.personal.passvault.domain.MatchKind.CHANGED, m[0].kind)
        assertEquals(com.personal.passvault.domain.MatchKind.SAME, m[1].kind)
    }

    @Test(expected = CsvFormatException::class)
    fun csv_withoutPasswordColumn_isRejected() {
        CsvImport.import("a,b,c\n1,2,3\n")
    }
}
