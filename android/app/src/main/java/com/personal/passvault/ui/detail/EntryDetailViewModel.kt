package com.personal.passvault.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.passvault.core.security.ClipboardHelper
import com.personal.passvault.data.icons.InstalledAppsRepository
import com.personal.passvault.data.repo.EntryRepository
import com.personal.passvault.domain.EntryDetail
import com.personal.passvault.domain.EntrySummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DetailState {
    data object Loading : DetailState
    data object Missing : DetailState
    data class Loaded(val detail: EntryDetail) : DetailState
}

@HiltViewModel
class EntryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: EntryRepository,
    private val clipboard: ClipboardHelper,
    private val apps: InstalledAppsRepository,
) : ViewModel() {

    val id: String = checkNotNull(savedStateHandle["id"])

    /** From the list's in-memory cache: lets the header render on the very first frame. */
    val preview: EntrySummary? = repo.cachedSummary(id)

    val state: StateFlow<DetailState> = repo.detail(id)
        .map { if (it == null) DetailState.Missing else DetailState.Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState.Loading)

    fun copy(what: String, value: String, sensitive: Boolean = true) {
        if (value.isNotEmpty()) clipboard.copy(what, value, sensitive)
    }

    fun setCodeUsed(codeId: Long, used: Boolean) = viewModelScope.launch { repo.setCodeUsed(codeId, used) }
    fun restorePassword(historyId: Long) = viewModelScope.launch { repo.restorePassword(id, historyId) }
    fun deleteHistoryItem(historyId: Long) = viewModelScope.launch { repo.deleteHistoryItem(historyId) }
    fun setFavorite(favorite: Boolean) = viewModelScope.launch { repo.setFavorite(id, favorite) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch { repo.delete(id); onDone() }

    fun appIntent(packageName: String): Intent? = apps.launchIntent(packageName)

    fun webIntent(domain: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("https://$domain")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
