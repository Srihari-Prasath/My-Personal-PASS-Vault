package com.personal.passvault.ui.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.passvault.core.crypto.VaultKeyManager
import com.personal.passvault.core.session.VaultSession
import com.personal.passvault.data.repo.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

enum class SetupStep { WELCOME, PASSWORD, BIOMETRIC, DONE }

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val keyManager: VaultKeyManager,
    private val session: VaultSession,
    private val settings: SettingsRepository,
) : ViewModel() {

    var step by mutableStateOf(SetupStep.WELCOME)
        private set
    var creating by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun goTo(next: SetupStep) {
        step = next
    }

    fun createVault(master: CharArray, biometricAvailable: Boolean) {
        if (creating) return
        creating = true
        viewModelScope.launch {
            try {
                val vk = keyManager.createVault(master)
                session.unlock(vk)
                settings.recordSuccessfulUnlock(withPassword = true)
                step = if (biometricAvailable) SetupStep.BIOMETRIC else SetupStep.DONE
            } catch (e: Exception) {
                error = "Couldn't create the vault: ${e.message}"
            } finally {
                master.fill('\u0000')
                creating = false
            }
        }
    }

    fun biometricCipher(): Cipher? = runCatching { keyManager.biometricEnrollCipher() }.getOrNull()

    fun enableBiometric(authorised: Cipher) {
        session.withVaultKey { vk -> keyManager.enableBiometric(authorised, vk) }
        step = SetupStep.DONE
    }
}
