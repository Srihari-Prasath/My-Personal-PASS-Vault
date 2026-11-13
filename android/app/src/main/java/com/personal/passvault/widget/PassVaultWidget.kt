package com.personal.passvault.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.RemoteViews
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.passvault.R
import com.personal.passvault.core.security.ClipboardHelper
import com.personal.passvault.core.session.VaultSession
import com.personal.passvault.data.repo.SettingsRepository
import com.personal.passvault.ui.components.CopyPill
import com.personal.passvault.ui.components.LocalClipboardHelper
import com.personal.passvault.ui.generator.GeneratorPanel
import com.personal.passvault.ui.theme.PassVaultTheme
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun session(): VaultSession
}

/** Home-screen widget: "Lock now" and "Generate password". Shows no secrets, ever. */
class PassVaultWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_passvault)
        val lock = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, PassVaultWidget::class.java).setAction(ACTION_LOCK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val generate = PendingIntent.getActivity(
            context, 1,
            Intent(context, GeneratorActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_lock, lock)
        views.setOnClickPendingIntent(R.id.widget_generate, generate)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_LOCK) {
            val session = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java).session()
            val wasOpen = session.unlocked.value
            session.lock()
            Toast.makeText(context, if (wasOpen) "PassVault locked" else "PassVault is already locked", Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val ACTION_LOCK = "com.personal.passvault.widget.LOCK"
    }
}

/** Password generator as a floating sheet — works without unlocking (it never touches the vault). */
@AndroidEntryPoint
class GeneratorActivity : FragmentActivity() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var clipboard: ClipboardHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            val s by settings.settings.collectAsStateWithLifecycle()
            val clip by clipboard.state.collectAsStateWithLifecycle()
            PassVaultTheme(s.themeMode, s.dynamicColor) {
                CompositionLocalProvider(LocalClipboardHelper provides clipboard) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                            .clickable(remember { MutableInteractionSource() }, indication = null) { finish() },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(8.dp)
                                .clickable(remember { MutableInteractionSource() }, indication = null) { },
                        ) {
                            Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 20.dp)) {
                                Text(
                                    "Password generator",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                                GeneratorPanel(onCopy = { clipboard.copy("Password", it) })
                            }
                        }
                        CopyPill(clip, onClearNow = clipboard::clearNow, modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp))
                    }
                }
            }
        }
    }
}
