package com.personal.passvault.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.personal.passvault.ui.theme.Motion
import com.personal.passvault.ui.theme.SecretTextStyle

/** Digits and symbols in different colours — much easier to read/type a password correctly. */
fun colorizeSecret(secret: String, letter: Color, digit: Color, symbol: Color): AnnotatedString =
    buildAnnotatedString {
        secret.forEach { c ->
            val color = when {
                c.isDigit() -> digit
                c.isLetter() -> letter
                else -> symbol
            }
            withStyle(SpanStyle(color = color)) { append(c) }
        }
    }

fun maskOf(secret: String): String = "•".repeat(secret.length.coerceIn(8, 16))

/** Masked ⇄ revealed with a vertical slide + cross-fade. */
@Composable
fun RevealableSecret(secret: String, revealed: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val colored = remember(secret, scheme) {
        colorizeSecret(secret, scheme.onSurface, scheme.primary, scheme.tertiary)
    }
    AnimatedContent(
        targetState = revealed,
        transitionSpec = {
            (fadeIn(Motion.smooth()) + slideInVertically(Motion.offset) { it / 2 }) togetherWith
                (fadeOut(Motion.fast()) + slideOutVertically(Motion.offset) { -it / 2 })
        },
        label = "reveal",
        modifier = modifier,
    ) { show ->
        if (show) Text(colored, style = SecretTextStyle)
        else Text(maskOf(secret), style = SecretTextStyle, color = scheme.onSurfaceVariant)
    }
}
