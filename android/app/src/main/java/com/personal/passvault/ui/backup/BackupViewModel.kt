package com.personal.passvault.ui.backup

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.passvault.core.crypto.InvalidBackupException
import com.personal.passvault.core.crypto.VaultKeyManager
import com.personal.passvault.core.crypto.WrongBackupPasswordException
import com.personal.passvault.core.session.VaultSession
import com.personal.passvault.data.backup.BackupManager
import com.personal.passvault.data.backup.BackupPayload
import com.personal.passvault.data.backup.ImportResult
import com.personal.passvault.data.backup.NoBackupFoundException
import com.personal.passvault.data.backup.RestoreMode
import com.personal.passvault.data.backup.UploadResult
import com.personal.passvault.data.repo.BackupState
import com.personal.passvault.data.repo.SettingsRepository
import com.personal.passvault.data.telegram.TelegramException
import com.personal.passvault.data.telegram.TgUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.security.SecureRandom
import javax.inject.Inject

enum class PairStep { TOKEN, WAITING, MANUAL }

data class ConnectUi(
    val token: String = "",
    val bot: TgUser? = null,
    val step: PairStep = PairStep.TOKEN,
    val code: String = "",
    val manualChatId: String = "",
    val busy: Boolean = false,
    val error: String? = null,
) {
    val deepLink: String? get() = bot?.username?.let { "https://t.me/$it?start=$code" }
}

enum class RestoreStage { IDLE, LOADING, NEED_PASSWORD, DECRYPTING, READY, IMPORTING, DONE }

