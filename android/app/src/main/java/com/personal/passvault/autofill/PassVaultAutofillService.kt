package com.personal.passvault.autofill

import android.app.PendingIntent
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import com.personal.passvault.core.session.VaultSession
import com.personal.passvault.data.repo.EntryRepository
import com.personal.passvault.domain.Domains
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Android Autofill provider. Matching rules (anti-phishing):
 *  - apps: only entries linked to that exact package name
 *  - browsers (known list only): entries linked to the website shown in the address bar (subdomains match)
 *  - a non-browser app claiming a website is ignored
 */
@AndroidEntryPoint
class PassVaultAutofillService : AutofillService() {

    @Inject lateinit var session: VaultSession
    @Inject lateinit var repo: EntryRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure ?: return callback.onSuccess(null)
        val screen = StructureParser.parse(structure)
        if (!screen.hasFields || screen.packageName.isEmpty() || screen.packageName == packageName) {
            callback.onSuccess(null)
            return
        }
        val browser = Browsers.isBrowser(screen.packageName)
        val domain = if (browser) screen.webDomain?.let { Domains.normalize(it) } else null
        if (browser && domain == null) { callback.onSuccess(null); return }
        val matchPackage = if (browser) null else screen.packageName

        val job = scope.launch {
            val response = runCatching {
                if (session.unlocked.value) {
                    AutofillResponses.unlocked(this@PassVaultAutofillService, screen, repo.entriesFor(matchPackage, domain))
                } else {
                    AutofillResponses.locked(this@PassVaultAutofillService, screen, matchPackage, domain)
                }
            }.getOrNull()
            runCatching { callback.onSuccess(response) }
        }
        cancellationSignal.setOnCancelListener { job.cancel() }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure ?: return callback.onSuccess()
        val screen = StructureParser.parse(structure)
        val password = screen.passwordValue
        if (password.isNullOrEmpty() || screen.packageName == packageName) { callback.onSuccess(); return }
        val browser = Browsers.isBrowser(screen.packageName)
        val token = PendingSaves.put(
            PendingSaves.Login(
                packageName = if (browser) null else screen.packageName,
                domain = if (browser) screen.webDomain?.let { Domains.normalize(it) } else null,
                username = screen.usernameValue.orEmpty(),
                password = password,
                at = System.currentTimeMillis(),
            )
        )
        val intent = Intent(this, AutofillSaveActivity::class.java)
            .putExtra(AutofillResponses.EXTRA_SAVE_TOKEN, token)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            this, token.hashCode(), intent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        callback.onSuccess(pending.intentSender) // opens our "Save login?" screen (unlocks first if needed)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
