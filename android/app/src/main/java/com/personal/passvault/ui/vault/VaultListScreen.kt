package com.personal.passvault.ui.vault

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CloudUpload
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Wifi
import com.personal.passvault.data.repo.BackupState
import com.personal.passvault.domain.TimeText
import com.personal.passvault.domain.EntrySummary
import com.personal.passvault.domain.EntryType
import com.personal.passvault.ui.components.ConfirmDialog
import com.personal.passvault.ui.components.LocalSecretGate
import com.personal.passvault.ui.components.EntryAvatar
import com.personal.passvault.ui.components.sharedElementOrSelf
import com.personal.passvault.ui.theme.Motion
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VaultListScreen(
    onOpen: (String) -> Unit,
    onEdit: (String) -> Unit,
    onAdd: (EntryType) -> Unit,
    onGenerator: () -> Unit,
    onSettings: () -> Unit,
    onBackup: () -> Unit,
    onSecurity: () -> Unit = {},
    vm: VaultListViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val backup by vm.backup.collectAsStateWithLifecycle()

    // Ask once (Android 13+) so backup / master-password reminders can be shown
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && vm.shouldAskNotifications()) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var searchActive by remember { mutableStateOf(false) }
    var menuItem by remember { mutableStateOf<EntrySummary?>(null) }
    var lastMenuItem by remember { mutableStateOf<EntrySummary?>(null) }
    var confirmDelete by remember { mutableStateOf<EntrySummary?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    if (menuItem != null) lastMenuItem = menuItem

    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val blur by animateDpAsState(if (menuItem != null) 14.dp else 0.dp, Motion.smooth(), label = "blur")
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    fun toast(msg: String) = scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) }
    val gate = LocalSecretGate.current
    fun copyPassword(item: EntrySummary) = gate {
        vm.copyPassword(item.id) { ok -> if (!ok) toast("No password saved for ${item.title}") }
    }

    BackHandler(enabled = menuItem != null || searchActive) {
        if (menuItem != null) menuItem = null
        else { searchActive = false; vm.setQuery("") }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .blur(blur)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                    navigationIcon = {
                        AnimatedVisibility(searchActive, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                            IconButton(onClick = { searchActive = false; vm.setQuery("") }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close search")
                            }
                        }
                    },
                    title = {
                        AnimatedContent(
                            targetState = searchActive,
                            transitionSpec = {
                                (fadeIn() + slideInVertically { it / 2 }) togetherWith (fadeOut() + slideOutVertically { -it / 2 })
                            },
                            label = "titleSearch",
                        ) { active ->
                            if (active) SearchField(ui.query, vm::setQuery)
                            else Column {
                                Text("PassVault", style = MaterialTheme.typography.titleLarge)
                                AnimatedContent(ui.totalCount, label = "count") { n ->
                                    Text(
                                        if (n == 1) "1 item" else "$n items",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        AnimatedVisibility(!searchActive, enter = fadeIn(), exit = fadeOut()) {
                            Row {
                                IconButton(onClick = { searchActive = true }) { Icon(Icons.Rounded.Search, "Search") }
                                IconButton(onClick = onSecurity) { Icon(Icons.Rounded.GppGood, "Security check") }
                                IconButton(onClick = onGenerator) { Icon(Icons.Rounded.Password, "Password generator") }
                                IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings") }
                                IconButton(onClick = vm::lock) { Icon(Icons.Rounded.Lock, "Lock now") }
                            }
                        }
                    },
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showAddSheet = true },
                    expanded = fabExpanded,
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text("Add") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                BackupBanner(backup, hasEntries = ui.totalCount > 0, onClick = onBackup)
                FilterRow(ui.filter, vm::setFilter)
                when {
                    ui.loading -> Unit
                    ui.totalCount == 0 -> EmptyState(
                        icon = Icons.Rounded.Shield,
                        title = "Your vault is empty",
                        text = "Tap Add to save your first password or recovery codes.",
                    )
                    ui.items.isEmpty() -> EmptyState(
                        icon = Icons.Rounded.SearchOff,
                        title = "Nothing found",
                        text = if (ui.query.isNotBlank()) "No entries match \"${ui.query}\"." else "No entries in ${ui.filter.label}.",
                    )
                    else -> LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 120.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(ui.items, key = { it.id }, contentType = { "entry" }) { item ->
                            SwipeRow(
                                startIcon = Icons.Rounded.ContentCopy,
                                startColor = MaterialTheme.colorScheme.secondary,
                                onSwipeStart = { copyPassword(item) },
                                endIcon = if (item.favorite) Icons.Rounded.StarBorder else Icons.Rounded.Star,
                                endColor = MaterialTheme.colorScheme.tertiary,
                                onSwipeEnd = { vm.toggleFavorite(item) },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = Motion.smooth(),
                                    placementSpec = Motion.offset,
                                    fadeOutSpec = Motion.smooth(),
                                ),
                            ) {
                                VaultRow(
                                    item = item,
                                    onClick = { onOpen(item.id) },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuItem = item
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // ------- Long-press context menu (blurred background, row "lifts" with a spring) -------
        AnimatedVisibility(visible = menuItem != null, enter = fadeIn(), exit = fadeOut()) {
            val item = lastMenuItem ?: return@AnimatedVisibility
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { menuItem = null },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier
                        .padding(28.dp)
                        .animateEnterExit(
                            enter = scaleIn(Motion.bouncy(), initialScale = 0.85f),
                            exit = scaleOut(Motion.fast(), targetScale = 0.92f),
                        ),
                ) {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
                        Row(Modifier.padding(14.dp).width(280.dp), verticalAlignment = Alignment.CenterVertically) {
                            EntryAvatar(item.icon, 48.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
                        Column(Modifier.width(308.dp).padding(vertical = 6.dp)) {
                            if (item.type == EntryType.WIFI) {
                                MenuAction(Icons.Rounded.Key, "Copy Wi-Fi password") { copyPassword(item); menuItem = null }
                            }
                            if (item.type == EntryType.LOGIN) {
                                MenuAction(Icons.Rounded.Key, "Copy password") { copyPassword(item); menuItem = null }
                                MenuAction(Icons.Rounded.Person, "Copy username") {
                                    vm.copyUsername(item.id) { ok -> if (!ok) toast("No username saved") }
                                    menuItem = null
                                }
                            }
                            MenuAction(
                                if (item.favorite) Icons.Rounded.StarBorder else Icons.Rounded.Star,
                                if (item.favorite) "Remove from favourites" else "Add to favourites",
                            ) { vm.toggleFavorite(item); menuItem = null }
                            MenuAction(Icons.Rounded.Edit, "Edit") { menuItem = null; onEdit(item.id) }
                            MenuAction(Icons.Rounded.Delete, "Delete", MaterialTheme.colorScheme.error) {
                                menuItem = null
                                confirmDelete = item
                            }
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { item ->
        ConfirmDialog(
            title = "Delete ${item.title}?",
            text = "This deletes the entry, its password history and recovery codes from this phone. This can't be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { vm.delete(item.id) },
            onDismiss = { confirmDelete = null },
        )
    }

    if (showAddSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }, sheetState = sheetState) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                Text("Add to vault", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                AddOption(Icons.Rounded.Key, "Login", "Password for an app or website") {
                    showAddSheet = false; onAdd(EntryType.LOGIN)
                }
                AddOption(Icons.Rounded.VpnKey, "Recovery codes", "Backup codes for 2-step verification") {
                    showAddSheet = false; onAdd(EntryType.RECOVERY_CODES)
                }
                AddOption(Icons.Rounded.QrCodeScanner, "2FA codes", "Authenticator codes — scan the setup QR") {
                    showAddSheet = false; onAdd(EntryType.TOTP)
                }
                AddOption(Icons.Rounded.CreditCard, "Card", "Debit/credit card, CVV and PIN") {
                    showAddSheet = false; onAdd(EntryType.CARD)
                }
                AddOption(Icons.Rounded.Wifi, "Wi-Fi", "Network password — share it with a QR code") {
                    showAddSheet = false; onAdd(EntryType.WIFI)
                }
                AddOption(Icons.Rounded.Description, "Secure note", "PINs, security answers, anything private") {
                    showAddSheet = false; onAdd(EntryType.NOTE)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultRow(item: EntrySummary, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EntryAvatar(item.icon, 52.dp, Modifier.sharedElementOrSelf("avatar-${item.id}"))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                val low = item.codesLeft != null && item.codesTotal != null && item.codesTotal > 0 && item.codesLeft <= 2
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AnimatedVisibility(item.favorite, enter = scaleIn(Motion.bouncy()) + fadeIn(), exit = scaleOut() + fadeOut()) {
                Icon(Icons.Rounded.Star, "Favourite", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
            }
        }
        HorizontalDivider(Modifier.padding(start = 82.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }
}

/** Nudges until backups are set up, and when they fail or get old. */
@Composable
private fun BackupBanner(state: BackupState, hasEntries: Boolean, onClick: () -> Unit) {
    val now = System.currentTimeMillis()
    val text = when {
        !state.loaded -> null
        !state.connected && hasEntries -> "Not backed up. Lose this phone and the vault is gone — set up Telegram backup."
        state.connected && state.lastError != null -> "Backup problem: ${state.lastError}"
        state.connected && state.lastBackupAt > 0 && now - state.lastBackupAt > 7L * 24 * 60 * 60 * 1000 ->
            "Last backup was ${TimeText.ago(state.lastBackupAt)}. Tap to back up now."
        else -> null
    }
    var last by remember { mutableStateOf(text) }
    if (text != null) last = text
    AnimatedVisibility(
        visible = text != null,
        enter = expandVertically(Motion.size) + fadeIn(),
        exit = shrinkVertically(Motion.size) + fadeOut(),
    ) {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CloudUpload, null, Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(last.orEmpty(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
            }
        }
    }
}

@Composable
private fun FilterRow(selected: VaultFilter, onSelect: (VaultFilter) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(VaultFilter.entries, key = { it.name }) { f ->
            FilterChip(
                selected = f == selected,
                onClick = { onSelect(f) },
                label = { Text(f.label) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(contentAlignment = Alignment.CenterStart) {
        if (query.isEmpty()) {
            Text("Search vault", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
    }
}

@Composable
private fun MenuAction(icon: ImageVector, label: String, color: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = color, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AddOption(icon: ImageVector, title: String, text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, text: String) {
    val floatY by rememberInfiniteTransition(label = "float").animateFloat(
        initialValue = -6f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "floatY",
    )
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .graphicsLayer { translationY = floatY * density }
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(80.dp))
    }
}
