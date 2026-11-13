package com.personal.passvault.ui.kit

import android.print.PrintAttributes
import android.print.PrintManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.personal.passvault.ui.components.ConfirmDialog
import com.personal.passvault.ui.components.LocalSecretGate
import com.personal.passvault.ui.components.SectionCard
import com.personal.passvault.ui.components.findFragmentActivity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyKitScreen(onBack: () -> Unit, vm: EmergencyKitViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val gate = LocalSecretGate.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmSave by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { vm.saveTo(it) }

    LaunchedEffect(vm.message) { vm.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }

    fun print() = gate {
        scope.launch {
            val activity = context.findFragmentActivity() ?: return@launch
            val bytes = vm.buildPdf()
            vm.holdForSystemUi()
            activity.getSystemService(PrintManager::class.java).print(
                "PassVault Emergency Kit",
                PdfPrintAdapter(bytes, "passvault-emergency-kit.pdf"),
                PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build(),
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Emergency kit") },
                navigationIcon = { IconButton(onClick = { vm.releaseHold(); onBack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SectionCard(title = "What it is") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "One printed sheet that gets you back in if you lose your phone or forget everything:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("• Step-by-step restore from @${vm.botUsername ?: "your bot"}", style = MaterialTheme.typography.bodyMedium)
                    Text("• A box to write your master password by hand — it is never printed", style = MaterialTheme.typography.bodyMedium)
                    Text("• The recovery codes (and 2FA keys) you pick below", style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Warning, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Keep it at home with your important papers. Print a new one when your codes change.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (!vm.loading) {
                SectionCard(title = "Recovery codes to include") {
                    if (vm.codeOptions.isEmpty()) {
                        Text("No recovery codes saved yet.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    vm.codeOptions.forEach { o -> OptionRow(o.title, o.subtitle, o.selected) { vm.toggleCodes(o.id) } }
                }
                if (vm.totpOptions.isNotEmpty()) {
                    SectionCard(title = "2FA setup keys (optional)") {
                        Text(
                            "Only tick accounts you'd be locked out of without codes, like your main email.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        vm.totpOptions.forEach { o -> OptionRow(o.title, o.subtitle, o.selected) { vm.toggleTotp(o.id) } }
                    }
                }
            }

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { print() }, enabled = !vm.loading, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Rounded.Print, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Print emergency kit")
                }
                OutlinedButton(onClick = { confirmSave = true }, enabled = !vm.loading, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Save, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save as PDF instead")
                }
            }
        }
    }

    if (confirmSave) {
        ConfirmDialog(
            title = "Save an unencrypted PDF?",
            text = "The file will contain your selected recovery codes in plain text. Print it, then delete the file " +
                "(and empty the trash). Printing directly is safer.",
            confirmLabel = "Save PDF",
            onConfirm = {
                gate {
                    vm.holdForSystemUi()
                    saveLauncher.launch("passvault-emergency-kit-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.pdf")
                }
            },
            onDismiss = { confirmSave = false },
        )
    }
}

@Composable
private fun OptionRow(title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
