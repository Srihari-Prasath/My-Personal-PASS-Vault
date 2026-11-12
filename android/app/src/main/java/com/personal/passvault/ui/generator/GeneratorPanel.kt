package com.personal.passvault.ui.generator

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.passvault.domain.PasswordGenerator
import com.personal.passvault.ui.components.StrengthMeter
import com.personal.passvault.ui.components.colorizeSecret
import com.personal.passvault.ui.theme.Motion
import com.personal.passvault.ui.theme.SecretTextStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorPanel(
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
    onUse: ((String) -> Unit)? = null,
) {
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 = random, 1 = passphrase
    var length by rememberSaveable { mutableFloatStateOf(20f) }
    var upper by rememberSaveable { mutableStateOf(true) }
    var lower by rememberSaveable { mutableStateOf(true) }
    var digits by rememberSaveable { mutableStateOf(true) }
    var symbols by rememberSaveable { mutableStateOf(true) }
    var avoid by rememberSaveable { mutableStateOf(true) }
    var words by rememberSaveable { mutableFloatStateOf(6f) }
    var separator by rememberSaveable { mutableStateOf("-") }
    var capitalize by rememberSaveable { mutableStateOf(true) }
    var addNumber by rememberSaveable { mutableStateOf(true) }
    var seed by remember { mutableIntStateOf(0) }

    val randomOpts = PasswordGenerator.RandomOptions(length.toInt(), upper, lower, digits, symbols, avoid)
    val phraseOpts = PasswordGenerator.PassphraseOptions(words.toInt(), separator, capitalize, addNumber)
    val value = remember(mode, randomOpts, phraseOpts, seed) {
        if (mode == 0) PasswordGenerator.random(randomOpts) else PasswordGenerator.passphrase(phraseOpts)
    }
    val bits = if (mode == 0) PasswordGenerator.randomEntropyBits(randomOpts) else PasswordGenerator.passphraseEntropyBits(phraseOpts)

    val scope = rememberCoroutineScope()
    val spin = remember { Animatable(0f) }
    val scheme = MaterialTheme.colorScheme

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Random", "Passphrase").forEachIndexed { i, label ->
                SegmentedButton(
                    selected = mode == i,
                    onClick = { mode = i },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                    label = { Text(label) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        Surface(
            shape = MaterialTheme.shapes.large,
            color = scheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
        ) {
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    (slideInVertically(Motion.offset) { it / 2 } + fadeIn()) togetherWith
                        (slideOutVertically(Motion.offset) { -it / 2 } + fadeOut())
                },
                contentAlignment = Alignment.Center,
                label = "generated",
                modifier = Modifier.padding(20.dp),
            ) { pw ->
                Text(
                    colorizeSecret(pw, scheme.onSurface, scheme.primary, scheme.tertiary),
                    style = SecretTextStyle.copy(fontSize = 20.sp, lineHeight = 28.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        StrengthMeter(bits, Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(
                onClick = {
                    seed++
                    scope.launch { spin.animateTo(spin.value + 360f, Motion.smooth()) }
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.Autorenew, null, Modifier.rotate(spin.value))
                Spacer(Modifier.width(6.dp))
                Text("New")
            }
            FilledTonalButton(onClick = { onCopy(value) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.ContentCopy, null)
                Spacer(Modifier.width(6.dp))
                Text("Copy")
            }
        }
        if (onUse != null) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onUse(value) }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Use this password") }
        }
        Spacer(Modifier.height(18.dp))

        if (mode == 0) {
            LabeledSlider("Length", length, 8f..64f, 55) { length = it }
            OptionSwitch("Uppercase (A–Z)", upper) { upper = it }
            OptionSwitch("Lowercase (a–z)", lower) { lower = it }
            OptionSwitch("Numbers (0–9)", digits) { digits = it }
            OptionSwitch("Symbols (!@#…)", symbols) { symbols = it }
            OptionSwitch("Avoid look-alikes (0 O 1 l I)", avoid) { avoid = it }
        } else {
            LabeledSlider("Words", words, 3f..10f, 6) { words = it }
            Text("Separator", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("-" to "Dash", "." to "Dot", "_" to "Underscore", " " to "Space").forEach { (sep, name) ->
                    FilterChip(selected = separator == sep, onClick = { separator = sep }, label = { Text(name) })
                }
            }
            OptionSwitch("Capitalise words", capitalize) { capitalize = it }
            OptionSwitch("Add a number", addNumber) { addNumber = it }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            AnimatedContent(value.toInt(), label = "sliderValue") { v ->
                Text("$v", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        Slider(value = value, onValueChange = { onChange(it.toInt().toFloat()) }, valueRange = range, steps = steps)
    }
}

@Composable
private fun OptionSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
