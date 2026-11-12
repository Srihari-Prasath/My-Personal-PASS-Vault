package com.personal.passvault.ui.unlock

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.personal.passvault.ui.components.Biometric
import com.personal.passvault.ui.components.findFragmentActivity
import com.personal.passvault.ui.components.rememberShakeController
import com.personal.passvault.ui.components.shake
import com.personal.passvault.ui.theme.Motion
import kotlinx.coroutines.delay

@Composable
fun UnlockScreen(vm: UnlockViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val ui = vm.ui
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val shake = rememberShakeController()
    val focus = remember { FocusRequester() }
    val deviceHasBiometric = remember { Biometric.isAvailable(context) }
    val canUseBiometric = ui.biometricEnabled && deviceHasBiometric

    fun startBiometric() {
        val activity = context.findFragmentActivity() ?: return
        val cipher = vm.biometricCipher() ?: return
        Biometric.prompt(
            activity, cipher,
            title = "Unlock PassVault",
            subtitle = "Touch the fingerprint sensor",
            negativeText = "Use master password",
            onSuccess = vm::unlockWithBiometric,
            onError = { msg -> if (msg == null) runCatching { focus.requestFocus() } },
        )
    }

    // Wrong password -> shake + haptic, clear field
    LaunchedEffect(Unit) {
        vm.shake.collect {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            password = ""
            shake.shake()
        }
    }
    // Offer fingerprint automatically once per time the app comes to the front; otherwise focus the field.
    var autoPrompted by remember { mutableStateOf(false) }
    fun maybeAutoPrompt() {
        val state = vm.ui
        if (autoPrompted || state.loading || state.success) return
        if (state.biometricEnabled && deviceHasBiometric) {
            autoPrompted = true
            startBiometric()
        } else {
            runCatching { focus.requestFocus() }
        }
    }
    LaunchedEffect(ui.loading) { if (!ui.loading) maybeAutoPrompt() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { maybeAutoPrompt() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { autoPrompted = false }

    // Lock-out countdown
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ui.lockedUntil) {
        while (System.currentTimeMillis() < ui.lockedUntil) {
            now = System.currentTimeMillis()
            delay(250)
        }
        now = System.currentTimeMillis()
    }
    val lockedSeconds = ((ui.lockedUntil - now) / 1000).toInt() + 1
    val lockedOut = ui.lockedUntil > now

    val circleColor by animateColorAsState(
        if (ui.success) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
        Motion.smooth(), label = "lockBg",
    )
    val iconScale by animateFloatAsState(if (ui.success) 1.12f else 1f, Motion.bouncy(), label = "lockScale")

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(104.dp)
                    .graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                    .background(circleColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = ui.success,
                    transitionSpec = { (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith (scaleOut(targetScale = 1.3f) + fadeOut()) },
                    label = "lockIcon",
                ) { open ->
                    Icon(
                        if (open) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = if (open) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("PassVault", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                ui.passwordRequiredReason ?: "Enter your master password",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; vm.clearError() },
                label = { Text("Master password") },
                singleLine = true,
                enabled = !lockedOut && !ui.success,
                isError = ui.error != null,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    if (password.isNotEmpty()) vm.unlockWithPassword(password.toCharArray())
                }),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show password")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .shake(shake)
                    .focusRequester(focus),
            )

            AnimatedVisibility(
                visible = ui.error != null || lockedOut,
                enter = expandVertically(Motion.size) + fadeIn(),
                exit = shrinkVertically(Motion.size) + fadeOut(),
            ) {
                Text(
                    if (lockedOut) "Too many wrong attempts. Try again in ${lockedSeconds}s" else ui.error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { vm.unlockWithPassword(password.toCharArray()) },
                enabled = password.isNotEmpty() && !ui.busy && !lockedOut && !ui.success,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                AnimatedContent(ui.busy, label = "unlockBusy") { busy ->
                    if (busy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(10.dp))
                            Text("Unlocking…")
                        }
                    } else Text("Unlock")
                }
            }
            if (canUseBiometric) {
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = { startBiometric() },
                    enabled = !ui.success,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Rounded.Fingerprint, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Use fingerprint")
                }
            }
            Spacer(Modifier.height(48.dp))
            Row(horizontalArrangement = Arrangement.Center) {
                Text(
                    "Encrypted on this phone · AES-256 · Argon2id",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
