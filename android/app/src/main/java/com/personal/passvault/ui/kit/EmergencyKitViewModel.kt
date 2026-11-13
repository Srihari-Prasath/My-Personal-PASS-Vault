package com.personal.passvault.ui.kit

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.passvault.core.session.VaultSession
import com.personal.passvault.data.repo.EntryRepository
import com.personal.passvault.data.repo.SettingsRepository
import com.personal.passvault.domain.EntryPayload
import com.personal.passvault.domain.EntryType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class KitOption<T>(val id: String, val title: String, val subtitle: String, val selected: Boolean, val value: T)

@HiltViewModel
class EmergencyKitViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: EntryRepository,
    private val settings: SettingsRepository,
    private val session: VaultSession,
) : ViewModel() {

    var loading by mutableStateOf(true)
        private set
    /** Recovery-code entries: selected by default (that's what the kit is for). */
    var codeOptions by mutableStateOf<List<KitOption<KitCodes>>>(emptyList())
        private set
    /** 2FA keys: off by default (extra-sensitive; only pick the ones you'd be stuck without). */
    var totpOptions by mutableStateOf<List<KitOption<KitTotp>>>(emptyList())
        private set
    var message by mutableStateOf<String?>(null)
        private set

    val botUsername: String? get() = settings.backup.value.botUsername

    init {
        viewModelScope.launch {
            val data = repo.auditData()
            val codesBy = data.codes.filter { it.setReplacedAt == null }.groupBy { it.entryId }
            codeOptions = data.entries.filter { EntryType.from(it.type) == EntryType.RECOVERY_CODES }
                .sortedBy { it.title.lowercase() }
                .mapNotNull { e ->
                    val codes = codesBy[e.id].orEmpty().sortedBy { it.position }
                    if (codes.isEmpty()) return@mapNotNull null
                    val left = codes.count { !it.used }
                    KitOption(
                        e.id, e.title, "$left of ${codes.size} codes left", selected = true,
                        value = KitCodes(e.title, e.username.ifBlank { e.email }, codes.map { it.code to it.used }),
                    )
                }
            totpOptions = data.entries.sortedBy { it.title.lowercase() }.mapNotNull { e ->
                val t = EntryPayload.parse(e.payload).totp ?: return@mapNotNull null
                val label = listOf(t.issuer, t.account).filter { it.isNotBlank() }.joinToString(" · ")
                val details = buildList {
                    if (t.digits != 6) add("${t.digits} digits")
                    if (t.period != 30) add("every ${t.period}s")
                    if (t.algorithm != "SHA1") add(t.algorithm)
                }.joinToString(" · ")
                KitOption(e.id, e.title, label.ifBlank { "2FA key" }, selected = false, value = KitTotp(e.title, label, t.secret, details))
            }
            loading = false
        }
    }

    fun toggleCodes(id: String) { codeOptions = codeOptions.map { if (it.id == id) it.copy(selected = !it.selected) else it } }
    fun toggleTotp(id: String) { totpOptions = totpOptions.map { if (it.id == id) it.copy(selected = !it.selected) else it } }
    fun consumeMessage() { message = null }

    suspend fun buildPdf(): ByteArray = withContext(Dispatchers.Default) {
        EmergencyKitPdf.build(
            KitData(
                createdAt = System.currentTimeMillis(),
                botUsername = settings.backup.value.botUsername,
                lastBackupAt = settings.backup.value.lastBackupAt,
                codeSets = codeOptions.filter { it.selected }.map { it.value },
                totpKeys = totpOptions.filter { it.selected }.map { it.value },
            )
        )
    }

    /** The print screen is another app's window: keep the vault open meanwhile. */
    fun holdForSystemUi() = session.holdAutoLock()
    fun releaseHold() = session.releaseAutoLock()

    fun saveTo(uri: Uri?) {
        session.releaseAutoLock()
        if (uri == null) return
        viewModelScope.launch {
            message = try {
                val bytes = buildPdf()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                        ?: throw IllegalStateException("Couldn't write the file")
                }
                "Saved. Print it, then delete the PDF — it isn't encrypted."
            } catch (e: Exception) {
                e.message ?: "Couldn't save the PDF"
            }
        }
    }
}
