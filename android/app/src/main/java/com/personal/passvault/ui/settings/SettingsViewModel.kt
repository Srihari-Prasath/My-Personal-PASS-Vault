package com.personal.passvault.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.passvault.core.crypto.VaultKeyManager
import com.personal.passvault.core.session.VaultSession
import com.personal.passvault.data.db.DatabaseProvider
import com.personal.passvault.data.icons.IconLoader
import com.personal.passvault.data.icons.WebIconRepository
import com.personal.passvault.data.backup.BackupManager
import com.personal.passvault.data.repo.AppSettings
import com.personal.passvault.data.repo.BackupState
import com.personal.passvault.data.repo.SettingsRepository
import com.personal.passvault.data.repo.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val keyManager: VaultKeyManager,
    private val session: VaultSession,
    private val dbProvider: DatabaseProvider,
    private val iconLoader: IconLoader,
    private val webIcons: WebIconRepository,
    private val backups: BackupManager,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepo.settings
    val backup: StateFlow<BackupState> = settingsRepo.backup

    var biometricOn by mutableStateOf(keyManager.isBiometricEnabled())
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun consumeMessage() { message = null }

    fun setAutoLock(seconds: Int) = viewModelScope.launch { settingsRepo.setAutoLock(seconds) }
    fun setClipboard(seconds: Int) = viewModelScope.launch { settingsRepo.setClipboard(seconds) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsRepo.setTheme(mode) }
    fun setDynamicColor(on: Boolean) = viewModelScope.launch { settingsRepo.setDynamicColor(on) }
    fun setDownloadIcons(on: Boolean) = viewModelScope.launch { settingsRepo.setDownloadWebIcons(on) }
    fun setBackupReminders(on: Boolean) = viewModelScope.launch { settingsRepo.setBackupReminders(on) }
    fun setPasswordReminders(on: Boolean) = viewModelScope.launch { settingsRepo.setPasswordReminders(on) }
    fun setGoogleSyncReminders(on: Boolean) = viewModelScope.launch { settingsRepo.setGoogleSyncReminders(on) }

    fun refreshWebIcons() = viewModelScope.launch {
        webIcons.clearCache()
        iconLoader.clearWebIcons()
        message = "Website icons will be downloaded again"
    }

    // ---------- Fingerprint ----------

    fun biometricEnrollCipher(): Cipher? = runCatching { keyManager.biometricEnrollCipher() }.getOrNull()

    fun enableBiometric(authorised: Cipher) {
        // Decoy vault: pretend. Wrapping the decoy key here would make the fingerprint open the decoy forever.
        if (session.isDecoy) {
            biometricOn = true
            message = "Fingerprint unlock is on"
            return
        }
        session.withVaultKey { vk -> keyManager.enableBiometric(authorised, vk) }
        biometricOn = keyManager.isBiometricEnabled()
        message = if (biometricOn) "Fingerprint unlock is on" else "Couldn't turn on fingerprint"
    }

    fun disableBiometric() {
        if (!session.isDecoy) keyManager.disableBiometric()
        biometricOn = false
        message = "Fingerprint unlock is off"
    }

    // ---------- Fingerprint to reveal ----------

    fun setRevealNeedsAuth(on: Boolean) = viewModelScope.launch { settingsRepo.setRevealNeedsAuth(on) }
    fun setAllowScreenshots(on: Boolean) = viewModelScope.launch { settingsRepo.setAllowScreenshots(on) }

    // ---------- Decoy vault ----------

    /** Never shown while the decoy itself is open (the Settings row is hidden then). */
    val inDecoy: Boolean get() = session.isDecoy
    var decoyStatus by mutableStateOf(keyManager.decoyStatus())
        private set

    /** Returns via [onResult]: null = OK, otherwise an error message. */
    fun setDecoyPassword(password: CharArray, onResult: (String?) -> Unit) {
        if (busy || session.isDecoy) return
        busy = true
        viewModelScope.launch {
            val vk = session.withVaultKey { it.copyOf() }
            val ok = vk != null && keyManager.setDecoyPassword(vk, password)
            vk?.fill(0)
            password.fill('\u0000')
            busy = false
            decoyStatus = keyManager.decoyStatus()
            if (ok) message = "Decoy password set"
            onResult(if (ok) null else "The decoy password must be different from your master password")
        }
    }

    fun removeDecoy() {
        if (session.isDecoy) return
        keyManager.removeDecoy()
        dbProvider.deleteDecoyDatabase()
        decoyStatus = keyManager.decoyStatus()
        message = "Decoy vault deleted"
    }

    // ---------- Master password ----------

    fun changeMasterPassword(old: CharArray, new: CharArray, onResult: (Boolean) -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            val decoy = session.isDecoy
            // Inside the decoy, "the master password" is the decoy password
            val ok = if (decoy) keyManager.changeDecoyPassword(old, new) else keyManager.changeMasterPassword(old, new)
            old.fill('\u0000'); new.fill('\u0000')
            if (ok && !decoy) {
                settingsRepo.recordSuccessfulUnlock(withPassword = true)
                // Fresh backup under the NEW password right away (older backups keep needing the old one)
                backups.onVaultChanged()
                decoyStatus = keyManager.decoyStatus()
            }
            busy = false
            message = when {
                !ok -> null
                !decoy && decoyStatus == VaultKeyManager.DecoyStatus.NEEDS_PASSWORD ->
                    "Master password changed. Set your decoy password again (Settings → Decoy vault)."
                else -> "Master password changed"
            }
            onResult(ok)
        }
    }

    /**
     * Permanently deletes the vault after re-checking the master password.
     * Deleting the key file first means the DB is unreadable even if deleting it fails.
     * Navigation to the setup screen happens automatically when the session locks.
     */
    fun wipeVault(master: CharArray, onResult: (Boolean) -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            val vk = keyManager.unlockWithPassword(master)
            master.fill('\u0000')
            if (vk == null) {
                busy = false
                onResult(false)
                return@launch
            }
            vk.fill(0)
            backups.forgetLocal() // pending upload + bot token (backups already in Telegram are untouched)
            keyManager.wipe()
            session.lock()
            dbProvider.deleteDatabaseFile()
            settingsRepo.clearAll()
            iconLoader.clearWebIcons()
            busy = false
            onResult(true)
        }
    }
}
