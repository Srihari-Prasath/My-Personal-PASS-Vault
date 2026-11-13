package com.personal.passvault.ui.importcsv

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.passvault.core.session.VaultSession
import com.personal.passvault.data.icons.InstalledAppsRepository
import com.personal.passvault.data.repo.EntryRepository
import com.personal.passvault.data.repo.SettingsRepository
import com.personal.passvault.domain.CsvImport
import com.personal.passvault.domain.CsvImportResult
import com.personal.passvault.domain.EntryDraft
import com.personal.passvault.domain.EntryPayload
import com.personal.passvault.domain.EntryType
import com.personal.passvault.domain.ExistingLogin
import com.personal.passvault.domain.ImportMatch
import com.personal.passvault.domain.ImportMatcher
import com.personal.passvault.domain.ImportedLogin
import com.personal.passvault.domain.MatchKind
import com.personal.passvault.domain.TargetDraft
import com.personal.passvault.domain.TargetKind
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class ImportStage { PICK, LOADING, PREVIEW, IMPORTING, DONE }

@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: EntryRepository,
    private val apps: InstalledAppsRepository,
    private val session: VaultSession,
    private val settings: SettingsRepository,
) : ViewModel() {

    var stage by mutableStateOf(ImportStage.PICK)
        private set
    var result by mutableStateOf<CsvImportResult?>(null)
        private set
    /** One per imported login: NEW / SAME / CHANGED (+ which entry). */
    var matches by mutableStateOf<List<ImportMatch>>(emptyList())
        private set
    /** Give existing entries the password from the file when it's different (old one -> history). */
    var updateChanged by mutableStateOf(true)
    var added by mutableStateOf(0)
        private set
    var updated by mutableStateOf(0)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var fileDeleted by mutableStateOf<Boolean?>(null)
        private set

    private var fileUri: Uri? = null

    val newCount: Int get() = matches.count { it.kind == MatchKind.NEW }
    val changedCount: Int get() = matches.count { it.kind == MatchKind.CHANGED }
    val sameCount: Int get() = matches.count { it.kind == MatchKind.SAME }
    val workCount: Int get() = newCount + if (updateChanged) changedCount else 0

    fun holdForPicker() = session.holdAutoLock()

    fun load(uri: Uri?) {
        session.releaseAutoLock()
        if (uri == null) return
        fileUri = uri
        stage = ImportStage.LOADING
        error = null
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readBytes()
                        require(bytes.size < 10 * 1024 * 1024) { "File is too large" }
                        String(bytes, Charsets.UTF_8)
                    } ?: throw IllegalStateException("Couldn't open the file")
                }
                val parsed = withContext(Dispatchers.Default) { CsvImport.import(text) }
                val audit = repo.auditData()
                val targetsBy = audit.targets.groupBy { it.entryId }
                val existing = audit.entries
                    .filter { EntryType.from(it.type) == EntryType.LOGIN }
                    .map { e ->
                        val t = targetsBy[e.id].orEmpty()
                        ExistingLogin(
                            id = e.id,
                            title = e.title,
                            user = e.username.ifBlank { e.email },
                            password = e.password,
                            domains = t.filter { it.kind == TargetKind.WEB.db }.mapNotNull { it.domain },
                            packages = t.filter { it.kind == TargetKind.APP.db }.mapNotNull { it.packageName },
                        )
                    }
                matches = withContext(Dispatchers.Default) { ImportMatcher.match(parsed.logins, existing) }
                result = parsed
                stage = ImportStage.PREVIEW
            } catch (e: Exception) {
                error = e.message ?: "Couldn't read that file"
                stage = ImportStage.PICK
            }
        }
    }

    fun import() {
        val parsed = result ?: return
        stage = ImportStage.IMPORTING
        viewModelScope.launch {
            try {
                val installed = apps.list().associateBy { it.packageName }
                val newDrafts = parsed.logins.filterIndexed { i, _ -> matches[i].kind == MatchKind.NEW }
                    .map { it.toDraft(installed[it.packageName]?.label) }
                val updates = if (!updateChanged) emptyMap() else
                    parsed.logins.withIndex()
                        .filter { (i, _) -> matches[i].kind == MatchKind.CHANGED }
                        .associate { (i, l) -> matches[i].entryId!! to l.password }
                updated = repo.importSync(newDrafts, updates)
                added = newDrafts.size
                settings.recordGoogleSync()
                stage = ImportStage.DONE
            } catch (e: Exception) {
                error = e.message ?: "Import failed"
                stage = ImportStage.PREVIEW
            }
        }
    }

    /** The CSV holds every password in plain text — try to delete it for the user. */
    fun deleteFile() {
        val uri = fileUri ?: return
        fileDeleted = runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }.getOrDefault(false)
    }

    private fun ImportedLogin.toDraft(appLabel: String?): EntryDraft {
        val targets = buildList {
            packageName?.let { add(TargetDraft(TargetKind.APP, packageName = it, label = appLabel ?: it)) }
            domain?.let { add(TargetDraft(TargetKind.WEB, domain = it, label = it)) }
        }
        return EntryDraft(
            id = null,
            type = EntryType.LOGIN,
            title = title,
            username = username,
            email = email,
            password = password,
            notes = notes,
            favorite = false,
            targets = targets,
            codes = emptyList(),
            payload = EntryPayload(totp = totp),
        )
    }

    override fun onCleared() {
        session.releaseAutoLock()
    }
}
