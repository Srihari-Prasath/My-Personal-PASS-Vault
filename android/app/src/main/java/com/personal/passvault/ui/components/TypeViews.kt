package com.personal.passvault.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.passvault.data.qr.Qr
import com.personal.passvault.domain.CardData
import com.personal.passvault.domain.CardNetwork
import com.personal.passvault.domain.Totp
import com.personal.passvault.domain.TotpConfig
import com.personal.passvault.domain.formatCardNumber
import com.personal.passvault.ui.theme.Motion

/**
 * Live 2FA code: rolls to the next code with a slide, countdown ring drains smoothly every frame,
 * turns red in the last 5 seconds. Tap to copy.
 */
@Composable
fun TotpCodeView(config: TotpConfig, onCopy: (String) -> Unit, modifier: Modifier = Modifier, large: Boolean = true) {
    val haptic = LocalHapticFeedback.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(config) {
        while (true) withFrameMillis { now = System.currentTimeMillis() }
    }
    // Recompose the text only when the 30-second window changes, not every frame
    val window by remember(config) { derivedStateOf { now / 1000 / config.period } }
    val code = remember(config, window) { runCatching { Totp.code(config, window * 1000 * config.period) }.getOrDefault("------") }
    val secondsLeft by remember(config) { derivedStateOf { Totp.secondsLeft(config, now) } }
    val urgent = secondsLeft <= 5
    val color by animateColorAsState(
        if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, label = "totpColor",
    )

    Row(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onCopy(code)
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = code,
            transitionSpec = {
                (slideInVertically(Motion.offset) { it } + fadeIn()) togetherWith (slideOutVertically(Motion.offset) { -it } + fadeOut())
            },
            label = "totpCode",
            modifier = Modifier.weight(1f),
        ) { c ->
            Text(
                Totp.pretty(c),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = if (large) 34.sp else 22.sp,
                letterSpacing = 2.sp,
                color = color,
            )
        }
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = {
                    val ms = now % (config.period * 1000L)
                    1f - ms.toFloat() / (config.period * 1000f)
                },
                modifier = Modifier.size(if (large) 36.dp else 28.dp),
                strokeWidth = 3.dp,
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Text(
                "$secondsLeft",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun networkGradient(network: CardNetwork): List<Color> = when (network) {
    CardNetwork.VISA -> listOf(Color(0xFF1A1F71), Color(0xFF3B4CCA))
    CardNetwork.MASTERCARD -> listOf(Color(0xFF2B2B2B), Color(0xFFEB6A1E))
    CardNetwork.AMEX -> listOf(Color(0xFF0B6E8E), Color(0xFF2FA4C7))
    CardNetwork.RUPAY -> listOf(Color(0xFF0E3B66), Color(0xFF1F8A4C))
    CardNetwork.DISCOVER -> listOf(Color(0xFF3A3A3A), Color(0xFFF08C00))
    else -> listOf(Color(0xFF4F46E5), Color(0xFF8B5CF6))
}

/**
 * A credit-card-shaped view. Tap flips it (3D rotation) to the back showing CVV — like a real card.
 * Number stays masked until [revealed].
 */
@Composable
fun CardVisual(card: CardData, revealed: Boolean, modifier: Modifier = Modifier) {
    var flipped by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        if (flipped) 180f else 0f,
        spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow),
        label = "flip",
    )
    val gradient = networkGradient(card.network)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1.586f) // ISO card ratio
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 14f * density
            }
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(gradient))
            .clickable { flipped = !flipped },
    ) {
        if (rotation <= 90f) {
            Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(card.bank.ifBlank { "Card" }, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(card.network.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Box(Modifier.size(width = 44.dp, height = 32.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFE6C66E)))
                Text(
                    formatCardNumber(card.number, masked = !revealed).ifBlank { "•••• •••• •••• ••••" },
                    color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 20.sp, letterSpacing = 1.5.sp,
                )
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("CARD HOLDER", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                        Text(card.holder.ifBlank { "—" }.uppercase(), color = Color.White, fontSize = 14.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("EXPIRES", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                        Text(card.expiry.ifBlank { "—" }, color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        } else {
            // Back side (counter-rotated so text isn't mirrored)
            Column(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
                Spacer(Modifier.height(28.dp))
                Box(Modifier.fillMaxWidth().height(44.dp).background(Color.Black.copy(alpha = 0.75f)))
                Spacer(Modifier.height(18.dp))
                Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.85f)))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (revealed) card.cvv.ifBlank { "—" } else "•••",
                        color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "Tap to flip back",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(22.dp),
                )
            }
        }
    }
}

/** Shows a QR code (e.g. to let a guest join your Wi-Fi). */
@Composable
fun QrDialog(title: String, content: String, caption: String, onDismiss: () -> Unit) {
    val bitmap = remember(content) { Qr.encode(content) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Image(
                    bitmap = bitmap, contentDescription = "QR code",
                    filterQuality = FilterQuality.None, // keep modules crisp
                    modifier = Modifier.size(240.dp).clip(RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.height(12.dp))
                Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
