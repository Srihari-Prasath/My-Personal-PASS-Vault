package com.personal.passvault.ui.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.personal.passvault.domain.CardNetwork
import com.personal.passvault.domain.PasswordStrength
import com.personal.passvault.ui.components.CardVisual
import com.personal.passvault.ui.components.LocalClipboardHelper
import com.personal.passvault.ui.components.LocalSecretGate
import com.personal.passvault.ui.components.toggle
import com.personal.passvault.ui.components.SectionCard
import com.personal.passvault.ui.components.StrengthMeter
import com.personal.passvault.ui.components.TotpCodeView
import com.personal.passvault.ui.theme.Motion
import com.personal.passvault.ui.theme.SecretTextStyle

// =================================================================================================
// 2FA
// =================================================================================================

@Composable
fun TotpSection(vm: EntryEditViewModel, onScan: () -> Unit, optional: Boolean) {
    val f = vm.form
    val clipboard = LocalClipboardHelper.current
    SectionCard(title = if (optional) "2FA codes (optional)" else "2FA codes") {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.QrCodeScanner, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (f.totp == null) "Scan the setup QR code" else "Scan again")
            }
            OutlinedTextField(
                value = f.totpInput,
                onValueChange = vm::setTotpInput,
                label = { Text("…or paste the setup key") },
                singleLine = true,
                isError = f.totpInputInvalid,
                supportingText = {
                    Text(
                        if (f.totpInputInvalid) "That isn't a valid key (letters A–Z and digits 2–7)"
                        else "Shown as \"Can't scan? Enter this key\" on the 2FA setup page",
                    )
                },
                textStyle = SecretTextStyle,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                visualTransformation = PasswordVisualTransformation(),
                trailingIcon = if (f.totp != null) ({
                    IconButton(onClick = vm::removeTotp) { Icon(Icons.Rounded.Close, "Remove 2FA") }
                }) else null,
                modifier = Modifier.fillMaxWidth(),
            )
            val totp = f.totp
            AnimatedVisibility(totp != null, enter = expandVertically(Motion.size) + fadeIn(), exit = shrinkVertically(Motion.size) + fadeOut()) {
                if (totp != null) {
                    Column {
                        Text(
                            listOf(totp.issuer, totp.account).filter { it.isNotBlank() }.joinToString(" · ")
                                .ifBlank { "Code preview" } + " — type this code on the website to finish setup",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        TotpCodeView(totp, onCopy = { clipboard?.copy("2FA code", it) }, large = false)
                    }
                }
            }
            if (optional) {
                InfoLine(
                    "Keeping 2FA in the same vault as the password is convenient but weaker. " +
                        "For email and banking, consider a separate authenticator app.",
                )
            }
        }
    }
}

// =================================================================================================
// Card
// =================================================================================================

/** Shows "4111 1111 1111 1111" while the field holds only digits. */
private object CardNumberTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val out = StringBuilder()
        val map = IntArray(digits.length + 1)
        digits.forEachIndexed { i, c ->
            if (i > 0 && i % 4 == 0) out.append(' ')
            map[i] = out.length
            out.append(c)
        }
        map[digits.length] = out.length
        val reverse = IntArray(out.length + 1)
        var d = 0
        for (o in 0..out.length) {
            while (d < digits.length && map[d] < o) d++
            reverse[o] = d
        }
        return TransformedText(AnnotatedString(out.toString()), object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = map[offset.coerceIn(0, digits.length)]
            override fun transformedToOriginal(offset: Int) = reverse[offset.coerceIn(0, out.length)]
        })
    }
}

@Composable
fun CardSection(vm: EntryEditViewModel) {
    val card = vm.form.card
    var reveal by remember { mutableStateOf(true) }
    val digits = card.number.filter { it.isDigit() }
    val luhnBad = digits.length >= 12 && !CardNetwork.luhnValid(digits)

    SectionCard(title = "Card") {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CardVisual(card, revealed = reveal, modifier = Modifier.padding(4.dp))
            OutlinedTextField(
                value = digits,
                onValueChange = { v -> vm.updateCard { it.copy(number = v.filter { c -> c.isDigit() }.take(19)) } },
                label = { Text("Card number") },
                singleLine = true,
                isError = luhnBad,
                supportingText = {
                    Text(
                        when {
                            luhnBad -> "This number looks mistyped (checksum doesn't match)"
                            digits.isNotEmpty() -> card.network.label
                            else -> ""
                        },
                    )
                },
                textStyle = SecretTextStyle,
                visualTransformation = CardNumberTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, autoCorrectEnabled = false),
                trailingIcon = {
                    IconButton(onClick = { reveal = !reveal }) {
                        Icon(if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show on card")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = card.holder,
                onValueChange = { v -> vm.updateCard { it.copy(holder = v) } },
                label = { Text("Name on card") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = card.expiry,
                    onValueChange = { v -> vm.updateCard { it.copy(expiry = formatExpiry(v)) } },
                    label = { Text("Expiry (MM/YY)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = card.cvv,
                    onValueChange = { v -> vm.updateCard { it.copy(cvv = v.filter { c -> c.isDigit() }.take(4)) } },
                    label = { Text("CVV") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = card.pin,
                    onValueChange = { v -> vm.updateCard { it.copy(pin = v.filter { c -> c.isDigit() }.take(8)) } },
                    label = { Text("PIN (optional)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = card.bank,
                    onValueChange = { v -> vm.updateCard { it.copy(bank = v) } },
                    label = { Text("Bank") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** "1" -> "1", "12" -> "12/", "1226" -> "12/26" */
private fun formatExpiry(input: String): String {
    val d = input.filter { it.isDigit() }.take(4)
    return when {
        d.length <= 2 -> d
        else -> d.substring(0, 2) + "/" + d.substring(2)
    }
}

// =================================================================================================
// Wi-Fi
// =================================================================================================

@Composable
fun WifiSection(vm: EntryEditViewModel, onScan: () -> Unit, onGenerate: () -> Unit) {
    val f = vm.form
    var visible by remember { mutableStateOf(false) }
    val gate = LocalSecretGate.current
    SectionCard(title = "Wi-Fi network") {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.QrCodeScanner, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Scan the router's Wi-Fi QR code")
            }
            OutlinedTextField(
                value = f.wifiSsid,
                onValueChange = { v -> vm.update { it.copy(wifiSsid = v, title = if (it.title == it.wifiSsid) v else it.title) } },
                label = { Text("Network name (SSID)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = f.password,
                onValueChange = { v -> vm.update { it.copy(password = v) } },
                label = { Text("Wi-Fi password") },
                singleLine = true,
                enabled = f.wifiSecurity != "nopass",
                textStyle = SecretTextStyle,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
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
            if (f.password.isNotEmpty()) StrengthMeter(PasswordStrength.bits(f.password), Modifier.fillMaxWidth().padding(horizontal = 4.dp))
            Text("Security", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("WPA" to "WPA/WPA2/WPA3", "WEP" to "WEP", "nopass" to "Open").forEach { (value, label) ->
                    FilterChip(
                        selected = f.wifiSecurity == value,
                        onClick = { vm.update { it.copy(wifiSecurity = value) } },
                        label = { Text(label) },
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Hidden network", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = f.wifiHidden, onCheckedChange = { v -> vm.update { it.copy(wifiHidden = v) } })
            }
        }
    }
}

@Composable
private fun InfoLine(text: String) {
    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Rounded.Info, null, Modifier.size(16.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
