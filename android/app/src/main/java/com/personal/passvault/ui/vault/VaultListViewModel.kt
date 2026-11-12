package com.personal.passvault.ui.vault

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.passvault.core.security.ClipboardHelper
import com.personal.passvault.core.session.VaultSession
import com.personal.passvault.data.repo.BackupState
import com.personal.passvault.data.repo.EntryRepository
import com.personal.passvault.data.repo.SettingsRepository
import com.personal.passvault.domain.EntrySummary
import com.personal.passvault.domain.EntryType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class VaultFilter(val label: String) {
    ALL("All"), FAVORITES("Favourites"), LOGINS("Logins"), TOTP("2FA"), CODES("Recovery codes"),
    CARDS("Cards"), WIFI("Wi-Fi"), NOTES("Notes")
}

@Immutable
data class VaultUi(
    val loading: Boolean = true,
    val totalCount: Int = 0,
    val items: List<EntrySummary> = emptyList(),
    val query: String = "",
    val filter: VaultFilter = VaultFilter.ALL,
)

@HiltViewModel
class VaultListViewModel @Inject constructor(
    private val repo: EntryRepository,
    private val clipboard: ClipboardHelper,
    private val session: VaultSession,
    private val settings: SettingsRepository,
) : ViewModel() {

    /** true once per install: ask for notification permission (reminders). */
    suspend fun shouldAskNotifications(): Boolean = settings.shouldAskNotificationPermission()

    val backup: StateFlow<BackupState> = settings.backup

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(VaultFilter.ALL)

    val ui: StateFlow<VaultUi> = combine(repo.summaries(), query, filter) { all, q, f ->
        val needle = q.trim().lowercase()
        val items = all
            .filter { e ->
                when (f) {
                    VaultFilter.ALL -> true
                    VaultFilter.LOGINS -> e.type == EntryType.LOGIN
                    VaultFilter.CODES -> e.type == EntryType.RECOVERY_CODES
                    VaultFilter.NOTES -> e.type == EntryType.NOTE
                    VaultFilter.FAVORITES -> e.favorite
                    VaultFilter.TOTP -> e.type == EntryType.TOTP || e.subtitle.endsWith("· 2FA")
                    VaultFilter.CARDS -> e.type == EntryType.CARD
                    VaultFilter.WIFI -> e.type == EntryType.WIFI
                }
            }
            .filter { needle.isEmpty() || it.searchText.contains(needle) }
            .sortedWith(compareByDescending<EntrySummary> { it.favorite }.thenBy { it.title.lowercase() })
        VaultUi(loading = false, totalCount = all.size, items = items, query = q, filter = f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultUi())

    fun setQuery(q: String) { query.value = q }
    fun setFilter(f: VaultFilter) { filter.value = f }

    /** Returns false when there's nothing to copy. */
    fun copyPassword(id: String, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val pw = repo.get(id)?.password.orEmpty()
        if (pw.isNotEmpty()) clipboard.copy("Password", pw)
        onResult(pw.isNotEmpty())
    }

    fun copyUsername(id: String, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val e = repo.get(id)
        val user = e?.username?.ifBlank { e.email }.orEmpty()
        if (user.isNotEmpty()) clipboard.copy("Username", user, sensitive = false)
        onResult(user.isNotEmpty())
    }

    fun toggleFavorite(item: EntrySummary) = viewModelScope.launch { repo.setFavorite(item.id, !item.favorite) }

    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }

    fun lock() = session.lock()
}
