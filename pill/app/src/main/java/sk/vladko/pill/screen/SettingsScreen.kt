package sk.vladko.pill.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import sk.vladko.pill.pill
import sk.vladko.pill.rules.ThemeMode
import sk.vladko.pill.system.AppItem
import sk.vladko.pill.system.PermissionStatus
import sk.vladko.pill.system.permissions
import sk.vladko.pill.ui.APPS
import sk.vladko.pill.ui.Glyph
import sk.vladko.pill.ui.PILL_ICON
import sk.vladko.pill.ui.TOUCH_APP

private enum class Tab(
    val label: String,
    val icon: String,
) {
    Gestures("Gestures", TOUCH_APP),
    Menus("Menus", APPS),
    Pill("Pill", PILL_ICON),
}

/** Root of the settings screen: applies the chosen theme and hosts the three tabs. */
@Composable
fun SettingsScreen(onTheme: (dark: Boolean) -> Unit) {
    val ctx = LocalContext.current
    val s by ctx.pill.settings.value
        .collectAsState()
    val dark =
        when (s.theme) {
            ThemeMode.System -> isSystemInDarkTheme()
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }
    val pal = if (dark) DarkPal else LightPal
    SideEffect { onTheme(dark) }
    CompositionLocalProvider(LocalPal provides pal) {
        val scheme =
            if (dark) {
                darkColorScheme(background = pal.bg, surface = pal.panel, primary = pal.ink, onBackground = pal.ink, onSurface = pal.ink)
            } else {
                lightColorScheme(background = pal.bg, surface = pal.panel, primary = pal.ink, onBackground = pal.ink, onSurface = pal.ink)
            }
        MaterialTheme(colorScheme = scheme) {
            Surface(Modifier.fillMaxSize(), color = pal.bg) { App() }
        }
    }
}

@Composable
private fun App() {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var perms by remember { mutableStateOf(permissions(ctx).take(3)) }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) perms = permissions(ctx).take(3) }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    var tab by rememberSaveable { mutableStateOf(Tab.Gestures) }
    var pick by remember { mutableStateOf<PickRequest?>(null) }
    // Icons of all installed apps load once, off the main thread.
    val apps by produceState<List<AppItem>>(emptyList()) { value = ctx.pill.apps.launchable() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Problems(perms)
            when (tab) {
                Tab.Gestures -> GesturesTab { pick = it }
                Tab.Menus -> MenusTab { pick = it }
                Tab.Pill -> PillTab()
            }
        }
        TabBar(tab) { tab = it }
    }
    pick?.let { Picker(it, apps) { pick = null } }
}

@Composable
private fun Problems(perms: List<PermissionStatus>) {
    val ctx = LocalContext.current
    val off = perms.withIndex().filter { !it.value.on }
    if (off.isEmpty()) return
    Column(
        Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(20.dp),
            ).background(SignalBg)
            .border(1.dp, Signal.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
    ) {
        off.forEachIndexed { n, (i, p) ->
            if (n > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Signal.copy(alpha = 0.2f)))
            val adb = p.fix.startsWith("adb: ")
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Signal))
                Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                    Text("${p.name} is off", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (adb) "Needs a computer. Copy the command, run it with adb." else p.why,
                        color = Dim,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
                Text(
                    if (adb) "Copy" else "Turn on",
                    color = Bg,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .background(Ink)
                            .clickable {
                                if (adb) {
                                    ctx
                                        .getSystemService(
                                            ClipboardManager::class.java,
                                        ).setPrimaryClip(ClipData.newPlainText("adb", p.fix.removePrefix("adb: ")))
                                } else {
                                    val a =
                                        if (i ==
                                            0
                                        ) {
                                            Settings.ACTION_ACCESSIBILITY_SETTINGS
                                        } else {
                                            Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                                        }
                                    runCatching { ctx.startActivity(Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                }
                            }.padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }
    }
}

@Composable
private fun TabBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
) {
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(Panel)
                .border(1.dp, Line, CircleShape)
                .padding(5.dp),
        ) {
            Tab.entries.forEach { t ->
                val on = t == selected
                Row(
                    Modifier
                        .weight(
                            1f,
                        ).height(48.dp)
                        .clip(CircleShape)
                        .background(if (on) Ink else Color.Transparent)
                        .clickable { onSelect(t) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(t.icon, if (on) Bg else Dim, 20.dp)
                    Text(
                        t.label,
                        color = if (on) Bg else Dim,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 7.dp),
                    )
                }
            }
        }
    }
}
