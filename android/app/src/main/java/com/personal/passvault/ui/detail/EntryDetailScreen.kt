package com.personal.passvault.ui.detail

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.passvault.data.db.LinkedTargetEntity
import com.personal.passvault.data.db.PasswordHistoryEntity
import com.personal.passvault.data.db.RecoveryCodeEntity
import com.personal.passvault.domain.CodeSet
import com.personal.passvault.domain.EntryDetail
import com.personal.passvault.domain.EntryType
import com.personal.passvault.domain.IconSpec
import com.personal.passvault.domain.PasswordStrength
import com.personal.passvault.domain.TargetKind
import com.personal.passvault.domain.TimeText
import com.personal.passvault.ui.components.ConfirmDialog
import com.personal.passvault.ui.components.EntryAvatar
import com.personal.passvault.ui.components.LocalSecretGate
import com.personal.passvault.ui.components.RevealableSecret
import com.personal.passvault.ui.components.SecretGate
import com.personal.passvault.ui.components.toggle
import com.personal.passvault.ui.components.SectionCard
import com.personal.passvault.ui.components.TargetIcon
import com.personal.passvault.ui.components.sharedElementOrSelf
import com.personal.passvault.ui.components.strengthColor
import com.personal.passvault.ui.theme.Motion
import com.personal.passvault.ui.theme.SecretTextStyle

