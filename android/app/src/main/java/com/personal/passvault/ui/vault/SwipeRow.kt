package com.personal.passvault.ui.vault

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Telegram-style swipe: drag right/left past ~25% -> haptic tick -> release triggers the action and the row
 * springs back. Vertical scrolling is untouched (only horizontal drags are claimed).
 */
@Composable
fun SwipeRow(
    startIcon: ImageVector,
    startColor: Color,
    onSwipeStart: () -> Unit,
    endIcon: ImageVector,
    endColor: Color,
    onSwipeEnd: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var width by remember { mutableIntStateOf(0) }
    var dx by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    val currentStart by rememberUpdatedState(onSwipeStart)
    val currentEnd by rememberUpdatedState(onSwipeEnd)

    val threshold = width * 0.25f
    val progress = if (threshold > 0) (abs(dx) / threshold).coerceIn(0f, 1f) else 0f

    Box(modifier.onSizeChanged { width = it.width }) {
        // Action background revealed behind the row
        if (dx != 0f) {
            val right = dx > 0
            Box(
                Modifier
                    .matchParentSize()
                    .background((if (right) startColor else endColor).copy(alpha = 0.25f + 0.75f * progress)),
                contentAlignment = if (right) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Icon(
                    if (right) startIcon else endIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .padding(horizontal = 28.dp)
                        .size(26.dp)
                        .graphicsLayer {
                            val s = 0.6f + 0.4f * progress + if (armed) 0.15f else 0f
                            scaleX = s; scaleY = s
                        },
                )
            }
        }
        Box(
            Modifier
                .offset { IntOffset(dx.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val fired = dx
                            if (armed) {
                                if (fired > 0) currentStart() else currentEnd()
                            }
                            armed = false
                            scope.launch {
                                animate(dx, 0f, animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) { v, _ ->
                                    dx = v
                                }
                            }
                        },
                        onDragCancel = {
                            armed = false
                            scope.launch { animate(dx, 0f) { v, _ -> dx = v } }
                        },
                    ) { change, amount ->
                        change.consume()
                        // Read width (state) here, not the composition-time `threshold`, which would be stale
                        val th = width * 0.25f
                        val limit = width * 0.45f
                        // Rubber-band resistance past the threshold
                        val resistance = if (abs(dx) > th) 0.35f else 1f
                        dx = (dx + amount * resistance).coerceIn(-limit, limit)
                        val nowArmed = th > 0 && abs(dx) >= th
                        if (nowArmed != armed) {
                            armed = nowArmed
                            if (nowArmed) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                },
        ) { content() }
    }
}
