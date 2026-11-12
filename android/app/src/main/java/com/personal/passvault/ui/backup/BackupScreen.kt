package com.personal.passvault.ui.backup

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.passvault.data.backup.RestoreMode
import com.personal.passvault.data.repo.BackupState
import com.personal.passvault.domain.TimeText
import com.personal.passvault.ui.components.ConfirmDialog
import com.personal.passvault.ui.components.SectionCard
import com.personal.passvault.ui.theme.Motion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    onRestoredNewVault: () -> Unit,
    vm: BackupViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmDisconnect by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        vm.exportTo(it)
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        vm.restoreFromFile(it)
    }
    fun openUrl(url: String) {
        vm.leavingAppForTelegram()
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (vm.setupMode) "Restore from backup" else "Backup") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (vm.setupMode) {
                Text(
                    "Bring your vault back from an encrypted backup. You'll need the master password you used on your old phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            AnimatedContent(state.connected, label = "connected") { connected ->
                if (!connected) {
                    ConnectCard(
                        ui = vm.connect,
                        title = if (vm.setupMode) "Connect the bot you used before" else "Connect your Telegram bot",
                        onToken = vm::setToken,
                        onVerify = vm::verifyToken,
                        onOpenUrl = ::openUrl,
                        onManual = vm::useManualChatId,
                        onManualId = vm::setManualChatId,
                        onSubmitManual = vm::submitManualChatId,
                        onRestart = vm::restartConnect,
                    )
                } else if (!vm.setupMode) {
                    StatusCard(state, vm.backingUp, onBackupNow = vm::backupNow, onAuto = { vm.setAutoBackup(it) })
                } else {
                    SectionCard(title = "Telegram") {
                        ActionRow(Icons.AutoMirrored.Rounded.Send, "Connected to @${state.botUsername ?: "your bot"}", "The newest backup is the pinned message") {}
                    }
                }
            }

            SectionCard(title = "Restore") {
                ActionRow(
                    Icons.Rounded.CloudDownload, "Latest backup from Telegram",
                    if (state.connected) "Downloads the pinned backup from your bot chat" else "Connect Telegram first",
                    enabled = state.connected,
                ) { vm.restoreFromTelegram() }
                ActionRow(Icons.Rounded.FileOpen, "Choose a backup file", "A .pvlt file saved from Telegram or exported earlier") {
                    vm.holdForPicker()
                    openLauncher.launch(arrayOf("*/*"))
                }
            }

            if (!vm.setupMode) {
                SectionCard(title = "Backup file") {
                    ActionRow(Icons.Rounded.Save, "Export encrypted backup", "Save a .pvlt file anywhere — same encryption as Telegram backups") {
                        vm.holdForPicker()
                        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        exportLauncher.launch("passvault-$stamp.pvlt")
                    }
                }
                if (state.connected) {
                    SectionCard {
                        ActionRow(Icons.Rounded.LinkOff, "Disconnect Telegram", "Stops backups. Backups already in the chat stay there.", tint = MaterialTheme.colorScheme.error) {
                            confirmDisconnect = true
                        }
                    }
                }
                HowItWorks()
            }
        }
    }

    if (vm.restore.stage != RestoreStage.IDLE) {
        RestoreSheet(vm, onRestoredNewVault)
    }
    vm.restore.error?.takeIf { vm.restore.stage == RestoreStage.IDLE }?.let { err ->
        LaunchedEffect(err) { snackbar.showSnackbar(err); vm.closeRestore() }
    }

    if (confirmDisconnect) {
        ConfirmDialog(
            title = "Disconnect Telegram?",
            text = "Automatic backups stop. The backups already in your bot chat are not deleted.",
            confirmLabel = "Disconnect",
            destructive = true,
            onConfirm = { vm.disconnect() },
            onDismiss = { confirmDisconnect = false },
        )
    }
}

// =================================================================================================
// Connect flow: token -> tap link -> press Start in Telegram -> connected
// =================================================================================================

