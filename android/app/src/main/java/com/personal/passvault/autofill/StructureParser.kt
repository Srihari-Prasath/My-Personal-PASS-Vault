package com.personal.passvault.autofill

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId

/** What we found on the screen that asked for autofill. */
data class ParsedScreen(
    val packageName: String,
    val webDomain: String?,
    val usernameIds: List<AutofillId>,
    val passwordIds: List<AutofillId>,
    val otpIds: List<AutofillId>,
    val usernameValue: String?,
    val passwordValue: String?,
) {
    val hasFields: Boolean get() = usernameIds.isNotEmpty() || passwordIds.isNotEmpty() || otpIds.isNotEmpty()
    val allIds: List<AutofillId> get() = usernameIds + passwordIds + otpIds
}

/**
 * Walks the view tree an app/browser exposes to Autofill and classifies text fields as username, password or
 * one-time code — using autofill hints first, then input types, HTML attributes and finally id/hint words.
 */
object StructureParser {

    private enum class Kind { USERNAME, PASSWORD, OTP, OTHER }

    private class Field(val id: AutofillId, val kind: Kind, val value: String?)

    fun parse(structure: AssistStructure): ParsedScreen {
        val fields = mutableListOf<Field>()
        var webDomain: String? = null
        fun visit(node: AssistStructure.ViewNode) {
            if (webDomain == null && !node.webDomain.isNullOrBlank()) webDomain = node.webDomain
            classify(node)?.let { fields += it }
            for (i in 0 until node.childCount) visit(node.getChildAt(i))
        }
        for (i in 0 until structure.windowNodeCount) visit(structure.getWindowNodeAt(i).rootViewNode)

        val passwords = fields.filter { it.kind == Kind.PASSWORD }
        var usernames = fields.filter { it.kind == Kind.USERNAME }
        // No obvious username field? Use the text field right before the first password field.
        if (usernames.isEmpty() && passwords.isNotEmpty()) {
            val firstPw = fields.indexOf(passwords.first())
            fields.subList(0, firstPw).lastOrNull { it.kind == Kind.OTHER }?.let { usernames = listOf(it) }
        }
        val otps = fields.filter { it.kind == Kind.OTP }
        return ParsedScreen(
            packageName = structure.activityComponent?.packageName.orEmpty(),
            webDomain = webDomain,
            usernameIds = usernames.map { it.id },
            passwordIds = passwords.map { it.id },
            otpIds = otps.map { it.id },
            usernameValue = usernames.firstNotNullOfOrNull { it.value?.takeIf(String::isNotBlank) },
            passwordValue = passwords.firstNotNullOfOrNull { it.value?.takeIf(String::isNotEmpty) },
        )
    }

    private fun classify(node: AssistStructure.ViewNode): Field? {
        val id = node.autofillId ?: return null
        if (node.autofillType != View.AUTOFILL_TYPE_TEXT) return null
        if (node.visibility != View.VISIBLE) return null

        val hints = node.autofillHints.orEmpty().map { it.lowercase() }
        val html = node.htmlInfo
        val attrs = html?.attributes.orEmpty().associate { it.first.lowercase() to it.second.orEmpty().lowercase() }
        if (html != null && html.tag.lowercase() != "input") return null
        val htmlType = attrs["type"].orEmpty()
        if (htmlType in setOf("hidden", "submit", "button", "checkbox", "radio", "search")) return null
        val autocomplete = attrs["autocomplete"].orEmpty()
        val words = listOfNotNull(node.idEntry, node.hint?.toString(), attrs["name"], attrs["id"], attrs["placeholder"], attrs["aria-label"])
            .joinToString(" ").lowercase()

        val inputType = node.inputType
        val cls = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val textPassword = cls == InputType.TYPE_CLASS_TEXT && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        )
        val numberPassword = cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        val emailType = cls == InputType.TYPE_CLASS_TEXT && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
        )

        val kind = when {
            hints.any { "otp" in it || "one-time" in it || it == "smsotpcode" } || autocomplete == "one-time-code" ||
                Regex("\\b(otp|totp|2fa|mfa|one.?time|verification.?code|auth.?code)\\b").containsMatchIn(words) -> Kind.OTP
            hints.any { "password" in it } || htmlType == "password" || autocomplete.contains("password") ||
                textPassword || numberPassword || Regex("pass(word|wd|code)?\\b|pwd").containsMatchIn(words) -> Kind.PASSWORD
            hints.any { it in setOf("username", "emailaddress", "email", "phone", "phonenumber") } ||
                autocomplete in setOf("username", "email", "tel") || htmlType == "email" || emailType ||
                Regex("user|email|e-mail|login|account|phone|mobile").containsMatchIn(words) -> Kind.USERNAME
            else -> Kind.OTHER
        }
        val value = node.autofillValue?.takeIf { it.isText }?.textValue?.toString()
        return Field(id, kind, value)
    }
}

/** Only these apps are trusted to tell us which WEBSITE is open (anti-phishing). */
object Browsers {
    private val packages = setOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary", "org.chromium.chrome",
        "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix", "org.mozilla.focus",
        "com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.beta",
        "com.brave.browser", "com.microsoft.emmx", "com.opera.browser", "com.opera.mini.native",
        "com.duckduckgo.mobile.android", "com.vivaldi.browser", "com.kiwibrowser.browser",
        "com.mi.globalbrowser", "com.google.android.googlequicksearchbox",
    )

    fun isBrowser(packageName: String) = packageName in packages
}
