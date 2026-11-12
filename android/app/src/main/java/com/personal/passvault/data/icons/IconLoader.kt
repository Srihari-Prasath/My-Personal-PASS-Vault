package com.personal.passvault.data.icons

import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.personal.passvault.domain.IconSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** [isApp] = real launcher icon (already shaped by the system); otherwise a website icon that needs a tile. */
@Immutable
class LoadedIcon(val bitmap: ImageBitmap, val isApp: Boolean)

/**
 * Picks the icon for an entry: first linked app that's installed -> first website icon -> null (letter avatar).
 * Decoded bitmaps are kept in memory so scrolling never decodes twice.
 */
@Singleton
class IconLoader @Inject constructor(
    private val apps: InstalledAppsRepository,
    private val web: WebIconRepository,
) {
    private val memory = LruCache<String, LoadedIcon>(300)

    /** Synchronous memory-only lookup, so already-seen icons appear instantly without flicker. */
    fun cached(spec: IconSpec): LoadedIcon? {
        spec.packages.forEach { pkg -> memory.get(appKey(pkg))?.let { return it } }
        spec.domains.forEach { d -> memory.get(webKey(d))?.let { return it } }
        return null
    }

    suspend fun load(spec: IconSpec): LoadedIcon? = withContext(Dispatchers.IO) {
        for (pkg in spec.packages) {
            appIcon(pkg)?.let { return@withContext it }
        }
        for (domain in spec.domains) {
            webIcon(domain)?.let { return@withContext it }
        }
        null
    }

    suspend fun appIcon(packageName: String): LoadedIcon? = withContext(Dispatchers.IO) {
        memory.get(appKey(packageName))?.let { return@withContext it }
        if (!apps.isInstalled(packageName)) return@withContext null
        apps.loadIcon(packageName, ICON_PX)
            ?.let { LoadedIcon(it.asImageBitmap(), isApp = true) }
            ?.also { memory.put(appKey(packageName), it) }
    }

    suspend fun webIcon(domain: String): LoadedIcon? = withContext(Dispatchers.IO) {
        memory.get(webKey(domain))?.let { return@withContext it }
        web.icon(domain)
            ?.let { LoadedIcon(it.asImageBitmap(), isApp = false) }
            ?.also { memory.put(webKey(domain), it) }
    }

    fun cachedApp(packageName: String): LoadedIcon? = memory.get(appKey(packageName))

    /** Called after "Refresh website icons" or on lock (icons hint at which sites you use). */
    fun clearWebIcons() {
        memory.snapshot().keys.filter { it.startsWith("web:") }.forEach { memory.remove(it) }
    }

    private fun appKey(pkg: String) = "app:$pkg"
    private fun webKey(domain: String) = "web:$domain"

    private companion object {
        const val ICON_PX = 192
    }
}
