package com.personal.passvault.data.icons

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.personal.passvault.data.db.DatabaseProvider
import com.personal.passvault.data.db.WebIconEntity
import com.personal.passvault.data.repo.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Website icons, fetched DIRECTLY from the website itself (no Google/third-party icon service, so nobody
 * else learns which sites you have accounts on). Results are cached inside the encrypted database.
 */
@Singleton
class WebIconRepository @Inject constructor(
    private val http: OkHttpClient,
    private val dbProvider: DatabaseProvider,
    private val settings: SettingsRepository,
) {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun icon(domain: String): Bitmap? = withContext(Dispatchers.IO) {
        val dao = runCatching { dbProvider.require().webIcons() }.getOrNull() ?: return@withContext null
        locks.getOrPut(domain) { Mutex() }.withLock {
            val cached = runCatching { dao.get(domain) }.getOrNull()
            if (cached != null) {
                cached.png?.let { return@withLock decode(it) }
                if (System.currentTimeMillis() - cached.fetchedAt < RETRY_FAILED_MS) return@withLock null
            }
            if (!settings.settings.value.downloadWebIcons) return@withLock null

            val png = runCatching { fetch(domain) }.getOrNull()
            runCatching {
                dbProvider.require().webIcons()
                    .upsert(WebIconEntity(domain, png, System.currentTimeMillis(), failed = png == null))
            }
            png?.let(::decode)
        }
    }

    /** Forget all cached website icons; they'll be downloaded again when shown. */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        runCatching { dbProvider.require().webIcons().clear() }
    }

    // ---------------------------------------------------------------------------------------------

    private fun fetch(domain: String): ByteArray? {
        val home = "https://$domain/".toHttpUrlOrNull() ?: return null
        val candidates = mutableListOf<Candidate>()
        var base: HttpUrl = home

        runCatching {
            http.newCall(request(home)).execute().use { resp ->
                base = resp.request.url
                if (resp.isSuccessful) {
                    val html = resp.body?.byteStream()?.let { readLimited(it, MAX_HTML_BYTES) }
                        ?.toString(Charsets.UTF_8).orEmpty()
                    candidates += parseIconLinks(html, base)
                }
            }
        }
        base.resolve("/apple-touch-icon.png")?.let { candidates += Candidate(it, 120) }
        base.resolve("/favicon.ico")?.let { candidates += Candidate(it, 10) }

        for (c in candidates.distinctBy { it.url }.sortedByDescending { it.score }.take(MAX_TRIES)) {
            val bytes = runCatching {
                http.newCall(request(c.url)).execute().use { resp ->
                    if (!resp.isSuccessful) null
                    else resp.body?.byteStream()?.let { readLimited(it, MAX_ICON_BYTES) }
                }
            }.getOrNull() ?: continue
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
            if (bmp.width < 16 || bmp.height < 16) continue
            return toPng(bmp)
        }
        return null
    }

    private data class Candidate(val url: HttpUrl, val score: Int)

    private fun parseIconLinks(html: String, base: HttpUrl): List<Candidate> {
        val head = html.substringBefore("</head>", html)
        return LINK_TAG.findAll(head).mapNotNull { tag ->
            val attrs = ATTR.findAll(tag.value).associate { m ->
                m.groupValues[1].lowercase() to (m.groups[3]?.value ?: m.groups[4]?.value ?: m.groups[5]?.value ?: "")
            }
            val rel = attrs["rel"]?.lowercase() ?: return@mapNotNull null
            val href = attrs["href"]?.trim().orEmpty()
            if ("icon" !in rel || "mask" in rel) return@mapNotNull null
            if (href.isEmpty() || href.startsWith("data:")) return@mapNotNull null
            if (href.substringBefore('?').endsWith(".svg") || attrs["type"] == "image/svg+xml") return@mapNotNull null
            val url = base.resolve(href)?.takeIf { it.isHttps } ?: return@mapNotNull null
            val apple = "apple-touch-icon" in rel
            val size = attrs["sizes"]?.substringBefore('x')?.toIntOrNull() ?: if (apple) 180 else 32
            // Prefer ~128–256 px; giant images aren't better, just slower
            val score = (if (size > 256) 256 - (size - 256) / 8 else size) + if (apple) 40 else 0
            Candidate(url, score)
        }.toList()
    }

    private fun request(url: HttpUrl) = Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
        .header("Accept", "text/html,image/avif,image/webp,image/png,image/*;q=0.8,*/*;q=0.5")
        .build()

    private fun readLimited(input: java.io.InputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        var total = 0
        while (total < max) {
            val n = input.read(buf, 0, minOf(buf.size, max - total))
            if (n < 0) break
            out.write(buf, 0, n)
            total += n
        }
        return out.toByteArray()
    }

    private fun toPng(src: Bitmap): ByteArray {
        val scaled = if (src.width > STORE_PX || src.height > STORE_PX) {
            val ratio = STORE_PX.toFloat() / maxOf(src.width, src.height)
            Bitmap.createScaledBitmap(src, (src.width * ratio).toInt(), (src.height * ratio).toInt(), true)
        } else src
        return ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun decode(png: ByteArray): Bitmap? = BitmapFactory.decodeByteArray(png, 0, png.size)

    private companion object {
        const val RETRY_FAILED_MS = 7L * 24 * 60 * 60 * 1000
        const val MAX_HTML_BYTES = 300 * 1024
        const val MAX_ICON_BYTES = 400 * 1024
        const val MAX_TRIES = 5
        const val STORE_PX = 192
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
        val LINK_TAG = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
        val ATTR = Regex("([a-zA-Z-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))")
    }
}
