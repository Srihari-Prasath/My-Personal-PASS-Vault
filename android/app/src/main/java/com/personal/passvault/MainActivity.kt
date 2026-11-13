package com.personal.passvault

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.passvault.core.crypto.VaultKeyManager
import com.personal.passvault.core.security.ClipboardHelper
import com.personal.passvault.core.session.VaultSession
import com.personal.passvault.data.icons.IconLoader
import com.personal.passvault.data.repo.SettingsRepository
import com.personal.passvault.data.repo.ThemeMode
import com.personal.passvault.ui.components.LocalClipboardHelper
import com.personal.passvault.ui.components.LocalIconLoader
import com.personal.passvault.ui.nav.AppNavHost
import com.personal.passvault.ui.theme.LocalReduceMotion
import com.personal.passvault.ui.theme.PassVaultTheme
import com.personal.passvault.ui.theme.rememberReduceMotion
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** FragmentActivity (not plain ComponentActivity) because BiometricPrompt needs it. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var session: VaultSession
    @Inject lateinit var keyManager: VaultKeyManager
    @Inject lateinit var clipboard: ClipboardHelper
    @Inject lateinit var iconLoader: IconLoader
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Blocks screenshots, screen recording and the preview in the Recent-apps screen
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle()
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK, ThemeMode.AMOLED -> true
            }
            // Screenshot block stays on in release builds no matter what is saved in settings
            val allowScreenshots = BuildConfig.DEBUG && settings.allowScreenshots
            LaunchedEffect(allowScreenshots) {
                if (allowScreenshots) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            }
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            PassVaultTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                CompositionLocalProvider(
                    LocalIconLoader provides iconLoader,
                    LocalClipboardHelper provides clipboard,
                    LocalReduceMotion provides rememberReduceMotion(),
                ) {
                    AppNavHost(
                        session = session,
                        keyManager = keyManager,
                        clipboard = clipboard,
                        iconLoader = iconLoader,
                        revealNeedsAuth = settings.revealNeedsAuth,
                    )
                }
            }
        }
    }
}
