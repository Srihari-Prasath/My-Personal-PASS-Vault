package com.personal.passvault.autofill

import android.app.Activity
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.view.autofill.AutofillManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.personal.passvault.core.security.ClipboardHelper
import com.personal.passvault.core.session.VaultSession
import com.personal.passvault.data.icons.IconLoader
import com.personal.passvault.data.icons.InstalledAppsRepository
import com.personal.passvault.data.repo.EntryRepository
import com.personal.passvault.data.repo.SettingsRepository
import com.personal.passvault.domain.EntryDraft
import com.personal.passvault.domain.EntryPayload
import com.personal.passvault.domain.EntryType
import com.personal.passvault.domain.IconSpec
import com.personal.passvault.domain.TargetDraft
import com.personal.passvault.domain.TargetKind
import com.personal.passvault.ui.components.EntryAvatar
import com.personal.passvault.ui.components.LocalClipboardHelper
import com.personal.passvault.ui.components.LocalIconLoader
import com.personal.passvault.ui.components.RevealableSecret
import com.personal.passvault.ui.theme.PassVaultTheme
import com.personal.passvault.ui.unlock.UnlockScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Shared look for the small activities Autofill opens on top of other apps. */
@Composable
private fun AutofillTheme(settings: SettingsRepository, icons: IconLoader, clipboard: ClipboardHelper, content: @Composable () -> Unit) {
    val s by settings.settings.collectAsStateWithLifecycle()
    PassVaultTheme(s.themeMode, s.dynamicColor) {
        CompositionLocalProvider(LocalIconLoader provides icons, LocalClipboardHelper provides clipboard) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
        }
    }
}

/**
 * Opened when the vault is locked and the user taps "Unlock PassVault" in another app's login form.
 * Unlock (fingerprint / master password) -> hand the matching logins back to Android -> close.
 */
@AndroidEntryPoint
class AutofillAuthActivity : FragmentActivity() {

    @Inject lateinit var session: VaultSession
    @Inject lateinit var repo: EntryRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var icons: IconLoader
    @Inject lateinit var clipboard: ClipboardHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setResult(Activity.RESULT_CANCELED)

        lifecycleScope.launch {
            session.unlocked.first { it }
            deliver()
        }
        setContent {
            AutofillTheme(settings, icons, clipboard) {
                val unlocked by session.unlocked.collectAsStateWithLifecycle()
                if (!unlocked) UnlockScreen()
            }
        }
    }

    private suspend fun deliver() {
        val structure: AssistStructure? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(AutofillManager.EXTRA_ASSIST_STRUCTURE, AssistStructure::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(AutofillManager.EXTRA_ASSIST_STRUCTURE)
        }
        if (structure == null) { finish(); return }
        val screen = StructureParser.parse(structure)
        val pkg = intent.getStringExtra(AutofillResponses.EXTRA_PACKAGE)
        val domain = intent.getStringExtra(AutofillResponses.EXTRA_DOMAIN)
        val entries = repo.entriesFor(pkg, domain)
        val response = if (entries.isEmpty()) null else AutofillResponses.unlocked(this, screen, entries)
        if (response == null) {
            Toast.makeText(
                this,
                "No saved login for ${domain ?: "this app"}. Open PassVault and link it under \"Used on\".",
                Toast.LENGTH_LONG,
            ).show()
        } else {
            setResult(Activity.RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, response))
        }
        finish()
    }
}

/**
 * "Save to PassVault?" after the user typed a login by hand in another app.
 * Existing entry (same app/site + username) -> the password is updated and the old one goes into history.
 */
@AndroidEntryPoint
class AutofillSaveActivity : FragmentActivity() {

    @Inject lateinit var session: VaultSession
    @Inject lateinit var repo: EntryRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var icons: IconLoader
    @Inject lateinit var clipboard: ClipboardHelper
    @Inject lateinit var apps: InstalledAppsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        val login = PendingSaves.take(intent.getStringExtra(AutofillResponses.EXTRA_SAVE_TOKEN))
        if (login == null) { finish(); return }
        val label = login.packageName?.let { apps.label(it) } ?: login.domain ?: "this app"

        setContent {
            AutofillTheme(settings, icons, clipboard) {
                val unlocked by session.unlocked.collectAsStateWithLifecycle()
                AnimatedContent(unlocked, label = "saveUnlock") { open ->
                    if (!open) UnlockScreen()
                    else SaveSheet(
                        login = login,
                        label = label,
                        onSave = {
                            lifecycleScope.launch {
                                val updated = save(login, label)
                                Toast.makeText(
                                    this@AutofillSaveActivity,
                                    if (updated) "Password updated — the old one is in history" else "Saved to PassVault",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                finish()
                            }
                        },
                        onCancel = { finish() },
                    )
                }
            }
        }
    }

    /** Returns true if an existing entry was updated. */
    private suspend fun save(login: PendingSaves.Login, label: String): Boolean {
        val matches = repo.entriesFor(login.packageName, login.domain)
        val existing = matches.firstOrNull {
            login.username.isNotEmpty() && (it.username.equals(login.username, true) || it.email.equals(login.username, true))
        } ?: matches.singleOrNull()?.takeIf { login.username.isEmpty() }

        if (existing != null) {
            if (existing.password == login.password) return true
            repo.save(
                EntryDraft(
                    id = existing.id,
                    type = EntryType.from(existing.type),
                    title = existing.title,
                    username = existing.username,
                    email = existing.email,
                    password = login.password,
                    notes = existing.notes,
                    favorite = existing.favorite,
                    targets = repo.targets(existing.id).map { TargetDraft(TargetKind.from(it.kind), it.packageName, it.domain, it.label) },
                    codes = repo.currentCodes(existing.id).map { it.code },
                    payload = EntryPayload.parse(existing.payload),
                )
            )
            return true
        }
        val isEmail = '@' in login.username
        repo.save(
            EntryDraft(
                id = null,
                type = EntryType.LOGIN,
                title = label.replaceFirstChar { it.uppercaseChar() },
                username = if (isEmail) "" else login.username,
                email = if (isEmail) login.username else "",
                password = login.password,
                notes = "",
                favorite = false,
                targets = listOfNotNull(
                    login.packageName?.let { TargetDraft(TargetKind.APP, packageName = it, label = label) },
                    login.domain?.let { TargetDraft(TargetKind.WEB, domain = it, label = it) },
                ),
                codes = emptyList(),
            )
        )
        return false
    }
}

@Composable
private fun SaveSheet(login: PendingSaves.Login, label: String, onSave: () -> Unit, onCancel: () -> Unit) {
    var revealed by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.BottomCenter) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EntryAvatar(IconSpec(label, listOfNotNull(login.packageName), listOfNotNull(login.domain)), 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Save to PassVault?", style = MaterialTheme.typography.titleLarge)
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (login.username.isNotEmpty()) {
                    Text(login.username, style = MaterialTheme.typography.bodyLarge)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RevealableSecret(login.password, revealed, Modifier.weight(1f))
                    androidx.compose.material3.TextButton(onClick = { revealed = !revealed }) { Text(if (revealed) "Hide" else "Show") }
                }
                Text(
                    "If you already have this login, its password is updated and the old one is kept in history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Not now") }
                    Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Save") }
                }
            }
        }
    }
}
