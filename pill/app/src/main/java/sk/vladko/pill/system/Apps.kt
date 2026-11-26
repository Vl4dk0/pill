package sk.vladko.pill.system

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sk.vladko.pill.overlay.accentOf

private const val ICON_CACHE_SIZE = 48
private const val LABEL_CACHE_SIZE = 64
private const val ICON_PX = 96

class AppIcon(
    val image: ImageBitmap,
    val accentArgb: Int,
)

class AppItem(
    val pkg: String,
    val label: String,
    val icon: ImageBitmap,
)

/** One cache for app labels, icons and accents. Icon decoding runs off the main thread. */
class Apps(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val pm = appContext.packageManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val labels = LruCache<String, String>(LABEL_CACHE_SIZE)
    private val icons =
        object : LinkedHashMap<String, MutableStateFlow<AppIcon?>>(ICON_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MutableStateFlow<AppIcon?>>?): Boolean =
                size > ICON_CACHE_SIZE
        }

    /** App display name, or [pkg] when the app is gone. */
    fun label(pkg: String): String {
        labels.get(pkg)?.let { return it }
        val name = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
        if (name != null) labels.put(pkg, name)
        return name ?: pkg
    }

    fun installed(pkg: String): Boolean = pm.getLaunchIntentForPackage(pkg) != null

    fun launch(pkg: String): Boolean {
        val intent = pm.getLaunchIntentForPackage(pkg) ?: return false
        return runCatching {
            appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }

    /**
     * 96 px full-bleed icon and its accent colour, loaded off the main thread and kept for the last 48 packages.
     * Null until loaded or when the app is gone.
     */
    fun icon(pkg: String): StateFlow<AppIcon?> {
        var flow: MutableStateFlow<AppIcon?>? = null
        var created = false
        synchronized(icons) {
            val existing = icons[pkg]
            if (existing != null) {
                flow = existing
            } else {
                val fresh = MutableStateFlow<AppIcon?>(null)
                icons[pkg] = fresh
                flow = fresh
                created = true
            }
        }
        val target = flow!!
        if (created) {
            scope.launch {
                target.value = loadIcon(pkg)
            }
        }
        return target
    }

    /** Warms the icon cache in the background (e.g. for App menu packages when settings change). */
    fun preload(pkgs: Iterable<String>) {
        pkgs.forEach { if (it.isNotBlank()) icon(it) }
    }

    /** Launchable apps, sorted by label, excluding Pill itself. */
    suspend fun launchable(): List<AppItem> =
        withContext(Dispatchers.IO) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm
                .queryIntentActivities(intent, 0)
                .filter { it.activityInfo.packageName != appContext.packageName }
                .distinctBy { it.activityInfo.packageName }
                .sortedBy { it.loadLabel(pm).toString().lowercase() }
                .map { info ->
                    val pkg = info.activityInfo.packageName
                    val name = info.loadLabel(pm).toString()
                    labels.put(pkg, name)
                    val cached = synchronized(icons) { icons[pkg]?.value }
                    val img = cached?.image ?: info.loadIcon(pm).toBitmap().asImageBitmap()
                    AppItem(pkg, name, img)
                }
        }

    /** The 4 apps used last, excluding the current app, the launcher and Pill. Needs usage access; empty without it. */
    suspend fun recents(): List<AppItem> =
        withContext(Dispatchers.IO) {
            val usm = appContext.getSystemService(UsageStatsManager::class.java)
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - 6 * 3600_000L, now)
            val lastUsed = LinkedHashMap<String, Long>()
            val e = UsageEvents.Event()
            var foreground: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    lastUsed[e.packageName] = e.timeStamp
                    foreground = e.packageName
                }
                if (e.eventType == UsageEvents.Event.ACTIVITY_PAUSED && e.packageName == foreground) {
                    foreground = null
                }
            }
            val launcher =
                pm
                    .resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
                    ?.activityInfo
                    ?.packageName
            val ordered = lastUsed.entries.sortedByDescending { it.value }.map { it.key }
            val current = foreground
            ordered
                .filter { it != appContext.packageName && it != launcher && it != current && installed(it) }
                .take(4)
                .mapNotNull { pkg ->
                    val appIcon = iconBlocking(pkg) ?: return@mapNotNull null
                    AppItem(pkg, label(pkg), appIcon.image)
                }
        }

    private fun iconBlocking(pkg: String): AppIcon? {
        val existing = synchronized(icons) { icons[pkg] }
        existing?.value?.let { return it }
        val loaded = loadIcon(pkg)
        synchronized(icons) {
            val flow = icons[pkg]
            if (flow != null) {
                flow.value = loaded
            } else {
                icons[pkg] = MutableStateFlow(loaded)
            }
        }
        return loaded
    }

    private fun loadIcon(pkg: String): AppIcon? =
        runCatching {
            val bmp = pm.getApplicationIcon(pkg).toBitmap()
            AppIcon(bmp.asImageBitmap(), accentOf(bmp).toArgb())
        }.getOrNull()
}

/**
 * Draws a drawable into a square bitmap. An adaptive icon is drawn as its two full-bleed layers (the visible 72 of their
 * 108 units fill the square), so a circle clip later shows a filled circle, not the system mask's small icon in a square.
 */
private fun Drawable.toBitmap(sizePx: Int = ICON_PX): Bitmap {
    val bmp = createBitmap(sizePx, sizePx)
    val c = Canvas(bmp)
    if (this is AdaptiveIconDrawable) {
        val pad = (sizePx * 18 / 72f).toInt()
        listOfNotNull(background, foreground).forEach {
            it.setBounds(-pad, -pad, sizePx + pad, sizePx + pad)
            it.draw(c)
        }
    } else {
        setBounds(0, 0, sizePx, sizePx)
        draw(c)
    }
    return bmp
}
