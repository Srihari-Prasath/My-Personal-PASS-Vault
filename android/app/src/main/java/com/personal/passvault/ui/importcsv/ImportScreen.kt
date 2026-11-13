package com.personal.passvault.ui.importcsv

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.personal.passvault.domain.IconSpec
import com.personal.passvault.domain.MatchKind
import com.personal.passvault.ui.components.EntryAvatar
import com.personal.passvault.ui.components.SectionCard
import com.personal.passvault.ui.theme.Motion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(onBack: () -> Unit, onDone: () -> Unit, vm: ImportViewModel = hiltViewModel()) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { vm.load(it) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Import passwords") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = vm.stage,
            transitionSpec = { fadeIn(Motion.smooth()) togetherWith fadeOut(Motion.fast()) },
            label = "importStage",
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) { stage ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                when (stage) {
                    ImportStage.PICK -> PickStage(vm.error) {
                        vm.holdForPicker()
                        picker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream", "*/*"))
                    }
                    ImportStage.LOADING, ImportStage.IMPORTING -> Column(
                        Modifier.fillMaxWidth().padding(top = 120.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(if (stage == ImportStage.LOADING) "Reading the file…" else "Encrypting and updating ${vm.workCount} logins…")
                    }
                    ImportStage.PREVIEW -> PreviewStage(vm)
                    ImportStage.DONE -> DoneStage(vm, onDone)
                }
            }
        }
    }
}

@Composable
private fun PickStage(error: String?, onPick: () -> Unit) {
    SectionCard(title = "1 · Export from your old password manager") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            How("Google Password Manager / Chrome", "passwords.google.com → ⚙ Settings → Export passwords. Or Chrome → Settings → Passwords → ⋮ → Export.")
            How("Bitwarden", "Vault → Tools → Export vault → .csv")
            How("Anything else", "Any CSV with columns like name, url, username, password, notes.")
        }
    }
    SectionCard(title = "2 · Pick the file") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Nothing is imported until you confirm. App logins from Google (android://…) get their real app icon.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Button(onClick = onPick, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Icon(Icons.Rounded.FileOpen, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Choose CSV file")
            }
        }
    }
}

@Composable
private fun How(title: String, text: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PreviewStage(vm: ImportViewModel) {
    val result = vm.result ?: return
    SectionCard(title = "Found in ${result.source} export") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CountLine(vm.newCount, "new — will be added", MaterialTheme.colorScheme.primary)
            CountLine(vm.changedCount, "password changed — will be updated", MaterialTheme.colorScheme.tertiary)
            CountLine(vm.sameCount, "already up to date", MaterialTheme.colorScheme.onSurfaceVariant)
            if (result.skippedRows > 0) {
                Text("${result.skippedRows} rows skipped (empty, or not logins)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (vm.changedCount > 0) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Update changed passwords", style = MaterialTheme.typography.bodyLarge)
                        Text("The old ones are kept in each entry's history", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = vm.updateChanged, onCheckedChange = { vm.updateChanged = it })
                }
            }
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(4.dp))
            Button(onClick = vm::import, enabled = vm.workCount > 0, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text(
                    when {
                        vm.workCount == 0 -> "Everything is already in sync"
                        vm.updateChanged && vm.changedCount > 0 && vm.newCount > 0 -> "Add ${vm.newCount} · update ${vm.changedCount}"
                        vm.updateChanged && vm.changedCount > 0 -> "Update ${vm.changedCount} passwords"
                        else -> "Add ${vm.newCount} logins"
                    }
                )
            }
        }
    }
    // Show what will actually change first
    val ordered = result.logins.indices
        .sortedBy { i -> when (vm.matches[i].kind) { MatchKind.CHANGED -> 0; MatchKind.NEW -> 1; MatchKind.SAME -> 2 } }
    SectionCard(title = "Preview") {
        ordered.take(50).forEach { i ->
            val l = result.logins[i]
            val kind = vm.matches[i].kind
            val inactive = kind == MatchKind.SAME || (kind == MatchKind.CHANGED && !vm.updateChanged)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).graphicsLayer { alpha = if (inactive) 0.4f else 1f },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EntryAvatar(IconSpec(l.title, listOfNotNull(l.packageName), listOfNotNull(l.domain)), 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(l.title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        listOf(l.username.ifBlank { l.email }, if (l.totp != null) "2FA" else "")
                            .filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "—" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    when (kind) { MatchKind.NEW -> "New"; MatchKind.CHANGED -> "Changed"; MatchKind.SAME -> "Same" },
                    style = MaterialTheme.typography.labelMedium,
                    color = when (kind) {
                        MatchKind.NEW -> MaterialTheme.colorScheme.primary
                        MatchKind.CHANGED -> MaterialTheme.colorScheme.tertiary
                        MatchKind.SAME -> MaterialTheme.colorScheme.outline
                    },
                )
            }
        }
        if (result.logins.size > 50) {
            Text("…and ${result.logins.size - 50} more", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CountLine(count: Int, label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$count", style = MaterialTheme.typography.titleLarge, color = color, modifier = Modifier.width(56.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DoneStage(vm: ImportViewModel, onDone: () -> Unit) {
    val scale = remember { Animatable(0.5f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, Motion.bouncy()) }
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Rounded.CheckCircle, null,
            Modifier.size(72.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value },
            tint = MaterialTheme.colorScheme.secondary,
        )
        Text("Synced with Google", style = MaterialTheme.typography.headlineSmall)
        Text(
            "${vm.added} added · ${vm.updated} updated (old passwords kept in history).\n" +
                "Encrypted in your vault, and a backup is on its way to Telegram.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.large)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(10.dp))
                Text("Delete the CSV file now", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Text(
                "It contains every password in plain text. Also delete it from Downloads/Drive and empty the trash.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            when (vm.fileDeleted) {
                true -> Text("✓ Deleted from this phone", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelLarge)
                false -> Text("Couldn't delete it automatically — please delete it in your Files app.", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelLarge)
                null -> OutlinedButton(onClick = vm::deleteFile, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.DeleteForever, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete the CSV file", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Go to vault") }
    }
}