@Composable
private fun ConnectCard(
    ui: ConnectUi,
    title: String,
    onToken: (String) -> Unit,
    onVerify: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onManual: () -> Unit,
    onManualId: (String) -> Unit,
    onSubmitManual: () -> Unit,
    onRestart: () -> Unit,
) {
    SectionCard(title = title) {
        AnimatedContent(
            targetState = ui.step,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally(Motion.offset) { if (forward) it / 2 else -it / 2 } + fadeIn()) togetherWith
                    (slideOutHorizontally(Motion.offset) { if (forward) -it / 2 else it / 2 } + fadeOut())
            },
            label = "pairStep",
        ) { step ->
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (step) {
                    PairStep.TOKEN -> {
                        var show by remember { mutableStateOf(false) }
                        Step(1, "Open @BotFather in Telegram and send /newbot (skip if you already have a bot).")
                        TextButton(onClick = { onOpenUrl("https://t.me/BotFather") }) { Text("Open @BotFather") }
                        Step(2, "Copy the token it gives you (looks like 123456789:AAH…) and paste it here.")
                        OutlinedTextField(
                            value = ui.token,
                            onValueChange = onToken,
                            label = { Text("Bot token") },
                            singleLine = true,
                            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                            trailingIcon = {
                                IconButton(onClick = { show = !show }) {
                                    Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show token")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = onVerify, enabled = ui.token.isNotBlank() && !ui.busy, modifier = Modifier.fillMaxWidth()) {
                            if (ui.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Continue")
                        }
                    }
                    PairStep.WAITING -> {
                        val bot = ui.bot?.username ?: "your bot"
                        Step(3, "Open @$bot and press Start. PassVault will notice automatically.")
                        Button(onClick = { ui.deepLink?.let(onOpenUrl) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.AutoMirrored.Rounded.Send, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Open @$bot in Telegram")
                        }
                        WaitingIndicator(if (ui.busy) "Connecting…" else "Waiting for you to press Start…")
                        Row {
                            TextButton(onClick = onManual) { Text("Enter chat ID instead") }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = onRestart) { Text("Back") }
                        }
                    }
                    PairStep.MANUAL -> {
                        Text(
                            "If your PassVault bot server is running, it already replied to your Start with your chat ID " +
                                "(or send it /id). Otherwise send any message to @userinfobot. " +
                                "Make sure you've pressed Start in @${ui.bot?.username ?: "your bot"} so it may message you.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedTextField(
                            value = ui.manualChatId,
                            onValueChange = onManualId,
                            label = { Text("Your Telegram chat ID") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = onSubmitManual, enabled = ui.manualChatId.isNotBlank() && !ui.busy, modifier = Modifier.fillMaxWidth()) {
                            if (ui.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Connect")
                        }
                        TextButton(onClick = onRestart) { Text("Back") }
                    }
                }
                AnimatedVisibility(ui.error != null, enter = expandVertically(Motion.size) + fadeIn(), exit = shrinkVertically(Motion.size) + fadeOut()) {
                    ErrorText(ui.error.orEmpty())
                }
            }
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(24.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("$n", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun WaitingIndicator(text: String) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseScale",
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Box(
            Modifier
                .size(14.dp)
                .graphicsLayer { scaleX = pulse; scaleY = pulse; alpha = 2f - pulse }
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// =================================================================================================
// Status
// =================================================================================================

@Composable
private fun StatusCard(state: BackupState, backingUp: Boolean, onBackupNow: () -> Unit, onAuto: (Boolean) -> Unit) {
    val stale = state.lastBackupAt > 0 && System.currentTimeMillis() - state.lastBackupAt > 7L * 24 * 60 * 60 * 1000
    SectionCard(title = "Telegram backup") {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Rounded.Send, null, tint = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("@${state.botUsername ?: "your bot"}", style = MaterialTheme.typography.titleMedium)
                    AnimatedContent(state.lastBackupAt, label = "lastBackup") { at ->
                        Text(
                            if (at == 0L) "No backup yet"
                            else "Last backup ${TimeText.dateTime(at)} · ${state.lastBackupEntries} entries",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (stale || at == 0L) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            AnimatedVisibility(state.hasUnbackedChanges && state.lastError == null) {
                StatusLine(Icons.Rounded.Schedule, "Recent changes are waiting to upload")
            }
            AnimatedVisibility(state.lastError != null) {
                ErrorText(state.lastError.orEmpty(), Modifier.padding(top = 10.dp))
            }
            Spacer(Modifier.height(14.dp))
            Button(onClick = onBackupNow, enabled = !backingUp, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                AnimatedContent(backingUp, label = "backingUp") { busy ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Icon(Icons.Rounded.CloudUpload, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (busy) "Encrypting & uploading…" else "Back up now")
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Sync, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Automatic backup", style = MaterialTheme.typography.bodyLarge)
                    Text("A minute after you change something, when online", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.autoBackup, onCheckedChange = onAuto)
            }
        }
    }
}

@Composable
private fun StatusLine(icon: ImageVector, text: String) {
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorText(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.5f
    Surface(onClick = onClick, enabled = enabled, color = androidx.compose.ui.graphics.Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint.copy(alpha = alpha), modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
            }
        }
    }
}

@Composable
private fun HowItWorks() {
    SectionCard(title = "How it works") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Bullet("Your vault is encrypted on this phone with your master password before anything is sent.")
            Bullet("Telegram and the bot only ever see a scrambled .pvlt file — nobody can open it without your master password.")
            Bullet("The newest backup is pinned in the chat so a new phone can always find it.")
            Bullet("After changing your master password, older backups still need the OLD password.")
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row {
        Icon(Icons.Rounded.Lock, null, Modifier.size(16.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// =================================================================================================
// Restore sheet
// =================================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestoreSheet(vm: BackupViewModel, onRestoredNewVault: () -> Unit) {
    val r = vm.restore
    val busy = r.stage == RestoreStage.LOADING || r.stage == RestoreStage.DECRYPTING || r.stage == RestoreStage.IMPORTING
    var password by remember { mutableStateOf("") }
    var confirmReplace by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = { if (!busy) vm.closeRestore() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        AnimatedContent(r.stage, label = "restoreStage", modifier = Modifier.padding(horizontal = 20.dp)) { stage ->
            Column(
                Modifier.fillMaxWidth().padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (stage) {
                    RestoreStage.LOADING -> Busy("Getting the backup…")
                    RestoreStage.DECRYPTING -> Busy("Decrypting with your master password…")
                    RestoreStage.IMPORTING -> Busy("Restoring your vault…")
                    RestoreStage.NEED_PASSWORD -> {
                        Text("Unlock the backup", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Enter the master password that was in use when this backup was made.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Master password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        r.error?.let { ErrorText(it) }
                        Button(
                            onClick = { vm.unlockBackup(password.toCharArray()); password = "" },
                            enabled = password.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                        ) { Text("Unlock backup") }
                    }
                    RestoreStage.READY -> {
                        Icon(Icons.Rounded.CheckCircle, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.secondary)
                        Text("Backup unlocked", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${r.entryCount} entries · made ${TimeText.dateTime(r.backupDate)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        r.error?.let { ErrorText(it) }
                        if (vm.setupMode) {
                            Text(
                                "Your vault on this phone will use the same master password.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Button(onClick = { vm.restoreAsNewVault(onRestoredNewVault) }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                                Text("Restore vault")
                            }
                        } else {
                            Button(onClick = { vm.applyRestore(RestoreMode.MERGE) }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                                Text("Merge with this phone (recommended)")
                            }
                            Text(
                                "Keeps the newer version of each entry. No password is lost — any that get replaced go into history.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            OutlinedButton(onClick = { confirmReplace = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Replace everything with the backup")
                            }
                        }
                    }
                    RestoreStage.DONE -> {
                        Icon(Icons.Rounded.CheckCircle, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.secondary)
                        Text("Restored", style = MaterialTheme.typography.titleLarge)
                        r.result?.let {
                            Text(
                                "${it.added} added · ${it.updated} updated · ${it.keptLocal} kept from this phone",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FilledTonalButton(onClick = vm::closeRestore, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                    }
                    RestoreStage.IDLE -> Unit
                }
            }
        }
    }

    if (confirmReplace) {
        ConfirmDialog(
            title = "Replace everything?",
            text = "Every entry on this phone is deleted and replaced with the ${r.entryCount} entries from the backup. Changes made after the backup are lost.",
            confirmLabel = "Replace",
            destructive = true,
            onConfirm = { vm.applyRestore(RestoreMode.REPLACE) },
            onDismiss = { confirmReplace = false },
        )
    }
}

@Composable
private fun Busy(text: String) {
    Spacer(Modifier.height(12.dp))
    CircularProgressIndicator()
    Text(text, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(12.dp))
}
