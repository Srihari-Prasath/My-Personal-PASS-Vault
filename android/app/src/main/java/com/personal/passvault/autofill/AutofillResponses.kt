package com.personal.passvault.autofill

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.service.autofill.SaveInfo
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.personal.passvault.R
import com.personal.passvault.data.db.EntryEntity
import com.personal.passvault.domain.EntryPayload
import com.personal.passvault.domain.Totp
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Builds the dropdown the system shows under a login field. */
object AutofillResponses {

    const val EXTRA_PACKAGE = "pv_package"
    const val EXTRA_DOMAIN = "pv_domain"
    const val EXTRA_SAVE_TOKEN = "pv_save_token"
    private const val MAX_SUGGESTIONS = 6

    private fun presentation(context: Context, title: String, subtitle: String) =
        RemoteViews(context.packageName, R.layout.autofill_item).apply {
            setTextViewText(R.id.autofill_title, title)
            setTextViewText(R.id.autofill_subtitle, subtitle)
        }

    /** Vault is open: one suggestion per matching entry (+ "save" offer). */
    @Suppress("DEPRECATION") // Dataset.Builder(RemoteViews)/setValue: fine on all supported versions
    fun unlocked(context: Context, screen: ParsedScreen, entries: List<EntryEntity>): FillResponse? {
        val builder = FillResponse.Builder()
        var count = 0
        for (e in entries.sortedByDescending { it.updatedAt }.take(MAX_SUGGESTIONS)) {
            val user = e.username.ifBlank { e.email }
            val totp = EntryPayload.parse(e.payload).totp
            val ds = Dataset.Builder(presentation(context, e.title, user.ifBlank { "PassVault" }))
            var filled = false
            if (user.isNotEmpty()) screen.usernameIds.forEach { ds.setValue(it, AutofillValue.forText(user)); filled = true }
            if (e.password.isNotEmpty()) screen.passwordIds.forEach { ds.setValue(it, AutofillValue.forText(e.password)); filled = true }
            if (totp != null) screen.otpIds.forEach { ds.setValue(it, AutofillValue.forText(Totp.code(totp))); filled = true }
            if (filled) { builder.addDataset(ds.build()); count++ }
        }
        val save = saveInfo(screen)
        if (save != null) builder.setSaveInfo(save)
        return if (count == 0 && save == null) null else builder.build()
    }

    /** Vault is locked: a single "Unlock PassVault" item that opens our unlock screen. */
    fun locked(context: Context, screen: ParsedScreen, matchPackage: String?, domain: String?): FillResponse {
        val intent = Intent(context, AutofillAuthActivity::class.java)
            .putExtra(EXTRA_PACKAGE, matchPackage)
            .putExtra(EXTRA_DOMAIN, domain)
        // Must be MUTABLE: the system adds the screen structure to this intent
        val flags = PendingIntent.FLAG_CANCEL_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        val sender = PendingIntent.getActivity(context, 4711, intent, flags).intentSender
        val builder = FillResponse.Builder()
            .setAuthentication(
                screen.allIds.toTypedArray(),
                sender,
                presentation(context, "🔒 Unlock PassVault", domain ?: "Fill your saved login"),
            )
        saveInfo(screen)?.let { builder.setSaveInfo(it) }
        return builder.build()
    }

    /** Lets Android offer "Save to PassVault?" after the user signs in by hand. */
    private fun saveInfo(screen: ParsedScreen): SaveInfo? {
        if (screen.passwordIds.isEmpty()) return null
        val type = SaveInfo.SAVE_DATA_TYPE_PASSWORD or
            (if (screen.usernameIds.isNotEmpty()) SaveInfo.SAVE_DATA_TYPE_USERNAME else 0)
        return SaveInfo.Builder(type, screen.passwordIds.toTypedArray())
            .apply { if (screen.usernameIds.isNotEmpty()) setOptionalIds(screen.usernameIds.toTypedArray()) }
            .build()
    }
}

/**
 * Hands a just-typed login from the Autofill service to the save screen without putting the password
 * in an Intent (the token is all that travels). Same process, in memory only, expires in 5 minutes.
 */
object PendingSaves {
    data class Login(val packageName: String?, val domain: String?, val username: String, val password: String, val at: Long)

    private val map = ConcurrentHashMap<String, Login>()

    fun put(login: Login): String {
        val now = System.currentTimeMillis()
        map.entries.removeIf { now - it.value.at > 5 * 60_000 }
        return UUID.randomUUID().toString().also { map[it] = login }
    }

    fun take(token: String?): Login? = token?.let { map.remove(it) }
}
