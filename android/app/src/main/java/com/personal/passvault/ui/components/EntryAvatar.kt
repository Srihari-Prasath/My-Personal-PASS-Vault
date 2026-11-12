package com.personal.passvault.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.passvault.data.icons.LoadedIcon
import com.personal.passvault.domain.IconSpec
import kotlin.math.abs

/** Gradient pairs for letter avatars — colour is picked from the title, so it never changes. */
private val AvatarGradients = listOf(
    Color(0xFFFF8A65) to Color(0xFFE5484D),
    Color(0xFFFFC857) to Color(0xFFF08C00),
    Color(0xFF8BE28B) to Color(0xFF2F9E44),
    Color(0xFF6EE7D8) to Color(0xFF0E9F8E),
    Color(0xFF74C0FC) to Color(0xFF1C7ED6),
    Color(0xFFB197FC) to Color(0xFF6741D9),
    Color(0xFFF783AC) to Color(0xFFC2255C),
)

/**
 * The entry's icon: real app icon -> website icon on a tile -> gradient letter avatar.
 * Cross-fades from the letter to the real icon once loaded (like Telegram's avatar loading).
 */
@Composable
fun EntryAvatar(spec: IconSpec, size: Dp, modifier: Modifier = Modifier) {
    val loader = LocalIconLoader.current
    val hasTargets = spec.packages.isNotEmpty() || spec.domains.isNotEmpty()
    val initial = remember(spec.cacheKey) { if (hasTargets) loader?.cached(spec) else null }
    val icon by produceState(initialValue = initial, spec.cacheKey) {
        if (value == null && hasTargets && loader != null) value = loader.load(spec)
    }
    Box(modifier.size(size)) {
        Crossfade(targetState = icon, animationSpec = tween(220), label = "avatar") { loaded ->
            if (loaded != null) IconImage(loaded, size) else LetterAvatar(spec.title, size)
        }
    }
}

@Composable
fun IconImage(icon: LoadedIcon, size: Dp, modifier: Modifier = Modifier) {
    if (icon.isApp) {
        // System launcher icon: already has the device's icon shape
        Image(
            bitmap = icon.bitmap, contentDescription = null,
            modifier = modifier.size(size), filterQuality = FilterQuality.High,
        )
    } else {
        Box(
            modifier
                .size(size)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = icon.bitmap, contentDescription = null,
                contentScale = ContentScale.Fit, filterQuality = FilterQuality.High,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(size * 0.18f),
            )
        }
    }
}

@Composable
fun LetterAvatar(title: String, size: Dp, modifier: Modifier = Modifier) {
    val (top, bottom) = remember(title) { AvatarGradients[abs(title.lowercase().hashCode()) % AvatarGradients.size] }
    val initials = remember(title) { initialsOf(title) }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(top, bottom))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.38f).sp,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

private fun initialsOf(title: String): String {
    val words = title.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(1).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

/** Small icon used for linked-app/website chips. */
@Composable
fun TargetIcon(packageName: String?, domain: String?, label: String, size: Dp = 24.dp) {
    EntryAvatar(
        spec = IconSpec(
            title = label,
            packages = listOfNotNull(packageName),
            domains = listOfNotNull(domain),
        ),
        size = size,
    )
}
