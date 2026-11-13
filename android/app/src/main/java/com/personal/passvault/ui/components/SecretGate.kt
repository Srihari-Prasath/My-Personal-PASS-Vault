package com.personal.passvault.ui.components

import android.os.Build
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * "Confirm it's you" before showing or copying a secret, even while the vault is open.
 * Off by default. One confirmation covers the next 30 seconds, so revealing a few fields isn't annoying.
 *
 * Usage: `val gate = LocalSecretGate.current; gate { revealed = true }`
 */
@Stable
class SecretGate(
    private val activity: () -> FragmentActivity?,
    private val enabled: () -> Boolean,
    private val beforePrompt: () -> Unit = {},
    private val afterPrompt: () -> Unit = {},
) {
    operator fun invoke(action: () -> Unit) {
        if (!enabled() || SystemClock.elapsedRealtime() - lastAuthAt < WINDOW_MS) {
            action()
            return
        }
        val host = activity() ?: return action()
        // Fingerprint/face, or the phone's PIN/pattern as a fallback
        val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        } else {
            BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        }
        if (BiometricManager.from(host).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            // No screen lock set up on the phone: nothing to confirm with
            action()
            return
        }
        beforePrompt()
        val prompt = BiometricPrompt(
            host,
            ContextCompat.getMainExecutor(host),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    lastAuthAt = SystemClock.elapsedRealtime()
                    afterPrompt()
                    action()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    afterPrompt()
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Confirm it's you")
                .setSubtitle("To show or copy this secret")
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }

    companion object {
        private const val WINDOW_MS = 30_000L

        @Volatile
        private var lastAuthAt = 0L

        /** Called when the vault locks: the next reveal asks again. */
        fun reset() {
            lastAuthAt = 0L
        }

        /** Gate that never asks (previews, places without an activity). */
        val Open = SecretGate({ null }, { false })
    }
}

val LocalSecretGate = staticCompositionLocalOf { SecretGate.Open }

/** Show/hide toggle: hiding is instant, showing goes through the gate. `gate.toggle(revealed) { revealed = it }` */
fun SecretGate.toggle(current: Boolean, set: (Boolean) -> Unit) {
    if (current) set(false) else invoke { set(true) }
}
