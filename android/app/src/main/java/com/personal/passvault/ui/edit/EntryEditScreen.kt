package com.personal.passvault.ui.edit

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.personal.passvault.domain.EntryType
import com.personal.passvault.domain.IconSpec
import com.personal.passvault.domain.PasswordStrength
import com.personal.passvault.domain.TargetKind
import com.personal.passvault.domain.TimeText
import com.personal.passvault.ui.components.ConfirmDialog
import com.personal.passvault.ui.components.EntryAvatar
import com.personal.passvault.ui.components.LocalClipboardHelper
import com.personal.passvault.ui.components.LocalSecretGate
import com.personal.passvault.ui.components.toggle
import com.personal.passvault.ui.components.SectionCard
import com.personal.passvault.ui.components.StrengthMeter
import com.personal.passvault.ui.components.TargetIcon
import com.personal.passvault.ui.generator.GeneratorPanel
import com.personal.passvault.ui.theme.Motion
import com.personal.passvault.ui.theme.SecretTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditScreen(
    onDone: (id: String, isNew: Boolean) -> Unit,
    onCancel: () -> Unit,
    onScan: () -> Unit = {},
    scanResult: String? = null,
    onScanConsumed: () -> Unit = {},
    vm: EntryEditViewModel = hiltViewModel(),
) {
    val f = vm.form
    val clipboard = LocalClipboardHelper.current
    val context = LocalContext.current

    // Result coming back from the QR scanner screen
    LaunchedEffect(scanResult) {
        if (scanResult != null) {
            if (!vm.applyScan(scanResult)) {
                Toast.makeText(context, "That QR code isn't a 2FA or Wi-Fi code", Toast.LENGTH_SHORT).show()
            }
            onScanConsumed()
        }
    }
    var showDiscard by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showGenerator by remember { mutableStateOf(false) }
    var confirmReplaceCodes by remember { mutableStateOf(false) }

    BackHandler(enabled = vm.isDirty) { showDiscard = true }

    fun attemptSave() {
        if (vm.replacesCodeSet()) confirmReplaceCodes = true else vm.save(onDone)
    }

    val typeName = when (f.type) {
        EntryType.LOGIN -> "login"
        EntryType.RECOVERY_CODES -> "recovery codes"
        EntryType.NOTE -> "note"
        EntryType.TOTP -> "2FA codes"
        EntryType.CARD -> "card"
        EntryType.WIFI -> "Wi-Fi"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = { if (vm.isDirty) showDiscard = true else onCancel() }) {
                        Icon(Icons.Rounded.Close, "Cancel")
                    }
                },
                title = { Text(if (vm.isNew) "New $typeName" else "Edit $typeName") },
                actions = {
                    Button(
                        onClick = { attemptSave() },
                        enabled = f.canSave && !vm.saving && !vm.loading,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        if (vm.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Save")
                    }
                },
            )
        },
    ) { padding ->
        if (vm.loading) return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (vm.isNew) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(EntryType.entries, key = { it.name }) { t ->
                        FilterChip(
                            selected = f.type == t,
                            onClick = { vm.update { it.copy(type = t) } },
                            label = { Text(t.label) },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
            }

            // ---- Live icon preview + title ----
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                EntryAvatar(
                    IconSpec(
                        title = f.title.ifBlank { "?" },
                        packages = f.targets.filter { it.kind == TargetKind.APP }.mapNotNull { it.packageName },
                        domains = f.targets.filter { it.kind == TargetKind.WEB }.mapNotNull { it.domain },
                    ),
                    64.dp,
                )
                Spacer(Modifier.width(14.dp))
                OutlinedTextField(
                    value = f.title,
                    onValueChange = { v -> vm.update { it.copy(title = v) } },
                    label = { Text("Name (e.g. Instagram, SBI, Gmail)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            if (f.type == EntryType.LOGIN) LoginSection(vm, onGenerate = { showGenerator = true })

            when (f.type) {
                EntryType.LOGIN -> TotpSection(vm, onScan, optional = true)
                EntryType.TOTP -> TotpSection(vm, onScan, optional = false)
                EntryType.CARD -> CardSection(vm)
                EntryType.WIFI -> WifiSection(vm, onScan, onGenerate = { showGenerator = true })
                else -> Unit
            }

            if (f.type == EntryType.RECOVERY_CODES) {
                SectionCard(title = "Recovery codes") {
                    OutlinedTextField(
                        value = f.codesText,
                        onValueChange = { v -> vm.update { it.copy(codesText = v) } },
                        label = { Text("Paste codes — one per line") },
                        textStyle = SecretTextStyle,
                        minLines = 6,
                        supportingText = {
                            Text(
                                if (f.codes.isEmpty()) "Tip: copy them straight from the account's 2-step verification page"
                                else "${f.codes.size} codes" + if (vm.replacesCodeSet()) " · new set — the old set will be kept in history" else "",
                            )
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            // ---- Where this is used (drives the icons; for cards: the bank's app) ----
            if (f.type != EntryType.WIFI) UsedOnSection(vm, onLinkApp = { showAppPicker = true })

            SectionCard(title = if (f.type == EntryType.NOTE) "Note" else "Notes") {
                OutlinedTextField(
                    value = f.notes,
                    onValueChange = { v -> vm.update { it.copy(notes = v) } },
                    label = { Text(if (f.type == EntryType.NOTE) "Your private note" else "Security questions, PIN hints, anything else") },
                    minLines = if (f.type == EntryType.NOTE) 6 else 3,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            SectionCard {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Star, null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(14.dp))
                    Text("Favourite", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = f.favorite, onCheckedChange = { v -> vm.update { it.copy(favorite = v) } })
                }
            }
        }
    }

    if (showAppPicker) {
        AppPickerSheet(
            apps = vm.installedApps,
            alreadyLinked = f.targets.mapNotNull { it.packageName }.toSet(),
            onPick = { vm.addApp(it); showAppPicker = false },
            onDismiss = { showAppPicker = false },
        )
    }

    if (showGenerator) {
        ModalBottomSheet(
            onDismissRequest = { showGenerator = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            GeneratorPanel(
                onCopy = { clipboard?.copy("Password", it) },
                onUse = { pw -> vm.update { it.copy(password = pw) }; showGenerator = false },
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        }
    }

    if (showDiscard) {
        ConfirmDialog(
            title = "Discard changes?",
            text = "Your changes to this entry won't be saved.",
            confirmLabel = "Discard",
            destructive = true,
            onConfirm = onCancel,
            onDismiss = { showDiscard = false },
        )
    }

    if (confirmReplaceCodes) {
        ConfirmDialog(
            title = "Save as a new set of codes?",
            text = "None of the old codes are in the new list, so the old set will be moved to \"Older code sets\" (kept, not deleted).",
            confirmLabel = "Save",
            onConfirm = { vm.save(onDone) },
            onDismiss = { confirmReplaceCodes = false },
        )
    }
}

@Composable
private fun LoginSection(vm: EntryEditViewModel, onGenerate: () -> Unit) {
    val f = vm.form
    var visible by remember { mutableStateOf(false) }
    val gate = LocalSecretGate.current
    val bits = remember(f.password) { PasswordStrength.bits(f.password) }
    SectionCard(title = "Login") {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = f.username,
                onValueChange = { v -> vm.update { it.copy(username = v) } },
                label = { Text("Username") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = f.email,
                onValueChange = { v -> vm.update { it.copy(email = v) } },
                label = { Text("Email / phone") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = f.password,
                onValueChange = { v -> vm.update { it.copy(password = v) } },
                label = { Text("Password") },
                singleLine = true,
                textStyle = SecretTextStyle,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                trailingIcon = {
                    Row {
                        IconButton(onClick = { gate.toggle(visible) { visible = it } }) {
                            Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show password")
                        }
                        IconButton(onClick = onGenerate) { Icon(Icons.Rounded.Password, "Generate password") }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            AnimatedVisibility(f.password.isNotEmpty(), enter = expandVertically(Motion.size) + fadeIn(), exit = shrinkVertically(Motion.size) + fadeOut()) {
                StrengthMeter(bits, Modifier.fillMaxWidth().padding(horizontal = 4.dp))
            }
            val reuse = vm.reuse
            val usedBefore = reuse?.usedBeforeHere
            AnimatedVisibility(usedBefore != null && f.password.isNotEmpty(), enter = expandVertically(Motion.size) + fadeIn(), exit = shrinkVertically(Motion.size) + fadeOut()) {
                WarningCard(
                    if (usedBefore != null) "You used this exact password here before (until ${TimeText.date(usedBefore.replacedAt)}). Re-using old passwords is risky."
                    else "",
                )
            }
            val others = reuse?.otherEntries.orEmpty()
            AnimatedVisibility(others.isNotEmpty() && f.password.isNotEmpty(), enter = expandVertically(Motion.size) + fadeIn(), exit = shrinkVertically(Motion.size) + fadeOut()) {
                WarningCard("Same password as: ${others.take(3).joinToString()}${if (others.size > 3) " +${others.size - 3} more" else ""}. If one leaks, all of them do.")
            }
            if (!vm.isNew) {
                Text(
                    "Changing the password keeps the old one in this entry's history.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun UsedOnSection(vm: EntryEditViewModel, onLinkApp: () -> Unit) {
    val f = vm.form
    var addingSite by remember { mutableStateOf(false) }
    var site by remember { mutableStateOf("") }
    var siteError by remember { mutableStateOf(false) }
    val suggestions = vm.suggestions()

    fun submitSite() {
        if (vm.addWebsite(site)) { site = ""; addingSite = false; siteError = false } else siteError = true
    }

    SectionCard(title = "Used on — apps & websites") {
        Column(Modifier.animateContentSize(Motion.size)) {
            f.targets.forEach { t ->
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TargetIcon(t.packageName, t.domain, t.label, 36.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (t.kind == TargetKind.APP) "App" else "Website",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { vm.removeTarget(t) }) { Icon(Icons.Rounded.Close, "Remove", Modifier.size(18.dp)) }
                }
            }
            if (f.targets.isEmpty()) {
                Text(
                    "Link the app or website to show its real icon — and open it straight from here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (suggestions.isNotEmpty()) {
                Text(
                    "Suggested",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 6.dp),
                )
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { app ->
                        AssistChip(
                            onClick = { vm.addApp(app) },
                            label = { Text(app.label) },
                            leadingIcon = { TargetIcon(app.packageName, null, app.label, 20.dp) },
                            trailingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(16.dp)) },
                            shape = CircleShape,
                        )
                    }
                }
            }
            AnimatedVisibility(addingSite, enter = expandVertically(Motion.size) + fadeIn(), exit = shrinkVertically(Motion.size) + fadeOut()) {
                OutlinedTextField(
                    value = site,
                    onValueChange = { site = it; siteError = false },
                    label = { Text("Website, e.g. instagram.com") },
                    singleLine = true,
                    isError = siteError,
                    supportingText = if (siteError) ({ Text("That doesn't look like a website address") }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitSite() }),
                    trailingIcon = { TextButton(onClick = { submitSite() }) { Text("Add") } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = onLinkApp, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Apps, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Link app")
                }
                OutlinedButton(onClick = { addingSite = !addingSite }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Language, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add website")
                }
            }
        }
    }
}

@Composable
private fun WarningCard(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
    }
}
