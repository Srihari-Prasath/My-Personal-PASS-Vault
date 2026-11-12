package com.personal.passvault.ui.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.personal.passvault.domain.PasswordStrength
import com.personal.passvault.ui.components.Biometric
import com.personal.passvault.ui.components.StrengthMeter
import com.personal.passvault.ui.components.findFragmentActivity
import com.personal.passvault.ui.theme.Motion

@Composable
fun SetupScreen(onFinished: () -> Unit, onRestore: () -> Unit, vm: SetupViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val biometricAvailable = remember { Biometric.isAvailable(context) }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
    ) {
        AnimatedContent(
            targetState = vm.step,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally(Motion.offset) { if (forward) it else -it } + fadeIn()) togetherWith
                    (slideOutHorizontally(Motion.offset) { if (forward) -it / 3 else it / 3 } + fadeOut())
            },
            label = "setupStep",
        ) { step ->
            when (step) {
                SetupStep.WELCOME -> WelcomeStep(onNext = { vm.goTo(SetupStep.PASSWORD) }, onRestore = onRestore)
                SetupStep.PASSWORD -> PasswordStep(
                    creating = vm.creating,
                    error = vm.error,
                    onBack = { vm.goTo(SetupStep.WELCOME) },
                    onCreate = { pw -> vm.createVault(pw, biometricAvailable) },
                )
                SetupStep.BIOMETRIC -> BiometricStep(
                    onEnable = {
                        val activity = context.findFragmentActivity() ?: return@BiometricStep
                        val cipher = vm.biometricCipher() ?: return@BiometricStep vm.goTo(SetupStep.DONE)
                        Biometric.prompt(
                            activity, cipher,
                            title = "Turn on fingerprint unlock",
                            subtitle = "Touch the sensor to confirm",
                            negativeText = "Cancel",
                            onSuccess = vm::enableBiometric,
                            onError = { },
                        )
                    },
                    onSkip = { vm.goTo(SetupStep.DONE) },
                )
                SetupStep.DONE -> DoneStep(onFinished)
            }
        }
    }
}

@Composable
private fun StepColumn(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun HeroIcon(icon: ImageVector) {
    val scale = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, Motion.bouncy()) }
    Box(
        Modifier
            .size(112.dp)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit, onRestore: () -> Unit) = StepColumn {
    Spacer(Modifier.height(48.dp))
    HeroIcon(Icons.Rounded.Shield)
    Spacer(Modifier.height(28.dp))
    Text("PassVault", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        "Every password and recovery code in one place —\nencrypted, and only on your phone.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(36.dp))
    Feature(Icons.Rounded.Lock, "Military-grade encryption", "AES-256 + Argon2id. Nothing is stored in plain text.")
    Feature(Icons.Rounded.CloudOff, "Stays on this phone", "No cloud account. Encrypted backups go to your own Telegram bot.")
    Feature(Icons.Rounded.Key, "One password to remember", "Your master password unlocks everything else.")
    Spacer(Modifier.height(36.dp))
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Get started") }
    TextButton(onClick = onRestore, modifier = Modifier.padding(top = 4.dp)) { Text("I have a backup — restore it") }
}

@Composable
private fun Feature(icon: ImageVector, title: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(40.dp).background(MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PasswordStep(
    creating: Boolean,
    error: String?,
    onBack: () -> Unit,
    onCreate: (CharArray) -> Unit,
) = StepColumn {
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    var writtenDown by rememberSaveable { mutableStateOf(false) }
    val bits = remember(password) { PasswordStrength.bits(password) }
    val level = PasswordStrength.levelForBits(bits)

    val longEnough = password.length >= 12
    val matches = password.isNotEmpty() && password == confirm
    val strongEnough = level >= PasswordStrength.Level.FAIR
    val canCreate = longEnough && matches && strongEnough && writtenDown && !creating

    Spacer(Modifier.height(16.dp))
    HeroIcon(Icons.Rounded.Key)
    Spacer(Modifier.height(20.dp))
    Text("Create your master password", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))

    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(14.dp)) {
            Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(10.dp))
            Text(
                "This is the only password you'll need to remember. If you forget it, nobody can recover your vault — not even you. Write it on paper and keep it at home.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
    Spacer(Modifier.height(20.dp))

    val transformation = if (visible) VisualTransformation.None else PasswordVisualTransformation()
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Master password") },
        singleLine = true,
        visualTransformation = transformation,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Next),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show password")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    StrengthMeter(bits, Modifier.fillMaxWidth())
    Spacer(Modifier.height(14.dp))
    OutlinedTextField(
        value = confirm,
        onValueChange = { confirm = it },
        label = { Text("Type it again") },
        singleLine = true,
        visualTransformation = transformation,
        isError = confirm.isNotEmpty() && !matches,
        supportingText = {
            AnimatedVisibility(confirm.isNotEmpty() && !matches) { Text("Passwords don't match") }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(6.dp))
    Requirement("At least 12 characters", longEnough)
    Requirement("Strength at least \"Fair\" (tip: 4–5 random words work great)", strongEnough)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = writtenDown, onCheckedChange = { writtenDown = it })
        Text("I've written it down and kept it somewhere safe", style = MaterialTheme.typography.bodyMedium)
    }
    AnimatedVisibility(error != null) {
        Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
    }
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = { onCreate(password.toCharArray()) },
        enabled = canCreate,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        AnimatedContent(creating, label = "creating") { busy ->
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(10.dp))
                    Text("Encrypting your vault…")
                }
            } else Text("Create vault")
        }
    }
    TextButton(onClick = onBack, enabled = !creating) { Text("Back") }
}

@Composable
private fun Requirement(text: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(ok, label = "req") { done ->
            Icon(
                if (done) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                null,
                Modifier.size(18.dp),
                tint = if (done) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BiometricStep(onEnable: () -> Unit, onSkip: () -> Unit) = StepColumn {
    Spacer(Modifier.height(64.dp))
    HeroIcon(Icons.Rounded.Fingerprint)
    Spacer(Modifier.height(28.dp))
    Text("Unlock with fingerprint?", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(10.dp))
    Text(
        "Open your vault with a touch. Your master password still works, and we'll ask for it every 7 days and after a restart so you don't forget it.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(40.dp))
    Button(onClick = onEnable, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Turn on fingerprint") }
    TextButton(onClick = onSkip) { Text("Not now") }
}

@Composable
private fun DoneStep(onFinished: () -> Unit) = StepColumn {
    Spacer(Modifier.height(96.dp))
    HeroIcon(Icons.Rounded.CheckCircle)
    Spacer(Modifier.height(28.dp))
    Text("Your vault is ready", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(10.dp))
    Text(
        "Add your first password or recovery codes.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(40.dp))
    Button(onClick = onFinished, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Open vault") }
}
