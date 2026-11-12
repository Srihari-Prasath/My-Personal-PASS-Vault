package com.personal.passvault.core.session

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.personal.passvault.data.db.DatabaseProvider
import com.personal.passvault.data.repo.SettingsRepository
import com.personal.passvault.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the Vault Key in memory ONLY while unlocked, and auto-locks when the app has been in the
 * background longer than the chosen timeout. Locking wipes the key and closes the database.
 */
@Singleton
class VaultSession @Inject constructor(
    private val dbProvider: DatabaseProvider,
    private val settings: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : DefaultLifecycleObserver {

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private var vaultKey: ByteArray? = null
    private var backgroundedAt = 0L
    private var lockJob: Job? = null

    /** Call once from Application.onCreate (main thread). */
    fun attach() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    /** True while the decoy vault is open. Never shown in the UI; only used to keep real data safe. */
    @Volatile
    var isDecoy: Boolean = false
        private set

    /** Takes ownership of [vk]: it is copied and the argument is wiped. */
    suspend fun unlock(vk: ByteArray, decoy: Boolean = false) {
        withContext(Dispatchers.IO) { dbProvider.open(vk, decoy) }
        isDecoy = decoy
        synchronized(this) {
            vaultKey?.fill(0)
            vaultKey = vk.copyOf()
        }
        vk.fill(0)
        _unlocked.value = true
    }

    /** Runs [block] with the live Vault Key (e.g. to enable fingerprint). Never store the array. */
    fun <T> withVaultKey(block: (ByteArray) -> T): T? = synchronized(this) { vaultKey?.let(block) }

    fun lock() {
        lockJob?.cancel()
        lockJob = null
        // Flip state first so UI/flows stop reading before the database closes underneath them
        _unlocked.value = false
        synchronized(this) {
            vaultKey?.fill(0)
            vaultKey = null
        }
        dbProvider.close()
        isDecoy = false
    }

    /** Until this time (elapsedRealtime) auto-lock waits, e.g. while the user is in a file picker or Telegram. */
    @Volatile
    private var holdUntil = 0L

    /**
     * Keep the vault open while the user briefly leaves the app on purpose (pick a backup file, press Start in
     * Telegram). Capped at [maxMs]; call [releaseAutoLock] when they're back.
     */
    fun holdAutoLock(maxMs: Long = 5 * 60_000L) {
        holdUntil = SystemClock.elapsedRealtime() + maxMs
    }

    fun releaseAutoLock() {
        holdUntil = 0L
    }

    private fun timeoutMs(from: Long): Long =
        maxOf(settings.settings.value.autoLockSeconds * 1000L, holdUntil - from)

    override fun onStop(owner: LifecycleOwner) {
        if (!_unlocked.value) return
        backgroundedAt = SystemClock.elapsedRealtime()
        val timeoutMs = timeoutMs(backgroundedAt)
        if (timeoutMs <= 0) {
            lock()
        } else {
            lockJob?.cancel()
            lockJob = scope.launch {
                delay(timeoutMs)
                lock()
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        lockJob?.cancel()
        lockJob = null
        // The process may have been frozen in the background, so the delayed job might not have run
        if (_unlocked.value && backgroundedAt > 0) {
            if (SystemClock.elapsedRealtime() - backgroundedAt >= timeoutMs(backgroundedAt)) lock()
        }
        backgroundedAt = 0
    }
}
