package com.personal.passvault.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Sync
import com.personal.passvault.work.ReminderWorker
import com.personal.passvault.core.crypto.VaultKeyManager
import com.personal.passvault.ui.components.ConfirmDialog
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Widgets
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.passvault.BuildConfig
import com.personal.passvault.data.repo.ThemeMode
import com.personal.passvault.domain.PasswordStrength
import com.personal.passvault.domain.TimeText
import com.personal.passvault.ui.components.Biometric
import com.personal.passvault.ui.components.SectionCard
import com.personal.passvault.ui.components.StrengthMeter
import com.personal.passvault.ui.components.findFragmentActivity

private val AutoLockOptions = listOf(0 to "Immediately", 30 to "After 30 seconds", 60 to "After 1 minute", 300 to "After 5 minutes", 900 to "After 15 minutes")
private val ClipboardOptions = listOf(15 to "15 seconds", 30 to "30 seconds", 60 to "1 minute", 120 to "2 minutes")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onBackup: () -> Unit,
    onSecurity: () -> Unit = {},
    onImport: () -> Unit = {},
    onKit: () -> Unit = {},
    vm: SettingsViewModel = hiltViewModel(),
) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val autofillManager = remember { context.getSystemService(AutofillManager::class.java) }
    var autofillOn by remember { mutableStateOf(false) }
    var notificationsOn by remember { mutableStateOf(ReminderWorker.canNotify(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        autofillOn = runCatching { autofillManager?.hasEnabledAutofillServices() == true }.getOrDefault(false)
        notificationsOn = ReminderWorker.canNotify(context)
    }
    var askedThisVisit by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsOn = granted && ReminderWorker.canNotify(context)
    }
    fun enableNotifications() {
        // First tap: the system permission dialog. If that was refused (or Android won't show it again),
        // the next tap opens PassVault's notification settings instead.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedThisVisit) {
            askedThisVisit = true
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
            }
        }
    }
    val snackbar = remember { SnackbarHostState() }
    val deviceBiometric = remember { Biometric.isAvailable(context) }

    var dialog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            SectionCard(title = "Security") {
                SettingRow(
                    Icons.Rounded.Fingerprint, "Fingerprint unlock",
                    if (!deviceBiometric) "No strong fingerprint/face unlock set up on this phone"
                    else if (vm.biometricOn) "On — master password still asked every 7 days" else "Off",
                    enabled = deviceBiometric,
                    trailing = {
                        Switch(
                            checked = vm.biometricOn,
                            enabled = deviceBiometric,
                            onCheckedChange = { on ->
                                if (!on) vm.disableBiometric()
                                else {
                                    val activity = context.findFragmentActivity()
                                    val cipher = vm.biometricEnrollCipher()
                                    if (activity != null && cipher != null) {
                                        Biometric.prompt(
                                            activity, cipher, "Turn on fingerprint unlock", "Touch the sensor to confirm", "Cancel",
                                            onSuccess = vm::enableBiometric, onError = { },
                                        )
                                    }
                                }
                            },
                        )
                    },
                )
                SettingRow(Icons.Rounded.Timer, "Auto-lock", AutoLockOptions.firstOrNull { it.first == s.autoLockSeconds }?.second ?: "${s.autoLockSeconds}s") { dialog = "autolock" }
                SettingRow(Icons.Rounded.ContentPaste, "Clear copied passwords after", ClipboardOptions.firstOrNull { it.first == s.clipboardSeconds }?.second ?: "${s.clipboardSeconds}s") { dialog = "clipboard" }
                SettingRow(Icons.Rounded.Key, "Change master password", "Re-encrypts your vault key with a new password") { dialog = "password" }
                SettingRow(
                    Icons.Rounded.Fingerprint, "Confirm before showing secrets",
                    "Fingerprint or phone PIN to show/copy passwords, codes and card numbers (once per 30 s)",
                    trailing = { Switch(checked = s.revealNeedsAuth, onCheckedChange = { vm.setRevealNeedsAuth(it) }) },
                )
                if (!vm.inDecoy) {
                    SettingRow(
                        Icons.Rounded.TheaterComedy, "Decoy vault",
                        when (vm.decoyStatus) {
                            VaultKeyManager.DecoyStatus.ON -> "On — a second password opens a harmless fake vault"
                            VaultKeyManager.DecoyStatus.NEEDS_PASSWORD -> "Set the decoy password again (master password was changed)"
                            VaultKeyManager.DecoyStatus.OFF -> "Off — for when someone forces you to unlock"
                        },
                        tint = if (vm.decoyStatus == VaultKeyManager.DecoyStatus.NEEDS_PASSWORD) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    ) { dialog = "decoy" }
                }
                SettingRow(Icons.Rounded.GppGood, "Security check", "Weak, reused and old passwords · breach check", onClick = onSecurity)
            }

            SectionCard(title = "Autofill & import") {
                SettingRow(
                    Icons.Rounded.EditNote, "Autofill",
                    if (autofillOn) "On — PassVault fills logins in apps and browsers"
                    else "Off — tap to choose PassVault as your autofill service",
                    tint = if (autofillOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                ) {
                    val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                        .setData(Uri.parse("package:${context.packageName}"))
                    runCatching { context.startActivity(intent) }
                        .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } }
                }
                SettingRow(Icons.Rounded.UploadFile, "Import / sync from Google", "Adds new logins, updates changed passwords (old ones kept)", onClick = onImport)
                SettingRow(
                    Icons.Rounded.Widgets, "Home-screen widget",
                    "Long-press your home screen → Widgets → PassVault: lock or generate in one tap",
                )
            }

            SectionCard(title = "Reminders") {
                if (!notificationsOn) {
                    SettingRow(
                        Icons.Rounded.NotificationsOff, "Notifications are off",
                        "Tap to allow, so PassVault can remind you",
                        tint = MaterialTheme.colorScheme.error,
                    ) { enableNotifications() }
                }
                SettingRow(
                    Icons.Rounded.CloudUpload, "Backup reminders",
                    "If your vault isn't backed up, or changes haven't uploaded for 2 days",
                    trailing = { Switch(checked = s.backupReminders, onCheckedChange = { vm.setBackupReminders(it) }) },
                )
                SettingRow(
                    Icons.Rounded.Psychology, "Master password check",
                    "If you haven't typed your master password for 30 days",
                    trailing = { Switch(checked = s.passwordReminders, onCheckedChange = { vm.setPasswordReminders(it) }) },
                )
                SettingRow(
                    Icons.Rounded.Sync, "Monthly sync from Google",
                    "Reminds you to import from Google Password Manager — adds new, updates changed",
                    trailing = { Switch(checked = s.googleSyncReminders, onCheckedChange = { vm.setGoogleSyncReminders(it) }) },
                )
                SettingRow(Icons.Rounded.NotificationsActive, "Send a test notification", "See what a reminder looks like") {
                    if (!ReminderWorker.sendTest(context)) enableNotifications()
                }
            }

            SectionCard(title = "Appearance") {
                SettingRow(Icons.Rounded.Palette, "Theme", s.themeMode.label) { dialog = "theme" }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SettingRow(
                        Icons.Rounded.Wallpaper, "Colours from wallpaper", "Material You dynamic colour",
                        trailing = { Switch(checked = s.dynamicColor, onCheckedChange = { vm.setDynamicColor(it) }) },
                    )
                }
            }

            SectionCard(title = "Icons") {
                SettingRow(
                    Icons.Rounded.Language, "Download website icons",
                    "Fetched directly from each website — no third-party icon service",
                    trailing = { Switch(checked = s.downloadWebIcons, onCheckedChange = { vm.setDownloadIcons(it) }) },
                )
                SettingRow(Icons.Rounded.Refresh, "Refresh website icons", "Download them again") { vm.refreshWebIcons() }
            }

            SectionCard(title = "Backup") {
                val backup by vm.backup.collectAsStateWithLifecycle()
                SettingRow(
                    Icons.Rounded.Backup, "Backup & restore",
                    when {
                        !backup.connected -> "Not set up — connect your Telegram bot"
                        backup.lastBackupAt == 0L -> "Connected to @${backup.botUsername ?: "bot"} · no backup yet"
                        else -> "Last backup ${TimeText.ago(backup.lastBackupAt)} · @${backup.botUsername ?: "bot"}"
                    },
                    tint = if (backup.connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    onClick = onBackup,
                )
                SettingRow(
                    Icons.Rounded.Print, "Emergency kit",
                    "Print a recovery sheet: restore steps, recovery codes, a box for your master password",
                    onClick = onKit,
                )
            }

            SectionCard(title = "Danger zone") {
                SettingRow(
                    Icons.Rounded.DeleteForever, "Wipe vault",
                    "Permanently delete every entry from this phone",
                    tint = MaterialTheme.colorScheme.error,
                ) { dialog = "wipe" }
            }

            SectionCard(title = "About") {
                SettingRow(
                    Icons.Rounded.Info, "PassVault ${BuildConfig.VERSION_NAME}",
                    "AES-256-GCM · Argon2id (45 MiB, 2 passes) · SQLCipher database · keys in Android Keystore. Your data never leaves this phone.",
                )
            }
        }
    }

    when (dialog) {
        "autolock" -> OptionsDialog("Auto-lock", AutoLockOptions, s.autoLockSeconds, { vm.setAutoLock(it) }) { dialog = null }
        "clipboard" -> OptionsDialog("Clear clipboard after", ClipboardOptions, s.clipboardSeconds, { vm.setClipboard(it) }) { dialog = null }
        "theme" -> OptionsDialog("Theme", ThemeMode.entries.map { it to it.label }, s.themeMode, { vm.setTheme(it) }) { dialog = null }
        "password" -> ChangePasswordDialog(vm) { dialog = null }
        "wipe" -> WipeDialog(vm) { dialog = null }
        "decoy" -> DecoyDialog(vm) { dialog = null }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint.copy(alpha = alpha), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
private fun <T> OptionsDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value); onDismiss() }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = { onSelect(value); onDismiss() })
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ChangePasswordDialog(vm: SettingsViewModel, onDismiss: () -> Unit) {
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val bits = remember(new) { PasswordStrength.bits(new) }
    val ok = old.isNotEmpty() && new.length >= 12 && new == confirm &&
        PasswordStrength.levelForBits(bits) >= PasswordStrength.Level.FAIR && new != old

    AlertDialog(
        onDismissRequest = { if (!vm.busy) onDismiss() },
        title = { Text("Change master password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PasswordInput(old, { old = it; error = null }, "Current master password")
                PasswordInput(new, { new = it }, "New master password")
                StrengthMeter(bits, Modifier.fillMaxWidth(), showCrackTime = false)
                PasswordInput(confirm, { confirm = it }, "Type the new one again")
                Text(
                    "At least 12 characters and \"Fair\" strength. Write the new one down — it can't be recovered.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = ok && !vm.busy, onClick = {
                vm.changeMasterPassword(old.toCharArray(), new.toCharArray()) { success ->
                    if (success) onDismiss() else error = "Current master password is wrong"
                }
            }) { Text(if (vm.busy) "Changing…" else "Change") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !vm.busy) { Text("Cancel") } },
    )
}