data class RestoreUi(
    val stage: RestoreStage = RestoreStage.IDLE,
    val error: String? = null,
    val backupDate: Long = 0L,
    val entryCount: Int = 0,
    val result: ImportResult? = null,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val backups: BackupManager,
    private val settings: SettingsRepository,
    private val session: VaultSession,
    private val keyManager: VaultKeyManager,
) : ViewModel() {

    /** true = opened from first-time setup on a new phone (no vault yet). */
    val setupMode: Boolean = savedStateHandle.get<Boolean>("setup") ?: false

    val state: StateFlow<BackupState> = settings.backup

    var connect by mutableStateOf(ConnectUi())
        private set
    var restore by mutableStateOf(RestoreUi())
        private set
    var backingUp by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    private var pairingJob: Job? = null
    private var restoreBytes: ByteArray? = null
    private var restorePayload: BackupPayload? = null
    private var restoreMaster: CharArray? = null

    fun consumeMessage() { message = null }

    // =============================================================================================
    // Connect the bot
    // =============================================================================================

    fun setToken(t: String) { connect = connect.copy(token = t.trim(), error = null) }

    /** Step 1: check the token with Telegram, then start waiting for "/start <code>". */
    fun verifyToken() {
        if (connect.busy) return
        connect = connect.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val bot = backups.verifyToken(connect.token)
                val code = (100_000 + SecureRandom().nextInt(900_000)).toString()
                val offset = try {
                    backups.startPairing(connect.token)
                } catch (e: TelegramException) {
                    if (e.code == 409) {
                        // A bot server/webhook owns the updates: fall back to typing the chat ID
                        connect = connect.copy(bot = bot, step = PairStep.MANUAL, busy = false, error = e.message)
                        return@launch
                    }
                    throw e
                }
                connect = connect.copy(bot = bot, code = code, step = PairStep.WAITING, busy = false)
                pairingJob?.cancel()
                pairingJob = viewModelScope.launch { waitForStart(code, offset) }
            } catch (e: Exception) {
                connect = connect.copy(busy = false, error = e.friendly())
            }
        }
    }

    private suspend fun waitForStart(code: String, offset: Long) {
        try {
            val chatId = backups.waitForPairing(connect.token, code, offset)
            if (chatId == null) {
                connect = connect.copy(error = "Didn't hear from Telegram. Tap the button and press Start, or enter your chat ID.")
                return
            }
            finishConnect(chatId)
        } catch (e: Exception) {
            connect = connect.copy(error = e.friendly())
        }
    }

    /** Called right before opening Telegram, so the vault doesn't auto-lock while the user is there. */
    fun leavingAppForTelegram() = session.holdAutoLock()

    fun useManualChatId() {
        pairingJob?.cancel()
        connect = connect.copy(step = PairStep.MANUAL, error = null)
    }

    fun setManualChatId(v: String) { connect = connect.copy(manualChatId = v.filter { it.isDigit() || it == '-' }, error = null) }

    fun submitManualChatId() {
        val id = connect.manualChatId.toLongOrNull() ?: run {
            connect = connect.copy(error = "Chat ID is a number, e.g. 123456789")
            return
        }
        viewModelScope.launch { finishConnect(id) }
    }

    private suspend fun finishConnect(chatId: Long) {
        connect = connect.copy(busy = true, error = null)
        try {
            backups.connect(connect.token, chatId, connect.bot?.username)
            session.releaseAutoLock()
            connect = ConnectUi()
            message = "Connected to Telegram"
            // First backup straight away (only possible when a vault is open)
            if (!setupMode && session.unlocked.value) backupNow()
        } catch (e: Exception) {
            connect = connect.copy(busy = false, error = e.friendly())
        }
    }

    fun restartConnect() {
        pairingJob?.cancel()
        session.releaseAutoLock()
        connect = ConnectUi(token = connect.token)
    }

    fun disconnect() = viewModelScope.launch {
        backups.disconnect()
        message = "Telegram disconnected. Backups already in the chat are untouched."
    }

    // =============================================================================================
    // Backups
    // =============================================================================================

    fun backupNow() {
        if (backingUp) return
        backingUp = true
        viewModelScope.launch {
            message = when (val r = backups.backupNow()) {
                is UploadResult.Success -> "Backed up ${r.entries} entries to Telegram"
                is UploadResult.Failed -> r.message
                UploadResult.NotConfigured -> "Connect Telegram first"
                UploadResult.NothingPending -> null
            }
            backingUp = false
        }
    }

    fun setAutoBackup(on: Boolean) = viewModelScope.launch { settings.setAutoBackup(on) }

    fun holdForPicker() = session.holdAutoLock()

    fun exportTo(uri: Uri?) {
        session.releaseAutoLock()
        if (uri == null) return
        viewModelScope.launch {
            message = try {
                val n = backups.exportTo(uri)
                "Saved an encrypted backup with $n entries"
            } catch (e: Exception) {
                e.friendly()
            }
        }
    }

    // =============================================================================================
    // Restore
    // =============================================================================================

    fun restoreFromTelegram() = loadBackup { backups.downloadLatest() }

    fun restoreFromFile(uri: Uri?) {
        session.releaseAutoLock()
        if (uri == null) return
        loadBackup { backups.readFile(uri) }
    }

    private fun loadBackup(load: suspend () -> ByteArray) {
        restore = RestoreUi(stage = RestoreStage.LOADING)
        viewModelScope.launch {
            try {
                val bytes = load()
                com.personal.passvault.core.crypto.BackupCodec.readHeader(bytes) // fail fast on a wrong file
                restoreBytes = bytes
                restore = RestoreUi(stage = RestoreStage.NEED_PASSWORD)
            } catch (e: Exception) {
                restore = RestoreUi(stage = RestoreStage.IDLE, error = e.friendly())
            }
        }
    }

    fun unlockBackup(master: CharArray) {
        val bytes = restoreBytes ?: return
        restore = restore.copy(stage = RestoreStage.DECRYPTING, error = null)
        viewModelScope.launch {
            try {
                val payload = backups.open(bytes, master)
                restorePayload = payload
                restoreMaster?.fill('\u0000')
                restoreMaster = master
                restore = restore.copy(stage = RestoreStage.READY, backupDate = payload.createdAt, entryCount = payload.entries.size)
            } catch (e: Exception) {
                master.fill('\u0000')
                restore = restore.copy(stage = RestoreStage.NEED_PASSWORD, error = e.friendly())
            }
        }
    }

    /** Existing vault: merge or replace. */
    fun applyRestore(mode: RestoreMode) {
        val payload = restorePayload ?: return
        restore = restore.copy(stage = RestoreStage.IMPORTING)
        viewModelScope.launch {
            try {
                val result = backups.import(payload, mode)
                restore = restore.copy(stage = RestoreStage.DONE, result = result)
                clearRestoreSecrets()
            } catch (e: Exception) {
                restore = restore.copy(stage = RestoreStage.READY, error = e.friendly())
            }
        }
    }

    /** New phone: create the vault with the backup's master password, then import everything. */
    fun restoreAsNewVault(onDone: () -> Unit) {
        val payload = restorePayload ?: return
        val master = restoreMaster ?: return
        restore = restore.copy(stage = RestoreStage.IMPORTING)
        viewModelScope.launch {
            try {
                val vk = keyManager.createVault(master)
                session.unlock(vk)
                settings.recordSuccessfulUnlock(withPassword = true)
                val result = backups.import(payload, RestoreMode.REPLACE)
                restore = restore.copy(stage = RestoreStage.DONE, result = result)
                clearRestoreSecrets()
                onDone()
            } catch (e: Exception) {
                restore = restore.copy(stage = RestoreStage.READY, error = e.friendly())
            }
        }
    }

    fun closeRestore() {
        clearRestoreSecrets()
        restore = RestoreUi()
    }

    private fun clearRestoreSecrets() {
        restoreMaster?.fill('\u0000')
        restoreMaster = null
        restorePayload = null
        restoreBytes = null
    }

    override fun onCleared() {
        clearRestoreSecrets()
        session.releaseAutoLock()
    }

    private fun Throwable.friendly(): String = when (this) {
        is WrongBackupPasswordException -> "Wrong master password for this backup (or the file is damaged). Use the password you had when the backup was made."
        is InvalidBackupException -> message ?: "Not a PassVault backup"
        is NoBackupFoundException -> "No pinned backup found in the bot chat. Back up from your old phone first, or choose a file."
        is TelegramException -> message ?: "Telegram error"
        is IllegalArgumentException -> message ?: "Invalid input"
        is IOException -> "Network problem — check your connection and try again"
        else -> message ?: "Something went wrong"
    }
}
