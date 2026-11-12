package com.personal.passvault.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.personal.passvault.domain.CardData
import com.personal.passvault.domain.TotpConfig
import com.personal.passvault.domain.WifiData
import com.personal.passvault.domain.WifiQr
import com.personal.passvault.domain.formatCardNumber
import com.personal.passvault.ui.components.CardVisual
import com.personal.passvault.ui.components.ConfirmDialog
import com.personal.passvault.ui.components.LocalSecretGate
import com.personal.passvault.ui.components.toggle
import com.personal.passvault.ui.components.QrDialog
import com.personal.passvault.ui.components.RevealableSecret
import com.personal.passvault.ui.components.SectionCard
import com.personal.passvault.ui.components.TotpCodeView

@Composable
internal fun TotpDetailSection(totp: TotpConfig?, onCopy: (String) -> Unit) {
    SectionCard(title = "2FA code") {
        if (totp == null) {
            Text("No 2FA key saved. Tap Edit to scan the setup QR code.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@SectionCard
        }
        TotpCodeView(totp, onCopy = onCopy, modifier = Modifier.fillMaxWidth())
        val label = listOf(totp.issuer, totp.account).filter { it.isNotBlank() }.joinToString(" · ")
        Text(
            (if (label.isNotBlank()) "$label · " else "") + "tap the code to copy",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 10.dp),
        )
    }
}

@Composable
internal fun CardDetailSection(card: CardData?, onCopy: (what: String, value: String) -> Unit) {
    if (card == null) return
    var revealed by rememberSaveable { mutableStateOf(false) }
    val gate = LocalSecretGate.current
    val toggle: () -> Unit = { gate.toggle(revealed) { revealed = it } }
    Column {
        CardVisual(card, revealed = revealed, modifier = Modifier.padding(horizontal = 20.dp))
        Text(
            "Tap the card to flip it",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        SectionCard(title = "Card details") {
            SecretRow(Icons.Rounded.CreditCard, "Card number", formatCardNumber(card.number, masked = false), revealed,
                onToggle = toggle) { onCopy("Card number", card.number) }
            if (card.expiry.isNotBlank()) FieldRow(Icons.Rounded.CalendarMonth, "Expiry", card.expiry) { onCopy("Expiry", card.expiry) }
            if (card.cvv.isNotBlank()) SecretRow(Icons.Rounded.Security, "CVV", card.cvv, revealed, onToggle = toggle) { onCopy("CVV", card.cvv) }
            if (card.pin.isNotBlank()) SecretRow(Icons.Rounded.Dialpad, "PIN", card.pin, revealed, onToggle = toggle) { onCopy("PIN", card.pin) }
            if (card.holder.isNotBlank()) FieldRow(Icons.Rounded.Person, "Name on card", card.holder) { onCopy("Name", card.holder) }
            if (card.bank.isNotBlank()) FieldRow(Icons.Rounded.AccountBalance, "Bank", card.bank) { onCopy("Bank", card.bank) }
        }
    }
}

@Composable
internal fun WifiDetailSection(password: String, changedAt: Long, wifi: WifiData?, onCopy: (what: String, value: String) -> Unit) {
    val w = wifi ?: WifiData()
    val gate = LocalSecretGate.current
    var confirmQr by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    SectionCard(title = "Wi-Fi") {
        FieldRow(Icons.Rounded.Wifi, "Network name", w.ssid.ifBlank { "—" }) { onCopy("Network name", w.ssid) }
        if (w.security != "nopass") PasswordRow(password, changedAt) { onCopy("Wi-Fi password", password) }
        Text(
            when (w.security) { "nopass" -> "Open network"; "WEP" -> "WEP (old, insecure — change it on the router if you can)"; else -> "WPA/WPA2/WPA3" } +
                if (w.hidden) " · hidden" else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 54.dp, bottom = 6.dp),
        )
        if (w.ssid.isNotBlank()) {
            FilledTonalButton(
                onClick = { confirmQr = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Rounded.QrCode, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Share with a QR code")
            }
        }
    }
    if (confirmQr) {
        ConfirmDialog(
            title = "Show Wi-Fi QR code?",
            text = "Anyone who scans it joins \"${w.ssid}\" — it contains the password.",
            confirmLabel = "Show",
            onConfirm = { gate { showQr = true } },
            onDismiss = { confirmQr = false },
        )
    }
    if (showQr) {
        QrDialog(
            title = w.ssid,
            content = WifiQr.format(w.ssid, password, w.security, w.hidden),
            caption = "Open the camera on the other phone and point it here.",
            onDismiss = { showQr = false },
        )
    }
}

@Composable
private fun SecretRow(
    icon: ImageVector,
    label: String,
    value: String,
    revealed: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            RevealableSecret(value, revealed)
        }
        IconButton(onClick = onToggle) {
            Icon(if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show $label", Modifier.size(20.dp))
        }
        IconButton(onClick = onCopy) { Icon(Icons.Rounded.ContentCopy, "Copy $label", Modifier.size(20.dp)) }
    }
}
