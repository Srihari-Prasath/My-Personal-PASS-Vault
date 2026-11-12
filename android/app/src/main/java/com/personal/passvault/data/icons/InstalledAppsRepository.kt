package com.personal.passvault.data.icons

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
data class InstalledApp(val packageName: String, val label: String)

/** Apps with a launcher icon (visible to us through the <queries> block in the manifest). */
@Singleton
class InstalledAppsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val pm: PackageManager = context.packageManager

    @Volatile
    private var cache: List<InstalledApp>? = null

    suspend fun list(forceRefresh: Boolean = false): List<InstalledApp> = withContext(Dispatchers.IO) {
        if (!forceRefresh) cache?.let { return@withContext it }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        infos.map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .also { cache = it }
    }

    fun isInstalled(packageName: String): Boolean = try {
        @Suppress("DEPRECATION")
        pm.getApplicationInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun label(packageName: String): String? = try {
        @Suppress("DEPRECATION")
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /** Real launcher icon (adaptive icons included), rendered to a square bitmap. */
    fun loadIcon(packageName: String, sizePx: Int): Bitmap? = try {
        pm.getApplicationIcon(packageName).toBitmap(sizePx, sizePx)
    } catch (e: Exception) {
        null
    }

    fun launchIntent(packageName: String): Intent? = pm.getLaunchIntentForPackage(packageName)
}
