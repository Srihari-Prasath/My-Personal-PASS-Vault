package com.personal.passvault.ui.components

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.personal.passvault.core.security.ClipboardHelper
import com.personal.passvault.data.icons.IconLoader

val LocalIconLoader = staticCompositionLocalOf<IconLoader?> { null }
val LocalClipboardHelper = staticCompositionLocalOf<ClipboardHelper?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The AnimatedVisibilityScope of the current navigation destination. */
val LocalNavAnimatedScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Shared-element morph between screens (list avatar -> detail header avatar).
 * Falls back to a plain modifier when there's no transition scope (previews, dialogs).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElementOrSelf(key: String): Modifier {
    val sts = LocalSharedTransitionScope.current ?: return this
    val avs = LocalNavAnimatedScope.current ?: return this
    val base = this
    return with(sts) {
        base.sharedElement(rememberSharedContentState(key), avs)
    }
}

fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
