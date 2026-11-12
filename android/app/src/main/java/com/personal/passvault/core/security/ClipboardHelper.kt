package com.personal.passvault.core.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.os.SystemClock
import com.personal.passvault.data.repo.SettingsRepository
import com.personal.passvault.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** What the "Copied · clears in 27s" pill shows. */
data class ClipState(val what: String, val copiedAtElapsed: Long, val durationMs: Long)

@Singleton
class ClipboardHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val clipboard = context.getSystemService(ClipboardManager::class.java)
    private val _state = MutableStateFlow<ClipState?>(null)
    val state: StateFlow<ClipState?> = _state.asStateFlow()
    private var clearJob: Job? = null

    /** [what] is a human label like "Password" — never the secret itself. */
    fun copy(what: String, text: String, sensitive: Boolean = true) {
        val clip = ClipData.newPlainText(CLIP_LABEL, text)
        if (sensitive) {
            // Hides the value in Android 13+'s clipboard preview + keyboard suggestions
            clip.description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        clipboard.setPrimaryClip(clip)

        val durationMs = settings.settings.value.clipboardSeconds * 1000L
        _state.value = ClipState(what, SystemClock.elapsedRealtime(), durationMs)
        clearJob?.cancel()
        clearJob = scope.launch(Dispatchers.Main) {
            delay(durationMs)
            clearIfOurs()
        }
    }

    fun clearNow() {
        clearJob?.cancel()
        clearIfOurs()
    }

    private fun clearIfOurs() {
        // In the background Android may not let us read the clipboard; then we clear anyway.
        val label = runCatching { clipboard.primaryClipDescription?.label }.getOrNull()
        if (label == null || label == CLIP_LABEL) {
            runCatching { clipboard.clearPrimaryClip() }
        }
        _state.value = null
    }

    private companion object {
        const val CLIP_LABEL = "PassVault"
    }
}
