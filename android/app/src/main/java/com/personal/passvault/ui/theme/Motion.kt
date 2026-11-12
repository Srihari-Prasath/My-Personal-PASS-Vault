package com.personal.passvault.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.core.spring as coreSpring

/**
 * One place for all motion, so the whole app feels consistent.
 * Springs (not fixed-duration tweens) are what make Telegram-style UIs feel "alive":
 * they can be interrupted mid-way and keep their momentum.
 */
object Motion {
    const val DAMPING = 0.82f
    const val STIFFNESS = Spring.StiffnessMediumLow // ~300-350 ms feel

    fun <T> smooth(): FiniteAnimationSpec<T> = coreSpring(dampingRatio = DAMPING, stiffness = STIFFNESS)
    fun <T> bouncy(): FiniteAnimationSpec<T> = coreSpring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
    fun <T> fast(): FiniteAnimationSpec<T> = coreSpring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)

    val offset: FiniteAnimationSpec<IntOffset> = coreSpring(
        dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntOffset.VisibilityThreshold,
    )
    val size: FiniteAnimationSpec<IntSize> = coreSpring(
        dampingRatio = DAMPING, stiffness = STIFFNESS,
        visibilityThreshold = IntSize.VisibilityThreshold,
    )
}

/** True when the user turned off animations in Android accessibility / developer settings. */
val LocalReduceMotion = compositionLocalOf { false }

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Spring, or an instant snap if the user disabled animations. */
@Composable
fun <T> motionSpec(): FiniteAnimationSpec<T> =
    if (LocalReduceMotion.current) snap() else Motion.smooth()