@Composable
private fun WipeDialog(vm: SettingsViewModel, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    var master by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!vm.busy) onDismiss() },
        title = { Text("Wipe vault?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Every password, recovery code and note on this phone will be permanently deleted. Only a backup could bring them back.")
                OutlinedTextField(typed, { typed = it }, label = { Text("Type DELETE") }, singleLine = true)
                PasswordInput(master, { master = it; error = null }, "Master password")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = typed == "DELETE" && master.isNotEmpty() && !vm.busy,
                onClick = {
                    vm.wipeVault(master.toCharArray()) { success ->
                        if (success) onDismiss() else error = "Wrong master password"
                    }
                },
            ) { Text("Wipe everything", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !vm.busy) { Text("Cancel") } },
    )
}

@Composable
private fun DecoyDialog(vm: SettingsViewModel, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val status = vm.decoyStatus
    val ok = password.length >= 8 && password == confirm

    AlertDialog(
        onDismissRequest = { if (!vm.busy) onDismiss() },
        title = { Text("Decoy vault") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "A second password that opens a separate, harmless vault — for when someone forces you to unlock. " +
                        "It looks exactly like the real thing.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "• Fingerprint always opens your REAL vault.\n" +
                        "• The decoy is never sent to Telegram.\n" +
                        "• To fill it: lock PassVault, unlock with the decoy password, add a few believable logins.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PasswordInput(password, { password = it; error = null }, if (status == VaultKeyManager.DecoyStatus.ON) "New decoy password" else "Decoy password")
                PasswordInput(confirm, { confirm = it; error = null }, "Type it again")
                Text("At least 8 characters, different from your master password.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (status != VaultKeyManager.DecoyStatus.OFF) {
                    TextButton(onClick = { confirmDelete = true }, enabled = !vm.busy) {
                        Text("Delete decoy vault", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = ok && !vm.busy, onClick = {
                vm.setDecoyPassword(password.toCharArray()) { err -> if (err == null) onDismiss() else error = err }
            }) { Text(if (vm.busy) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !vm.busy) { Text("Cancel") } },
    )

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete the decoy vault?",
            text = "Its entries are deleted and the decoy password stops working. Your real vault is not touched.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { vm.removeDecoy(); onDismiss() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun PasswordInput(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}
