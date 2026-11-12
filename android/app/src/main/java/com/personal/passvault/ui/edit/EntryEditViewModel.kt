package com.personal.passvault.ui.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.passvault.data.icons.InstalledApp
import com.personal.passvault.data.icons.InstalledAppsRepository
import com.personal.passvault.data.repo.EntryRepository
import com.personal.passvault.domain.CardData
import com.personal.passvault.domain.Domains
import com.personal.passvault.domain.EntryDraft
import com.personal.passvault.domain.EntryPayload
import com.personal.passvault.domain.EntryType
import com.personal.passvault.domain.ReuseInfo
import com.personal.passvault.domain.TargetDraft
import com.personal.passvault.domain.TargetKind
import com.personal.passvault.domain.Totp
import com.personal.passvault.domain.TotpConfig
import com.personal.passvault.domain.WifiQr
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditForm(
    val type: EntryType = EntryType.LOGIN,
    val title: String = "",
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val notes: String = "",
    val favorite: Boolean = false,
    val targets: List<TargetDraft> = emptyList(),
    val codesText: String = "",
    // ---- 2FA, card, Wi-Fi ----
    /** What the user typed/scanned for 2FA: a Base32 key or an otpauth:// link. */
    val totpInput: String = "",
    val totp: TotpConfig? = null,
    val card: CardData = CardData(),
    val wifiSsid: String = "",
    val wifiSecurity: String = "WPA",
    val wifiHidden: Boolean = false,
) {
    /** One code per line (commas/tabs also split). Spaces inside a code are kept: "1234 5678". */
    val codes: List<String>
        get() = codesText.split('\n', ',', '\t', ';').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    val totpInputInvalid: Boolean get() = totpInput.isNotBlank() && totp == null

    val canSave: Boolean
        get() = title.isNotBlank() && !totpInputInvalid && (type != EntryType.TOTP || totp != null)
}

