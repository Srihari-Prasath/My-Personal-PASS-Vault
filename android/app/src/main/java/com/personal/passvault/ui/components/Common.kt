package com.personal.passvault.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.passvault.core.security.ClipState
import com.personal.passvault.domain.PasswordStrength
import com.personal.passvault.ui.theme.Motion

/** Rounded card section with an optional small caps title — the building block of every screen. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, bottom = 8.dp, top = 4.dp),
            )
        }
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            Column(Modifier.padding(vertical = 6.dp), content = content)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Shake on wrong password
// ---------------------------------------------------------------------------------------------

@Stable
class ShakeController {
    val offset = Animatable(0f)
    suspend fun shake() {
        for (x in listOf(-22f, 18f, -14f, 10f, -5f, 0f)) {
            offset.animateTo(x, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessHigh))
        }
    }
}

@Composable
fun rememberShakeController() = remember { ShakeController() }

fun Modifier.shake(controller: ShakeController) = graphicsLayer { translationX = controller.offset.value }

// ---------------------------------------------------------------------------------------------
// Animated strength meter
// ---------------------------------------------------------------------------------------------

@Composable
fun StrengthMeter(bits: Double, modifier: Modifier = Modifier, showCrackTime: Boolean = true) {
    val level = PasswordStrength.levelForBits(bits)
    val fraction by animateFloatAsState(PasswordStrength.fraction(bits), Motion.smooth(), label = "strength")
    val color by animateColorAsState(strengthColor(level), Motion.smooth(), label = "strengthColor")
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceAtLeast(0.03f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(level.label, style = MaterialTheme.typography.labelMedium, color = color)
            if (showCrackTime && bits > 0) {
                Text(
                    "Crack time: ${PasswordStrength.crackTime(bits)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

fun strengthColor(level: PasswordStrength.Level): Color = when (level) {
    PasswordStrength.Level.VERY_WEAK -> Color(0xFFE5484D)
    PasswordStrength.Level.WEAK -> Color(0xFFF08C00)
    PasswordStrength.Level.FAIR -> Color(0xFFE6B400)
    PasswordStrength.Level.STRONG -> Color(0xFF2F9E44)
    PasswordStrength.Level.VERY_STRONG -> Color(0xFF0E9F8E)
}

// ---------------------------------------------------------------------------------------------
// "Password copied · clears in 27s" pill with a live countdown ring
// ---------------------------------------------------------------------------------------------

@Composable
fun CopyPill(state: ClipState?, onClearNow: () -> Unit, modifier: Modifier = Modifier) {
    var last by remember { mutableStateOf(state) }
    if (state != null) last = state

    AnimatedVisibility(
        visible = state != null,
        enter = slideInVertically(Motion.offset) { it * 2 } + fadeIn() + scaleIn(initialScale = 0.9f),
        exit = slideOutVertically(Motion.offset) { it * 2 } + fadeOut(),
        modifier = modifier,
    ) {
        val s = last ?: return@AnimatedVisibility
        val remaining by produceState(1f, s) {
            while (true) {
                val elapsed = SystemClock.elapsedRealtime() - s.copiedAtElapsed
                value = (1f - elapsed.toFloat() / s.durationMs).coerceIn(0f, 1f)
                if (value <= 0f) break
                withFrameMillis { }
            }
        }
        val secondsLeft = (remaining * s.durationMs / 1000f).toInt() + 1
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 8.dp,
        ) {
            Row(
                Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { remaining },
                        modifier = Modifier.size(30.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.inversePrimary,
                        trackColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.15f),
                    )
                    Text(
                        "$secondsLeft",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text("${s.what} copied", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onClearNow) {
                    Text("Clear", color = MaterialTheme.colorScheme.inversePrimary)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
