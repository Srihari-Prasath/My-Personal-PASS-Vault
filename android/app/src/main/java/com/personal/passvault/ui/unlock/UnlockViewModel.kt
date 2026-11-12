package com.personal.passvault.ui.unlock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.passvault.core.crypto.VaultKeyManager
import com.personal.passvault.core.session.VaultSession
import com.personal.passvault.data.repo.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

data class UnlockUi(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val success: Boolean = false,
    val error: String? = null,
    val lockedUntil: Long = 0L,
    val biometricEnabled: Boolean = false,
    /** Why fingerprint isn't offered right now (e.g. weekly master-password check). */
    val passwordRequiredReason: String? = null,
)

@HiltViewModel
class UnlockViewModel @Inject constructor(
    private val keyManager: VaultKeyManager,
    private val session: VaultSession,
    private val settings: SettingsRepository,
) : ViewModel() {

    var ui by mutableStateOf(UnlockUi())
        private set

    private val _shake = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val shake: SharedFlow<Unit> = _shake

    init {
        viewModelScope.launch {
            val guard = settings.guard()
            val bioEnabled = keyManager.isBiometricEnabled()
            ui = ui.copy(
                loading = false,
                lockedUntil = guard.lockedUntil,
                biometricEnabled = bioEnabled && !guard.passwordRequired,
                passwordRequiredReason = if (bioEnabled && guard.passwordRequired)
                    "Enter your master password — we ask regularly so you never forget it." else null,
            )
        }
    }

    fun unlockWithPassword(password: CharArray) {
        if (ui.busy || ui.success) return
        if (System.currentTimeMillis() < ui.lockedUntil) return
        ui = ui.copy(busy = true, error = null)
        viewModelScope.launch {
            val result = keyManager.unlockAny(password)
            password.fill('\u0000')
            if (result == null) {
                val until = settings.recordFailedAttempt()
                ui = ui.copy(busy = false, error = "Wrong master password", lockedUntil = until)
                _shake.tryEmit(Unit)
            } else {
                // A decoy unlock looks exactly the same, but doesn't count as "typed the real master password"
                settings.recordSuccessfulUnlock(withPassword = !result.decoy)
                finishUnlock(result.vk, result.decoy)
            }
        }
    }

    /** Null = fingerprint can't be used (not set up, or invalidated by a newly enrolled fingerprint). */
    fun biometricCipher(): Cipher? {
        val cipher = keyManager.biometricUnlockCipher()
        if (cipher == null && ui.biometricEnabled) {
            ui = ui.copy(
                biometricEnabled = false,
                error = "Fingerprints changed on this phone. Unlock with your master password, then turn fingerprint on again in Settings.",
            )
        }
        return cipher
    }

    fun unlockWithBiometric(authorised: Cipher) {
        val vk = keyManager.unlockWithBiometric(authorised)
        if (vk == null) {
            ui = ui.copy(error = "Fingerprint unlock failed — use your master password")
            return
        }
        viewModelScope.launch {
            settings.recordSuccessfulUnlock(withPassword = false)
            finishUnlock(vk)
        }
    }

    fun clearError() {
        if (ui.error != null) ui = ui.copy(error = null)
    }

    private suspend fun finishUnlock(vk: ByteArray, decoy: Boolean = false) {
        ui = ui.copy(busy = false, success = true)
        delay(260) // let the lock-open animation play before the screen changes
        session.unlock(vk, decoy)
    }
}