private const val YEAR_MS = 365L * 24 * 60 * 60 * 1000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    vm: EntryDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val detail = (state as? DetailState.Loaded)?.detail

    if (state is DetailState.Missing) {
        LaunchedEffect(Unit) { onBack() }
    }

    val listState = rememberLazyListState()
    // Collapsing header: show the small title in the top bar once the big header scrolls away
    val collapsed by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 220 }
    }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val gate = LocalSecretGate.current

    val title = detail?.entry?.title ?: vm.preview?.title.orEmpty()
    val icon = detail?.icon ?: vm.preview?.icon ?: IconSpec(title)
    val favorite = detail?.entry?.favorite ?: vm.preview?.favorite ?: false

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                title = {
                    AnimatedVisibility(
                        collapsed,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            EntryAvatar(icon, 32.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                },
                actions = {
                    val starScale by animateFloatAsState(if (favorite) 1f else 0.9f, Motion.bouncy(), label = "star")
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        vm.setFavorite(!favorite)
                    }) {
                        AnimatedContent(favorite, transitionSpec = { scaleIn(Motion.bouncy()) togetherWith scaleOut() }, label = "fav") { fav ->
                            Icon(
                                if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                "Favourite",
                                tint = if (fav) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.graphicsLayer { scaleX = starScale; scaleY = starScale },
                            )
                        }
                    }
                    IconButton(onClick = { onEdit(vm.id) }, enabled = detail != null) { Icon(Icons.Rounded.Edit, "Edit") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; confirmDelete = true },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 120.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "header") {
                Header(
                    id = vm.id,
                    title = title,
                    icon = icon,
                    typeLabel = detail?.type?.label ?: vm.preview?.type?.label.orEmpty(),
                    targets = detail?.targets.orEmpty(),
                    scrollOffset = { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset else Int.MAX_VALUE },
                    onOpenTarget = { t ->
                        val intent = if (t.kind == TargetKind.APP.db) t.packageName?.let(vm::appIntent)
                        else t.domain?.let(vm::webIntent)
                        if (intent != null) runCatching { context.startActivity(intent) }
                    },
                )
            }
            if (detail != null) {
                detailSections(detail, vm, gate)
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete $title?",
            text = "This deletes the entry, all its old passwords and recovery codes. This can't be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { vm.delete { } },
            onDismiss = { confirmDelete = false },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.detailSections(
    detail: EntryDetail,
    vm: EntryDetailViewModel,
    gate: SecretGate,
) {
    val e = detail.entry
    // Every copy of a secret goes through the gate ("Confirm it's you", if turned on in Settings)
    fun copySecret(what: String, value: String) = gate { vm.copy(what, value) }
    when (detail.type) {
        EntryType.LOGIN -> item(key = "login") {
            SectionCard(title = "Login") {
                if (e.username.isNotBlank()) FieldRow(Icons.Rounded.Person, "Username", e.username) { vm.copy("Username", e.username, sensitive = false) }
                if (e.email.isNotBlank()) FieldRow(Icons.Rounded.Email, "Email", e.email) { vm.copy("Email", e.email, sensitive = false) }
                PasswordRow(e.password, e.passwordChangedAt) { copySecret("Password", e.password) }
            }
        }
        EntryType.RECOVERY_CODES -> item(key = "codes") {
            CodesSection(detail.currentCodes, detail.oldCodeSets, onToggle = vm::setCodeUsed, onCopy = { copySecret("Recovery code", it) })
        }
        EntryType.NOTE -> Unit
        EntryType.TOTP -> item(key = "totp") { TotpDetailSection(detail.payload.totp) { copySecret("2FA code", it) } }
        EntryType.CARD -> item(key = "card") {
            CardDetailSection(detail.payload.card) { what, value -> copySecret(what, value) }
        }
        EntryType.WIFI -> item(key = "wifi") {
            WifiDetailSection(e.password, e.passwordChangedAt, detail.payload.wifi) { what, value -> copySecret(what, value) }
        }
    }
    if (detail.type == EntryType.LOGIN && detail.payload.totp != null) {
        item(key = "login-totp") { TotpDetailSection(detail.payload.totp) { copySecret("2FA code", it) } }
    }

    if (e.notes.isNotBlank()) {
        item(key = "notes") { NotesSection(e.notes, secure = detail.type == EntryType.NOTE) { copySecret("Note", e.notes) } }
    }

    if (detail.type.usesPassword) {
        item(key = "history") {
            HistorySection(
                currentPassword = e.password,
                currentSince = e.passwordChangedAt,
                history = detail.history,
                onCopy = { copySecret("Old password", it) },
                onRestore = vm::restorePassword,
                onDelete = vm::deleteHistoryItem,
            )
        }
    }

    item(key = "meta") {
        Text(
            "Created ${TimeText.date(e.createdAt)} · Edited ${TimeText.ago(e.updatedAt)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

// =================================================================================================
// Header with collapsing avatar
// =================================================================================================

@Composable
private fun Header(
    id: String,
    title: String,
    icon: IconSpec,
    typeLabel: String,
    targets: List<LinkedTargetEntity>,
    scrollOffset: () -> Int,
    onOpenTarget: (LinkedTargetEntity) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.graphicsLayer {
                // Read scroll in the draw phase -> no recomposition while scrolling
                val f = (scrollOffset() / 300f).coerceIn(0f, 1f)
                val s = 1f - 0.35f * f
                scaleX = s; scaleY = s
                alpha = 1f - 0.9f * f
                translationY = 40f * f
            }
        ) {
            EntryAvatar(icon, 96.dp, Modifier.sharedElementOrSelf("avatar-$id"))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        if (typeLabel.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(typeLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(targets.isNotEmpty(), enter = fadeIn() + expandVertically(Motion.size), exit = fadeOut() + shrinkVertically(Motion.size)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 14.dp),
            ) {
                items(targets, key = { it.id }) { t ->
                    Surface(
                        onClick = { onOpenTarget(t) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Row(
                            Modifier.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TargetIcon(t.packageName, t.domain, t.label, 24.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(t.label, style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.AutoMirrored.Rounded.OpenInNew, null,
                                Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// =================================================================================================
// Fields
// =================================================================================================

@Composable
internal fun FieldRow(icon: ImageVector, label: String, value: String, onCopy: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onCopy)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        IconButton(onClick = onCopy) { Icon(Icons.Rounded.ContentCopy, "Copy $label", Modifier.size(20.dp)) }
    }
}

@Composable
internal fun PasswordRow(password: String, changedAt: Long, onCopy: () -> Unit) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    val gate = LocalSecretGate.current
    val old = System.currentTimeMillis() - changedAt > YEAR_MS
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Key, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Password", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (password.isEmpty()) Text("Not set", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                else RevealableSecret(password, revealed)
            }
            if (password.isNotEmpty()) {
                IconButton(onClick = { gate.toggle(revealed) { revealed = it } }) {
                    Icon(if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show password", Modifier.size(20.dp))
                }
                IconButton(onClick = onCopy) { Icon(Icons.Rounded.ContentCopy, "Copy password", Modifier.size(20.dp)) }
            }
        }
        if (password.isNotEmpty()) {
            val level = PasswordStrength.level(password)
            Row(Modifier.padding(start = 38.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(strengthColor(level), CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(
                    "${level.label} · changed ${TimeText.ago(changedAt)}" + if (old) " — consider changing it" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (old) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NotesSection(notes: String, secure: Boolean, onCopy: () -> Unit) {
    var revealed by rememberSaveable { mutableStateOf(!secure) }
    val gate = LocalSecretGate.current
    SectionCard(title = if (secure) "Secure note" else "Notes") {
        Box(
            Modifier
                .fillMaxWidth()
                .clickable { if (!revealed) gate { revealed = true } }
                .padding(16.dp)
        ) {
            AnimatedContent(revealed, label = "note") { show ->
                if (show) Text(notes, style = MaterialTheme.typography.bodyLarge)
                else Text("Tap to show", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
            if (secure) TextButton(onClick = { gate.toggle(revealed) { revealed = it } }) { Text(if (revealed) "Hide" else "Show") }
            TextButton(onClick = onCopy) { Text("Copy") }
        }
    }
}

// =================================================================================================
// Recovery codes
// =================================================================================================

@Composable
private fun CodesSection(
    codes: List<RecoveryCodeEntity>,
    oldSets: List<CodeSet>,
    onToggle: (Long, Boolean) -> Unit,
    onCopy: (String) -> Unit,
) {
    val left = codes.count { !it.used }
    val haptic = LocalHapticFeedback.current
    Column { // one root node for the LazyColumn item
    SectionCard(title = "Recovery codes") {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(
                left,
                transitionSpec = {
                    val down = targetState < initialState
                    (slideInVertically { if (down) -it else it } + fadeIn()) togetherWith
                        (slideOutVertically { if (down) it else -it } + fadeOut())
                },
                label = "codesLeft",
            ) { n ->
                Text("$n", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(8.dp))
            Text("of ${codes.size} codes left · tap a code once you've used it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(codes.isNotEmpty() && left <= 2) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Running low — generate a new set in the account's security settings and save it here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        codes.forEach { code ->
            CodeRow(code, onToggle = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle(code.id, !code.used)
            }, onCopy = { onCopy(code.code) })
        }
        if (codes.isEmpty()) {
            Text("No codes saved yet. Tap Edit to paste them in.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (oldSets.isNotEmpty()) {
        var open by rememberSaveable { mutableStateOf(false) }
        Spacer(Modifier.height(18.dp))
        SectionCard(title = "Older code sets") {
            ExpandHeader(Icons.Rounded.History, "${oldSets.size} replaced set${if (oldSets.size > 1) "s" else ""}", open) { open = !open }
            AnimatedVisibility(open, enter = expandVertically(Motion.size) + fadeIn(), exit = shrinkVertically(Motion.size) + fadeOut()) {
                Column {
                    oldSets.forEach { set ->
                        Text(
                            "Replaced on ${TimeText.date(set.replacedAt)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp),
                        )
                        set.codes.forEach { c ->
                            Text(
                                c.code + if (c.used) "  (used)" else "",
                                style = SecretTextStyle,
                                color = MaterialTheme.colorScheme.outline,
                                textDecoration = if (c.used) TextDecoration.LineThrough else null,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
    }
}

@Composable
private fun CodeRow(code: RecoveryCodeEntity, onToggle: () -> Unit, onCopy: () -> Unit) {
    val alpha by animateFloatAsState(if (code.used) 0.45f else 1f, Motion.smooth(), label = "codeAlpha")
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            code.used,
            transitionSpec = { scaleIn(Motion.bouncy(), initialScale = 0.4f) + fadeIn() togetherWith scaleOut() + fadeOut() },
            label = "check",
        ) { used ->
            Icon(
                if (used) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                if (used) "Used" else "Not used",
                tint = if (used) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            code.code,
            style = SecretTextStyle,
            textDecoration = if (code.used) TextDecoration.LineThrough else null,
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { this.alpha = alpha },
        )
        if (code.used && code.usedAt != null) {
            Text(TimeText.date(code.usedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        IconButton(onClick = onCopy, enabled = !code.used) { Icon(Icons.Rounded.ContentCopy, "Copy code", Modifier.size(18.dp)) }
    }
}

// =================================================================================================
// Password history timeline
// =================================================================================================

@Composable
private fun HistorySection(
    currentPassword: String,
    currentSince: Long,
    history: List<PasswordHistoryEntity>,
    onCopy: (String) -> Unit,
    onRestore: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(true) }
    var confirmRestore by remember { mutableStateOf<PasswordHistoryEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<PasswordHistoryEntity?>(null) }

    SectionCard(title = "Password history") {
        val label = when (history.size) {
            0 -> "No older passwords yet"
            1 -> "1 older password"
            else -> "${history.size} older passwords"
        }
        ExpandHeader(Icons.Rounded.History, label, open) { open = !open }
        AnimatedVisibility(open, enter = expandVertically(Motion.size) + fadeIn(), exit = shrinkVertically(Motion.size) + fadeOut()) {
            Column(Modifier.padding(bottom = 8.dp)) {
                if (currentPassword.isNotEmpty()) {
                    TimelineItem(isCurrent = true, isLast = history.isEmpty()) {
                        Text("Current password", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                        Text(
                            "In use since ${TimeText.date(currentSince)} (${TimeText.duration(currentSince, System.currentTimeMillis())})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (history.isEmpty()) {
                    Text(
                        "When you change this password, the old one is kept here automatically — with the dates it was in use.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                history.forEachIndexed { i, h ->
                    HistoryItem(
                        item = h,
                        isLast = i == history.lastIndex,
                        onCopy = { onCopy(h.password) },
                        onRestore = { confirmRestore = h },
                        onDelete = { confirmDelete = h },
                    )
                }
            }
        }
    }

    confirmRestore?.let { h ->
        ConfirmDialog(
            title = "Use this old password again?",
            text = "It becomes the current password. Your current password moves into history, so nothing is lost.",
            confirmLabel = "Restore",
            onConfirm = { onRestore(h.id) },
            onDismiss = { confirmRestore = null },
        )
    }
    confirmDelete?.let { h ->
        ConfirmDialog(
            title = "Delete from history?",
            text = "This old password (used until ${TimeText.date(h.replacedAt)}) will be permanently removed.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { onDelete(h.id) },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun HistoryItem(
    item: PasswordHistoryEntity,
    isLast: Boolean,
    onCopy: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    var revealed by remember { mutableStateOf(false) }
    val gate = LocalSecretGate.current
    TimelineItem(isCurrent = false, isLast = isLast) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RevealableSecret(item.password, revealed, Modifier.weight(1f))
            IconButton(onClick = { gate.toggle(revealed) { revealed = it } }) {
                Icon(if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show", Modifier.size(18.dp))
            }
        }
        Text(
            "${TimeText.monthYear(item.usedFrom)} → ${TimeText.monthYear(item.replacedAt)} · used for ${TimeText.duration(item.usedFrom, item.replacedAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Replaced ${TimeText.dateTime(item.replacedAt)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        if (item.note.isNotBlank()) {
            Text(item.note, style = MaterialTheme.typography.labelSmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.outline)
        }
        Row(Modifier.padding(top = 2.dp)) {
            SmallAction(Icons.Rounded.ContentCopy, "Copy", onCopy)
            SmallAction(Icons.Rounded.Restore, "Restore", onRestore)
            SmallAction(Icons.Rounded.DeleteOutline, "Delete", onDelete, MaterialTheme.colorScheme.error)
        }
    }
}

/** Vertical timeline: dot + connecting line on the left, content on the right (like a chat history). */
@Composable
private fun TimelineItem(isCurrent: Boolean, isLast: Boolean, content: @Composable () -> Unit) {
    val dotColor by animateColorAsState(
        if (isCurrent) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline, label = "dot",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(start = 20.dp, end = 8.dp),
    ) {
        Column(Modifier.width(16.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(14.dp))
            Box(Modifier.size(if (isCurrent) 12.dp else 10.dp).background(dotColor, CircleShape))
            if (!isLast) {
                Box(
                    Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) { content() }
    }
}

@Composable
private fun SmallAction(icon: ImageVector, label: String, onClick: () -> Unit, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
        Icon(icon, null, Modifier.size(16.dp), tint = color)
        Spacer(Modifier.width(6.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ExpandHeader(icon: ImageVector, label: String, open: Boolean, onClick: () -> Unit) {
    val rotation by animateFloatAsState(if (open) 180f else 0f, Motion.smooth(), label = "chevron")
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.ExpandMore, null, Modifier.rotate(rotation))
    }
}
