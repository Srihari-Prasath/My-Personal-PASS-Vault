package com.personal.passvault.domain

/** One login read from a password-manager CSV export. */
data class ImportedLogin(
    val title: String,
    val username: String,
    val email: String,
    val password: String,
    val notes: String,
    val domain: String?,
    val packageName: String?,
    val totp: TotpConfig?,
)

data class CsvImportResult(val logins: List<ImportedLogin>, val skippedRows: Int, val source: String)

class CsvFormatException(message: String) : Exception(message)

/** An existing PassVault login, reduced to what matching needs. */
data class ExistingLogin(
    val id: String,
    val title: String,
    val user: String,
    val password: String,
    val domains: List<String>,
    val packages: List<String>,
)

enum class MatchKind { NEW, SAME, CHANGED }

data class ImportMatch(val kind: MatchKind, val entryId: String? = null)

/**
 * Pairs each imported login with an existing entry: same username AND same website/app (subdomains count),
 * falling back to same name. Same password -> SAME (nothing to do), different -> CHANGED (update),
 * no match -> NEW (add). Pure JVM — unit tested.
 */
object ImportMatcher {

    fun match(imported: List<ImportedLogin>, existing: List<ExistingLogin>): List<ImportMatch> {
        val claimed = HashSet<String>()
        return imported.map { l ->
            val user = norm(l.username.ifBlank { l.email })
            val sameUser = existing.filter { norm(it.user) == user }
            val bySite = sameUser.firstOrNull { e ->
                (l.domain != null && e.domains.any { sameSite(it, l.domain) }) ||
                    (l.packageName != null && l.packageName in e.packages)
            }
            val found = bySite ?: sameUser.firstOrNull { norm(it.title) == norm(l.title) }
            when {
                found == null -> ImportMatch(MatchKind.NEW)
                found.password == l.password || !claimed.add(found.id) -> ImportMatch(MatchKind.SAME, found.id)
                else -> ImportMatch(MatchKind.CHANGED, found.id)
            }
        }
    }

    private fun norm(s: String) = s.trim().lowercase()

    /** "accounts.google.com" ~ "google.com" (either direction). */
    private fun sameSite(a: String, b: String) = a == b || a.endsWith(".$b") || b.endsWith(".$a")
}

/**
 * Reads CSV exports from Google Password Manager / Chrome (`name,url,username,password,note`),
 * Bitwarden (`folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp`)
 * and anything else with recognisable column names. Pure JVM — unit tested.
 */
object CsvImport {

    private val TITLE = setOf("name", "title", "account", "item name", "entry")
    private val URL = setOf("url", "login_uri", "website", "uri", "web site", "login url", "origin_url", "site")
    private val USER = setOf("username", "login_username", "user name", "login", "user", "userid")
    private val EMAIL = setOf("email", "e-mail", "email address", "mail")
    private val PASSWORD = setOf("password", "login_password", "pass", "pwd")
    private val NOTES = setOf("note", "notes", "extra", "comments", "comment")
    private val TOTP = setOf("login_totp", "totp", "otp", "otpauth", "2fa")
    private val TYPE = setOf("type")

    fun import(text: String): CsvImportResult {
        val rows = parseCsv(text.removePrefix("﻿"))
        if (rows.size < 2) throw CsvFormatException("The file has no rows to import")
        val header = rows.first().map { it.trim().lowercase() }
        fun col(names: Set<String>) = header.indexOfFirst { it in names }

        val iTitle = col(TITLE)
        val iUrl = col(URL)
        val iUser = col(USER)
        val iEmail = col(EMAIL)
        val iPassword = col(PASSWORD)
        val iNotes = col(NOTES)
        val iTotp = col(TOTP)
        val iType = col(TYPE)
        if (iPassword < 0) throw CsvFormatException("Couldn't find a \"password\" column. Is this a password export?")

        val source = when {
            "login_password" in header -> "Bitwarden"
            header.take(5) == listOf("name", "url", "username", "password", "note") -> "Google Password Manager / Chrome"
            else -> "CSV"
        }

        var skipped = 0
        val logins = rows.drop(1).mapNotNull { row ->
            fun at(i: Int) = if (i in row.indices) row[i].trim() else ""
            if (row.all { it.isBlank() }) return@mapNotNull null
            val type = at(iType).lowercase()
            if (iType >= 0 && type.isNotEmpty() && type != "login") { skipped++; return@mapNotNull null }

            val password = row.getOrNull(iPassword).orEmpty() // keep spaces inside passwords exactly
            var username = at(iUser)
            var email = at(iEmail)
            if (iEmail < 0 && '@' in username && ' ' !in username) { email = username; username = "" }
            val notes = at(iNotes)
            if (password.isEmpty() && username.isEmpty() && email.isEmpty() && notes.isEmpty()) { skipped++; return@mapNotNull null }

            val url = at(iUrl)
            val pkg = androidPackage(url)
            val domain = if (pkg == null && url.isNotEmpty()) Domains.normalize(url) else null
            val title = at(iTitle).ifBlank { domain?.substringBefore('.')?.replaceFirstChar { it.uppercaseChar() } ?: pkg ?: "Imported login" }

            ImportedLogin(
                title = title,
                username = username,
                email = email,
                password = password,
                notes = notes,
                domain = domain,
                packageName = pkg,
                totp = at(iTotp).takeIf { it.isNotEmpty() }?.let { Totp.parse(it) },
            )
        }
        return CsvImportResult(logins, skipped, source)
    }

    /** Chrome stores app logins as `android://<cert-hash>@com.example.app/`. */
    fun androidPackage(url: String): String? {
        if (!url.startsWith("android://", ignoreCase = true)) return null
        val pkg = url.substringAfter("@", "").substringBefore('/').trim()
        return pkg.takeIf { it.matches(Regex("^[a-zA-Z][\\w]*(\\.[a-zA-Z_][\\w]*)+$")) }
    }

    /** RFC 4180: commas, quotes ("" escapes), and newlines inside quoted fields. */
    fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else inQuotes = false
                } else field.append(c)
            } else when (c) {
                '"' -> inQuotes = true
                ',' -> { row.add(field.toString()); field.clear() }
                '\r' -> Unit
                '\n' -> { row.add(field.toString()); field.clear(); rows.add(row); row = mutableListOf() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows
    }
}