@HiltViewModel
class EntryEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: EntryRepository,
    private val apps: InstalledAppsRepository,
) : ViewModel() {

    val id: String? = savedStateHandle.get<String>("id")?.takeIf { it.isNotBlank() }
    val isNew: Boolean get() = id == null

    var form by mutableStateOf(EditForm(type = EntryType.from(savedStateHandle.get<String>("type"))))
        private set
    private var original: EditForm = form

    var loading by mutableStateOf(id != null)
        private set
    var saving by mutableStateOf(false)
        private set
    var reuse by mutableStateOf<ReuseInfo?>(null)
        private set
    var installedApps by mutableStateOf<List<InstalledApp>>(emptyList())
        private set
    /** Codes in the vault right now — used to warn when saving a completely new set. */
    var currentCodes by mutableStateOf<List<String>>(emptyList())
        private set

    private val passwordFlow = MutableStateFlow("")

    val isDirty: Boolean get() = form != original

    init {
        viewModelScope.launch { installedApps = apps.list() }
        val entryId = id
        if (entryId != null) viewModelScope.launch {
            val e = repo.get(entryId)
            if (e != null) {
                val codes = repo.currentCodes(entryId).map { it.code }
                currentCodes = codes
                val p = EntryPayload.parse(e.payload)
                form = EditForm(
                    type = EntryType.from(e.type),
                    title = e.title,
                    username = e.username,
                    email = e.email,
                    password = e.password,
                    notes = e.notes,
                    favorite = e.favorite,
                    targets = repo.targets(entryId).map {
                        TargetDraft(TargetKind.from(it.kind), it.packageName, it.domain, it.label)
                    },
                    codesText = codes.joinToString("\n"),
                    totpInput = p.totp?.secret.orEmpty(),
                    totp = p.totp,
                    card = p.card ?: CardData(),
                    wifiSsid = p.wifi?.ssid.orEmpty(),
                    wifiSecurity = p.wifi?.security ?: "WPA",
                    wifiHidden = p.wifi?.hidden ?: false,
                )
                original = form
                passwordFlow.value = e.password
            }
            loading = false
        }
        watchReuse()
    }

    @OptIn(FlowPreview::class)
    private fun watchReuse() = viewModelScope.launch {
        passwordFlow.debounce(300).collectLatest { pw ->
            reuse = runCatching { repo.reuseInfo(id, pw) }.getOrNull()
        }
    }

    fun update(transform: (EditForm) -> EditForm) {
        form = transform(form)
        passwordFlow.value = form.password
    }

    // ---------- 2FA / scanning ----------

    fun setTotpInput(text: String) = update { f ->
        val parsed = Totp.parse(text)
        // Keep issuer/account the user already had if they only re-typed the bare key
        val merged = if (parsed != null && !text.trim().startsWith("otpauth://", true) && f.totp != null) {
            parsed.copy(issuer = f.totp.issuer, account = f.totp.account, digits = f.totp.digits, period = f.totp.period, algorithm = f.totp.algorithm)
        } else parsed
        f.copy(totpInput = text, totp = merged)
    }

    fun removeTotp() = update { it.copy(totpInput = "", totp = null) }

    /** A QR code was scanned: otpauth:// -> 2FA, WIFI: -> Wi-Fi details. Returns false if not understood. */
    fun applyScan(text: String): Boolean {
        WifiQr.parse(text)?.let { w ->
            update {
                it.copy(
                    wifiSsid = w.ssid, password = w.password, wifiSecurity = w.security, wifiHidden = w.hidden,
                    title = it.title.ifBlank { w.ssid },
                )
            }
            return true
        }
        val totp = Totp.parse(text) ?: return false
        update {
            it.copy(
                totpInput = text.trim(), totp = totp,
                title = it.title.ifBlank { totp.issuer.ifBlank { totp.account } },
                username = if (it.type == EntryType.LOGIN && it.username.isBlank() && !totp.account.contains('@')) totp.account else it.username,
                email = if (it.type == EntryType.LOGIN && it.email.isBlank() && totp.account.contains('@')) totp.account else it.email,
            )
        }
        return true
    }

    fun updateCard(transform: (CardData) -> CardData) = update { it.copy(card = transform(it.card)) }

    // ---------- Linked apps / websites ----------

    /** Installed apps whose name matches the title, e.g. title "insta" -> Instagram. */
    fun suggestions(): List<InstalledApp> {
        val t = form.title.trim().lowercase()
        if (t.length < 3) return emptyList()
        val linked = form.targets.mapNotNull { it.packageName }.toSet()
        return installedApps.asSequence()
            .filter { it.packageName !in linked }
            .filter {
                val label = it.label.lowercase()
                label.contains(t) || (label.length >= 3 && t.contains(label)) || it.packageName.contains(t.replace(" ", ""))
            }
            .take(3)
            .toList()
    }

    fun addApp(app: InstalledApp) = update { f ->
        if (f.targets.any { it.packageName == app.packageName }) f
        else f.copy(
            targets = f.targets + TargetDraft(TargetKind.APP, packageName = app.packageName, label = app.label),
            title = f.title.ifBlank { app.label },
        )
    }

    /** Returns false if the text isn't a valid domain. */
    fun addWebsite(input: String): Boolean {
        val domain = Domains.normalize(input) ?: return false
        update { f ->
            if (f.targets.any { it.domain == domain }) f
            else f.copy(
                targets = f.targets + TargetDraft(TargetKind.WEB, domain = domain, label = domain),
                title = f.title.ifBlank { domain.substringBefore('.').replaceFirstChar { it.uppercaseChar() } },
            )
        }
        return true
    }

    fun removeTarget(target: TargetDraft) = update { f -> f.copy(targets = f.targets.filterNot { it.key == target.key }) }

    /** True if saving will archive the current set of codes because none of them are kept. */
    fun replacesCodeSet(): Boolean =
        form.type == EntryType.RECOVERY_CODES && currentCodes.isNotEmpty() && form.codes.none { it in currentCodes }

    fun save(onSaved: (id: String, isNew: Boolean) -> Unit) {
        if (saving || !form.canSave) return
        saving = true
        viewModelScope.launch {
            val f = form
            val payload = EntryPayload(
                totp = if (f.type == EntryType.LOGIN || f.type == EntryType.TOTP) f.totp else null,
                card = if (f.type == EntryType.CARD) f.card.copy(number = f.card.number.filter { it.isDigit() }) else null,
                wifi = if (f.type == EntryType.WIFI) com.personal.passvault.domain.WifiData(f.wifiSsid.trim(), f.wifiSecurity, f.wifiHidden) else null,
            )
            val savedId = repo.save(
                EntryDraft(
                    id = id,
                    type = f.type,
                    title = f.title,
                    username = if (f.type == EntryType.LOGIN) f.username else "",
                    email = if (f.type == EntryType.LOGIN) f.email else "",
                    password = if (f.type.usesPassword) f.password else "",
                    notes = f.notes,
                    favorite = f.favorite,
                    targets = if (f.type == EntryType.WIFI) emptyList() else f.targets,
                    codes = f.codes,
                    payload = payload,
                )
            )
            original = form
            saving = false
            onSaved(savedId, id == null)
        }
    }
}
