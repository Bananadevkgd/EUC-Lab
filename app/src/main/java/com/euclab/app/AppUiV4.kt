package com.euclab.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.euclab.app.ble.BleWheelManager
import com.euclab.app.data.*
import com.euclab.app.service.RideRecorderService
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private val V4Bg = Color(0xFF090B0F)
private val V4Surface = Color(0xFF11151C)
private val V4Surface2 = Color(0xFF171C24)
private val V4Muted = Color(0xFF7C8798)
private val V4Accent = Color(0xFFB6FF35)
private val V4Good = Color(0xFF6DFF9A)
private val V4Danger = Color(0xFFFF5060)
private val V4Amber = Color(0xFFFFC857)
private val V4Blue = Color(0xFF79C7FF)

private enum class V4Language { RU, EN }
private enum class V4Screen { HOME, RIDES, BATTERY, WHEEL, SETTINGS }

private fun V4Language.t(ru: String, en: String): String = if (this == V4Language.RU) ru else en

private fun V4Screen.title(lang: V4Language): String = when (this) {
    V4Screen.HOME -> lang.t("Главная", "Home")
    V4Screen.RIDES -> lang.t("Поездки", "Rides")
    V4Screen.BATTERY -> lang.t("Батарея", "Battery")
    V4Screen.WHEEL -> lang.t("Колесо", "Wheel")
    V4Screen.SETTINGS -> lang.t("Настройки", "Settings")
}

private fun V4Screen.icon(): String = when (this) {
    V4Screen.HOME -> "●"
    V4Screen.RIDES -> "≋"
    V4Screen.BATTERY -> "▦"
    V4Screen.WHEEL -> "◉"
    V4Screen.SETTINGS -> "⚙"
}

@Composable
fun AppThemeV4(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = V4Bg,
            surface = V4Surface,
            primary = V4Accent,
        ),
        content = { Surface(Modifier.fillMaxSize(), color = V4Bg) { content() } },
    )
}

@Composable
fun EucLabAppV4(ble: BleWheelManager) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("euc_lab_settings", Context.MODE_PRIVATE) }
    var lang by remember {
        mutableStateOf(
            runCatching { V4Language.valueOf(prefs.getString("language", V4Language.RU.name) ?: V4Language.RU.name) }
                .getOrDefault(V4Language.RU)
        )
    }
    var debug by remember { mutableStateOf(prefs.getBoolean("debug_enabled", false)) }
    var screen by remember { mutableStateOf(V4Screen.HOME) }

    Scaffold(
        containerColor = V4Bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0E1218), tonalElevation = 0.dp) {
                V4Screen.values().forEach { item ->
                    NavigationBarItem(
                        selected = screen == item,
                        onClick = { screen = item },
                        icon = { Text(item.icon(), fontSize = 15.sp, fontWeight = FontWeight.Black) },
                        label = { Text(item.title(lang), fontSize = 9.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = V4Accent,
                            indicatorColor = V4Accent,
                            unselectedIconColor = V4Muted,
                            unselectedTextColor = V4Muted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().background(V4Bg).padding(padding)) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 18 }) togetherWith
                        (fadeOut(tween(120)) + slideOutVertically(tween(160)) { -it / 24 })
                },
                label = "screen",
            ) { target ->
                when (target) {
                    V4Screen.HOME -> V4Home(ble, lang, debug) { screen = V4Screen.RIDES }
                    V4Screen.RIDES -> V4Rides(lang)
                    V4Screen.BATTERY -> V4Battery(lang)
                    V4Screen.WHEEL -> V4Wheel(ble, lang)
                    V4Screen.SETTINGS -> V4Settings(
                        lang = lang,
                        debug = debug,
                        onLang = {
                            lang = it
                            prefs.edit().putString("language", it.name).apply()
                        },
                        onDebug = {
                            debug = it
                            prefs.edit().putBoolean("debug_enabled", it).apply()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun V4Home(ble: BleWheelManager, lang: V4Language, debug: Boolean, openRides: () -> Unit) {
    val telemetry by WheelRepository.telemetry.collectAsState()
    val link by WheelRepository.linkState.collectAsState()
    val linkMessage by WheelRepository.linkMessage.collectAsState()
    val raw by WheelRepository.rawPacket.collectAsState()
    val recording by WheelRepository.recording.collectAsState()
    val lastLog by WheelRepository.lastLogPath.collectAsState()
    val candidates by ble.candidates.collectAsState()
    val lightOn by ble.lightOn.collectAsState()
    var showAll by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) ble.startScan()
    }

    fun scan() {
        showAll = false
        if (ble.canScan() && ble.canConnect()) {
            ble.startScan()
        } else {
            val permissions = buildList {
                if (Build.VERSION.SDK_INT >= 31) {
                    add(Manifest.permission.BLUETOOTH_SCAN)
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                } else add(Manifest.permission.ACCESS_FINE_LOCATION)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }.toTypedArray()
            permissionLauncher.launch(permissions)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Spacer(Modifier.height(28.dp)) }
        item { V4Header(telemetry, link, lang) }
        item { V4Speed(telemetry, lang) }
        item { V4MetricGrid(telemetry, lang) }
        item { V4LightQuick(telemetry != null, lightOn, lang) { ble.setLight(!lightOn) } }
        item { V4Connection(telemetry, link, linkMessage, recording, lang) }
        if (debug) item { V4Debug(raw, lang) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = ::scan,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202630)),
                ) {
                    Text(
                        when (link) {
                            LinkState.SCANNING -> lang.t("ПОИСК…", "SCANNING…")
                            LinkState.CONNECTED -> lang.t("СМЕНИТЬ КОЛЕСО", "CHANGE WHEEL")
                            else -> lang.t("ПОДКЛЮЧИТЬ", "CONNECT")
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Button(
                    onClick = {
                        if (recording) {
                            context.startService(Intent(context, RideRecorderService::class.java).setAction(RideRecorderService.ACTION_STOP))
                        } else {
                            ContextCompat.startForegroundService(context, Intent(context, RideRecorderService::class.java).setAction(RideRecorderService.ACTION_START))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (recording) V4Danger else V4Accent,
                        contentColor = Color.Black,
                    ),
                ) {
                    Text(
                        if (recording) lang.t("СТОП ЛОГ", "STOP LOG") else lang.t("НАЧАТЬ ЛОГ", "START LOG"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }

        if (link == LinkState.SCANNING && candidates.isNotEmpty()) {
            val euc = candidates.filter { it.likelyEuc }
            val visible = if (showAll) candidates else euc
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(lang.t("НАЙДЕННЫЕ КОЛЁСА", "NEARBY EUC"), color = V4Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (candidates.size > euc.size) {
                        Text(
                            if (showAll) lang.t("ТОЛЬКО EUC", "EUC ONLY") else lang.t("ПОКАЗАТЬ ВСЕ", "SHOW ALL"),
                            color = V4Accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { showAll = !showAll },
                        )
                    }
                }
            }
            items(visible, key = { it.address }) { candidate ->
                V4Device(candidate) { ble.connect(candidate.address) }
            }
        }

        lastLog?.let { path ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = openRides),
                    colors = CardDefaults.cardColors(containerColor = V4Surface),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(lang.t("ПОСЛЕДНЯЯ ЗАПИСЬ", "LAST LOG"), color = V4Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(path.substringAfterLast('/'), color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(lang.t("ОТКРЫТЬ ›", "OPEN ›"), color = V4Accent, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun V4Header(t: Telemetry?, link: LinkState, lang: V4Language) {
    val online = t != null
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("EUC LAB", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("${t?.model ?: "Sherman L"} · v0.0.4", color = V4Muted, fontSize = 12.sp)
        }
        Row(
            modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(if (online) Color(0xFF173A26) else Color(0xFF222831)).padding(horizontal = 13.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(99.dp)).background(if (online) V4Good else V4Muted))
            Text(
                if (online) lang.t("ОНЛАЙН", "ONLINE") else when (link) {
                    LinkState.SCANNING -> lang.t("ПОИСК", "SCAN")
                    LinkState.CONNECTING, LinkState.DISCOVERING -> lang.t("СВЯЗЬ…", "LINKING…")
                    LinkState.ERROR -> lang.t("ОШИБКА", "ERROR")
                    else -> lang.t("НЕ В СЕТИ", "OFFLINE")
                },
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun v4PwmColor(pwm: Float): Color {
    val p = pwm.coerceIn(0f, 100f)
    return Color.hsv(120f * (1f - p / 100f), 0.78f, 1f)
}

@Composable
private fun V4Speed(t: Telemetry?, lang: V4Language) {
    val speed = t?.speedKmh?.absoluteValue ?: 0f
    val pwm = t?.pwmPercent ?: 0f
    val color = v4PwmColor(pwm)
    Card(colors = CardDefaults.cardColors(containerColor = V4Surface), shape = RoundedCornerShape(30.dp)) {
        Column(Modifier.padding(24.dp)) {
            Text(lang.t("СКОРОСТЬ", "SPEED"), color = V4Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(String.format(Locale.getDefault(), "%.1f", speed), color = Color.White, fontSize = 76.sp, lineHeight = 78.sp, fontWeight = FontWeight.Black)
                Text(" km/h", color = V4Muted, fontSize = 18.sp, modifier = Modifier.padding(bottom = 14.dp))
            }
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(99.dp)).background(Color(0xFF29303A))) {
                Box(Modifier.fillMaxWidth(pwm.coerceIn(0f, 100f) / 100f).height(10.dp).background(color))
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("PWM ${String.format(Locale.getDefault(), "%.0f", pwm)}%", color = color, fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                t?.let { Text("${it.batteryPercent}% · ${String.format(Locale.getDefault(), "%.1f V", it.voltageV)}", color = Color(0xFFD9DEE7), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

private enum class V4Metric { BATTERY, VOLTAGE, PHASE, MOSFET, PITCH, PWM, TRIP, ODOMETER, FIRMWARE, CHARGING }

private fun V4Metric.title(lang: V4Language): String = when (this) {
    V4Metric.BATTERY -> lang.t("БАТАРЕЯ", "BATTERY")
    V4Metric.VOLTAGE -> lang.t("НАПРЯЖЕНИЕ", "VOLTAGE")
    V4Metric.PHASE -> lang.t("ФАЗНЫЙ ТОК", "PHASE CURRENT")
    V4Metric.MOSFET -> lang.t("ТЕМП. MOSFET", "MOSFET TEMP")
    V4Metric.PITCH -> lang.t("УГОЛ", "PITCH")
    V4Metric.PWM -> "PWM"
    V4Metric.TRIP -> lang.t("ПРОБЕГ ПОЕЗДКИ", "TRIP")
    V4Metric.ODOMETER -> lang.t("ОДОМЕТР", "ODOMETER")
    V4Metric.FIRMWARE -> lang.t("ПРОШИВКА", "FIRMWARE")
    V4Metric.CHARGING -> lang.t("ЗАРЯДКА", "CHARGING")
}

private fun V4Metric.value(t: Telemetry?, lang: V4Language): String = when (this) {
    V4Metric.BATTERY -> t?.let { "${it.batteryPercent}%" } ?: "—"
    V4Metric.VOLTAGE -> t?.let { String.format(Locale.getDefault(), "%.1f V", it.voltageV) } ?: "—"
    V4Metric.PHASE -> t?.let { String.format(Locale.getDefault(), "%.1f A", it.phaseCurrentA) } ?: "—"
    V4Metric.MOSFET -> t?.let { String.format(Locale.getDefault(), "%.1f °C", it.mosfetTempC) } ?: "—"
    V4Metric.PITCH -> t?.let { String.format(Locale.getDefault(), "%.2f°", it.pitchDeg) } ?: "—"
    V4Metric.PWM -> t?.let { String.format(Locale.getDefault(), "%.0f%%", it.pwmPercent) } ?: "—"
    V4Metric.TRIP -> t?.let { String.format(Locale.getDefault(), "%.2f km", it.tripKm) } ?: "—"
    V4Metric.ODOMETER -> t?.let { String.format(Locale.getDefault(), "%.1f km", it.totalKm) } ?: "—"
    V4Metric.FIRMWARE -> t?.firmwareRaw?.toString() ?: "—"
    V4Metric.CHARGING -> t?.let { if (it.charging) lang.t("ДА", "YES") else lang.t("НЕТ", "NO") } ?: "—"
}

@Composable
private fun V4MetricGrid(t: Telemetry?, lang: V4Language) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("dashboard_metrics", Context.MODE_PRIVATE) }
    val defaults = listOf(V4Metric.BATTERY, V4Metric.VOLTAGE, V4Metric.PHASE, V4Metric.MOSFET)
    var selected by remember {
        mutableStateOf(defaults.mapIndexed { index, fallback ->
            runCatching { V4Metric.valueOf(prefs.getString("slot_$index", fallback.name) ?: fallback.name) }.getOrDefault(fallback)
        })
    }
    var editSlot by remember { mutableStateOf<Int?>(null) }

    AnimatedContent(
        targetState = editSlot,
        transitionSpec = {
            (fadeIn(tween(180)) + expandVertically(tween(280), expandFrom = Alignment.Top)) togetherWith
                (fadeOut(tween(120)) + shrinkVertically(tween(220), shrinkTowards = Alignment.Top))
        },
        label = "metricPicker",
    ) { slot ->
        if (slot == null) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    V4MetricTile(selected[0], t, lang, Modifier.weight(1f)) { editSlot = 0 }
                    V4MetricTile(selected[1], t, lang, Modifier.weight(1f)) { editSlot = 1 }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    V4MetricTile(selected[2], t, lang, Modifier.weight(1f)) { editSlot = 2 }
                    V4MetricTile(selected[3], t, lang, Modifier.weight(1f)) { editSlot = 3 }
                }
                Text(lang.t("Нажми на карточку — она развернётся", "Tap a card to unfold its selector"), color = Color(0xFF586273), fontSize = 9.sp, modifier = Modifier.padding(horizontal = 2.dp))
            }
        } else {
            V4MetricPicker(selected[slot], lang, onCancel = { editSlot = null }) { metric ->
                selected = selected.toMutableList().apply { this[slot] = metric }
                prefs.edit().putString("slot_$slot", metric.name).apply()
                editSlot = null
            }
        }
    }
}

@Composable
private fun V4MetricTile(kind: V4Metric, t: Telemetry?, lang: V4Language, modifier: Modifier, onClick: () -> Unit) {
    val pressScale by animateFloatAsState(targetValue = 1f, animationSpec = spring(), label = "metricScale")
    Card(
        modifier = modifier.graphicsLayer { scaleX = pressScale; scaleY = pressScale }.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = V4Surface2),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.padding(17.dp)) {
            AnimatedContent(targetState = kind, transitionSpec = { (fadeIn(tween(180)) + slideInVertically { it / 3 }) togetherWith (fadeOut(tween(100)) + slideOutVertically { -it / 3 }) }, label = "metricValue") { target ->
                Column {
                    Text(target.title(lang), color = V4Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(7.dp))
                    Text(target.value(t, lang), color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun V4MetricPicker(current: V4Metric, lang: V4Language, onCancel: () -> Unit, onSelect: (V4Metric) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF181E27)), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(lang.t("Что показывать?", "What should this card show?"), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text(lang.t("Выбери показатель — карточка свернётся обратно", "Choose a metric and the card folds back"), color = V4Muted, fontSize = 10.sp)
                }
                Text("×", color = V4Muted, fontSize = 24.sp, modifier = Modifier.clickable(onClick = onCancel).padding(6.dp))
            }
            Spacer(Modifier.height(14.dp))
            V4Metric.values().chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { metric ->
                        val chosen = metric == current
                        Card(
                            modifier = Modifier.weight(1f).clickable { onSelect(metric) },
                            colors = CardDefaults.cardColors(containerColor = if (chosen) Color(0xFF26351B) else Color(0xFF202630)),
                            shape = RoundedCornerShape(15.dp),
                        ) {
                            Text(
                                (if (chosen) "✓  " else "") + metric.title(lang),
                                color = if (chosen) V4Accent else Color(0xFFD9DFE8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            )
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun V4LightQuick(online: Boolean, on: Boolean, lang: V4Language, toggle: () -> Unit) {
    val bg by animateColorAsState(if (on) Color(0xFFD7FF62) else Color(0xFF202630), tween(250), label = "lightColor")
    val fg = if (on) Color.Black else Color.White
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = online, onClick = toggle),
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 17.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (on) "☀" else "◌", color = fg, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(lang.t("ФАРА", "HEADLIGHT"), color = fg, fontSize = 12.sp, fontWeight = FontWeight.Black)
                Text(
                    if (!online) lang.t("Подключи колесо", "Connect the wheel") else if (on) lang.t("Включена · нажми, чтобы выключить", "On · tap to switch off") else lang.t("Выключена · нажми, чтобы включить", "Off · tap to switch on"),
                    color = fg.copy(alpha = 0.68f),
                    fontSize = 10.sp,
                )
            }
            Text(if (on) "ON" else "OFF", color = fg, fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun V4Connection(t: Telemetry?, link: LinkState, message: String, recording: Boolean, lang: V4Language) {
    Card(colors = CardDefaults.cardColors(containerColor = V4Surface), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (t != null) lang.t("● ТЕЛЕМЕТРИЯ ОНЛАЙН", "● TELEMETRY ONLINE") else lang.t("● СОЕДИНЕНИЕ", "● CONNECTION"), color = if (t != null) V4Good else if (link == LinkState.ERROR) V4Danger else V4Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (recording) lang.t("● ПИШЕМ ЛОГ", "● LOGGING") else lang.t("Лог выключен", "Log off"), color = if (recording) V4Amber else Color(0xFF626C7B), fontSize = 9.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (t != null) "${t.model} · ${t.batteryPercent}% · ${String.format(Locale.getDefault(), "%.1f V", t.voltageV)}" else when (link) {
                    LinkState.ERROR -> message
                    LinkState.SCANNING -> lang.t("Ищу Bluetooth-устройства…", "Scanning Bluetooth devices…")
                    LinkState.CONNECTING, LinkState.DISCOVERING -> lang.t("Подключаюсь к колесу…", "Connecting to wheel…")
                    else -> lang.t("Колесо не подключено", "Wheel is not connected")
                },
                color = Color(0xFFD8DEE8),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun V4Debug(raw: String, lang: V4Language) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1015)), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(15.dp)) {
            Text(lang.t("RAW BLE · ОТЛАДКА", "RAW BLE · DEBUG"), color = V4Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(raw, color = Color(0xFF8791A0), fontSize = 9.sp, lineHeight = 13.sp, minLines = 3, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun V4Device(candidate: BleCandidate, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (candidate.likelyEuc) Color(0xFF17241A) else Color(0xFF12161D)).clickable(onClick = onClick).padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(candidate.name, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(candidate.address, color = Color(0xFF687283), fontSize = 9.sp)
        }
        Text("${candidate.rssi} dBm", color = if (candidate.likelyEuc) V4Good else V4Muted, fontSize = 10.sp)
    }
}

private data class BatteryInsight(val titleRu: String, val titleEn: String, val bodyRu: String, val bodyEn: String, val color: Color, val icon: String)

private fun batteryInsight(bms: BmsSnapshot?, t: Telemetry?): BatteryInsight {
    if (bms == null) return BatteryInsight("Жду Smart BMS", "Waiting for Smart BMS", "Подключись к колесу и подожди несколько секунд — соберу обе BMS и оценю состояние.", "Connect the wheel and wait a few seconds while both BMS packs are collected.", V4Muted, "…")
    val packs = listOf(bms.pack1, bms.pack2)
    if (packs.any { !it.complete }) return BatteryInsight("Данные ещё собираются", "Still collecting data", "Не все 36 ячеек получены. Оценка появится после полного кадра обеих BMS.", "Not all 36 cells are available yet. The assessment will appear after both BMS frames are complete.", V4Muted, "…")

    val maxDeltaMv = packs.mapNotNull { it.deltaV }.maxOrNull()?.times(1000f) ?: 0f
    val temps = packs.flatMap { it.temperaturesC }.filter { it in -40f..120f && it != 0f }
    val minTemp = temps.minOrNull()
    val maxTemp = temps.maxOrNull()
    val avgDiffMv = kotlin.math.abs((packs[0].avgCellV ?: 0f) - (packs[1].avgCellV ?: 0f)) * 1000f

    return when {
        maxTemp != null && maxTemp >= 60f -> BatteryInsight("Батарея перегрета", "Battery is too hot", "Максимальная температура ${"%.1f".format(maxTemp)}°C. Снизь нагрузку и дай батарее остыть.", "Maximum temperature is ${"%.1f".format(maxTemp)}°C. Reduce load and let the battery cool down.", V4Danger, "!")
        maxDeltaMv >= 50f -> BatteryInsight("Нужна проверка баланса", "Cell balance needs attention", "Разброс ячеек до ${"%.0f".format(maxDeltaMv)} mV. Это уже заметно — стоит проследить, повторяется ли он после полного заряда.", "Cell spread reaches ${"%.0f".format(maxDeltaMv)} mV. Keep an eye on whether it remains after a full charge.", V4Danger, "!")
        minTemp != null && minTemp < 0f -> BatteryInsight("Батарея слишком холодная", "Battery is very cold", "Минимальная температура ${"%.1f".format(minTemp)}°C. На холоде доступная мощность и зарядный ток могут быть ниже — избегай пиковых нагрузок и зарядки до прогрева.", "Minimum temperature is ${"%.1f".format(minTemp)}°C. Cold cells can deliver and accept less current; avoid peak loads and charging until warmed.", V4Blue, "❄")
        maxTemp != null && maxTemp >= 50f -> BatteryInsight("Батарея горячая", "Battery is hot", "Максимальная температура ${"%.1f".format(maxTemp)}°C. Пока не критично, но запас по нагреву уже небольшой.", "Maximum temperature is ${"%.1f".format(maxTemp)}°C. Not critical yet, but thermal headroom is getting smaller.", V4Amber, "!")
        maxDeltaMv >= 30f || avgDiffMv >= 15f -> BatteryInsight("Стоит наблюдать", "Worth monitoring", "Максимальный разброс ${"%.0f".format(maxDeltaMv)} mV. Явной проблемы не видно, но сравни показатель после следующего полного заряда.", "Maximum spread is ${"%.0f".format(maxDeltaMv)} mV. No clear fault, but compare it again after the next full charge.", V4Amber, "i")
        minTemp != null && minTemp < 8f -> BatteryInsight("Батарея холодная", "Battery is cold", "Температура опускается до ${"%.1f".format(minTemp)}°C. Для спокойной езды нормально, но максимальная отдача может быть ниже, чем у тёплой батареи.", "Temperature drops to ${"%.1f".format(minTemp)}°C. Fine for gentle riding, but peak output may be lower than with warm cells.", V4Blue, "❄")
        maxDeltaMv <= 15f -> BatteryInsight("Батарея в отличном состоянии", "Battery looks excellent", "Обе BMS 36/36, максимальный разброс всего ${"%.0f".format(maxDeltaMv)} mV${if (minTemp != null && maxTemp != null) ", температуры ${"%.0f".format(minTemp)}–${"%.0f".format(maxTemp)}°C" else ""}. По текущим данным обслуживание не требуется.", "Both BMS packs are 36/36 and cell spread is only ${"%.0f".format(maxDeltaMv)} mV${if (minTemp != null && maxTemp != null) ", temperatures ${"%.0f".format(minTemp)}–${"%.0f".format(maxTemp)}°C" else ""}. No maintenance is indicated by the current data.", V4Good, "✓")
        else -> BatteryInsight("Батарея в норме", "Battery looks healthy", "Обе BMS получены полностью. Разброс ${"%.0f".format(maxDeltaMv)} mV — для текущего состояния всё выглядит ровно.", "Both BMS packs are complete. Cell spread is ${"%.0f".format(maxDeltaMv)} mV and looks even at the moment.", V4Good, "✓")
    }
}

@Composable
private fun V4Battery(lang: V4Language) {
    val bms by WheelRepository.bms.collectAsState()
    val telemetry by WheelRepository.telemetry.collectAsState()
    val insight = batteryInsight(bms, telemetry)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Spacer(Modifier.height(28.dp)) }
        item { V4Section("Smart BMS", lang.t("Два аккумуляторных пакета Sherman L", "Sherman L dual battery packs")) }
        item { V4InsightCard(insight, lang) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = V4Surface), shape = RoundedCornerShape(22.dp)) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(telemetry?.model ?: "Sherman L", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(lang.t("Общее напряжение колеса", "Wheel voltage"), color = V4Muted, fontSize = 9.sp)
                    }
                    Text(telemetry?.let { "${it.batteryPercent}% · ${String.format(Locale.getDefault(), "%.1f V", it.voltageV)}" } ?: "—", color = V4Accent, fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        if (bms == null) item { V4Empty(lang.t("Жду страницы Smart BMS от колеса…", "Waiting for Smart BMS pages…")) }
        else {
            item { V4BmsPack(bms!!.pack1, lang) }
            item { V4BmsPack(bms!!.pack2, lang) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun V4InsightCard(insight: BatteryInsight, lang: V4Language) {
    Card(colors = CardDefaults.cardColors(containerColor = insight.color.copy(alpha = 0.12f)), shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(insight.color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Text(insight.icon, color = insight.color, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(lang.t("AI-ПОДСКАЗКА · ЛОКАЛЬНАЯ ДИАГНОСТИКА", "AI INSIGHT · LOCAL DIAGNOSTICS"), color = insight.color, fontSize = 9.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(5.dp))
                Text(lang.t(insight.titleRu, insight.titleEn), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(5.dp))
                Text(lang.t(insight.bodyRu, insight.bodyEn), color = Color(0xFFCBD2DD), fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
private fun V4BmsPack(pack: BmsPack, lang: V4Language) {
    val min = pack.minCellV
    val max = pack.maxCellV
    Card(colors = CardDefaults.cardColors(containerColor = V4Surface), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("BMS ${pack.index}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text("${pack.validCells.size}/36 ${lang.t("ячеек получено", "cells received")}", color = V4Muted, fontSize = 9.sp)
                }
                Text(pack.deltaV?.let { "Δ ${String.format(Locale.getDefault(), "%.0f mV", it * 1000f)}" } ?: "Δ —", color = if ((pack.deltaV ?: 0f) > 0.03f) V4Danger else V4Good, fontSize = 14.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                V4Mini(lang.t("Мин.", "Min"), min?.let { String.format(Locale.getDefault(), "%.3f V", it) } ?: "—")
                V4Mini(lang.t("Макс.", "Max"), max?.let { String.format(Locale.getDefault(), "%.3f V", it) } ?: "—")
                V4Mini(lang.t("Сред.", "Avg"), pack.avgCellV?.let { String.format(Locale.getDefault(), "%.3f V", it) } ?: "—")
                V4Mini(lang.t("Ток", "Current"), pack.currentA?.let { String.format(Locale.getDefault(), "%.2f A", it) } ?: "—")
            }
            Spacer(Modifier.height(16.dp))
            Text(lang.t("ЯЧЕЙКИ", "CELLS"), color = V4Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            pack.cells.chunked(6).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEachIndexed { colIndex, value ->
                        val cell = rowIndex * 6 + colIndex + 1
                        val isMin = value > 1f && min != null && kotlin.math.abs(value - min) < 0.0005f
                        val isMax = value > 1f && max != null && kotlin.math.abs(value - max) < 0.0005f
                        Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFF1B212A)), shape = RoundedCornerShape(10.dp)) {
                            Column(Modifier.padding(vertical = 7.dp, horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(cell.toString(), color = Color(0xFF687384), fontSize = 7.sp)
                                Text(if (value > 1f) String.format(Locale.getDefault(), "%.3f", value) else "—", color = when { isMin -> V4Danger; isMax -> V4Good; else -> Color(0xFFE3E8EF) }, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
            }
            val temps = pack.temperaturesC.filter { it in -40f..120f && it != 0f }
            if (temps.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(lang.t("Температуры: ", "Temperatures: ") + temps.joinToString(" · ") { String.format(Locale.getDefault(), "%.1f°C", it) }, color = V4Muted, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun V4Wheel(ble: BleWheelManager, lang: V4Language) {
    val t by WheelRepository.telemetry.collectAsState()
    val lightOn by ble.lightOn.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }
    var pedalMode by remember { mutableStateOf<Int?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val online = t != null
    val stopped = (t?.speedKmh?.absoluteValue ?: 0f) < 1f

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Spacer(Modifier.height(28.dp)) }
        item { V4Section(lang.t("Колесо", "Wheel"), lang.t("Быстрые команды Veteran / LeaperKim", "Veteran / LeaperKim quick controls")) }
        item { V4LightQuick(online, lightOn, lang) { message = if (ble.setLight(!lightOn)) lang.t("Команда фары отправлена", "Headlight command sent") else lang.t("Не удалось отправить команду", "Could not send command") } }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = V4Surface), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(17.dp)) {
                    Text(lang.t("БЫСТРЫЕ ДЕЙСТВИЯ", "QUICK ACTIONS"), color = V4Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { message = if (ble.beep()) lang.t("Beep отправлен", "Beep sent") else lang.t("Колесо не подключено", "Wheel is not connected") },
                        enabled = online,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202630), contentColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text(lang.t("🔊  ПОДАТЬ СИГНАЛ", "🔊  BEEP"), fontWeight = FontWeight.Bold) }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = V4Surface), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(17.dp)) {
                    Text(lang.t("РЕЖИМ ПЕДАЛЕЙ", "PEDAL MODE"), color = V4Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(lang.t("Меняется только когда колесо стоит", "Available only while the wheel is stopped"), color = V4Muted, fontSize = 9.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to lang.t("ЖЁСТКИЙ", "HARD"), 1 to lang.t("СРЕДНИЙ", "MEDIUM"), 2 to lang.t("МЯГКИЙ", "SOFT")).forEach { (mode, label) ->
                            Button(
                                onClick = {
                                    if (ble.setPedalMode(mode)) {
                                        pedalMode = mode
                                        message = lang.t("Режим педалей отправлен", "Pedal mode command sent")
                                    } else message = lang.t("Команда не отправлена", "Command was not sent")
                                },
                                enabled = online && stopped,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = if (pedalMode == mode) V4Accent else Color(0xFF202630), contentColor = if (pedalMode == mode) Color.Black else Color.White),
                                contentPadding = PaddingValues(horizontal = 5.dp, vertical = 11.dp),
                                shape = RoundedCornerShape(14.dp),
                            ) { Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = V4Surface), shape = RoundedCornerShape(22.dp)) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(lang.t("Сбросить Trip", "Reset trip"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(lang.t("Обнулит счётчик поездки в колесе", "Clears the wheel trip counter"), color = V4Muted, fontSize = 9.sp)
                    }
                    TextButton(onClick = { confirmReset = true }, enabled = online && stopped) { Text(lang.t("СБРОС", "RESET"), color = V4Amber, fontWeight = FontWeight.Black) }
                }
            }
        }
        item {
            AnimatedVisibility(visible = message != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF142019)), shape = RoundedCornerShape(16.dp)) {
                    Text(message ?: "", color = V4Good, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(13.dp))
                }
            }
        }
        item { V4Empty(lang.t("Дальше сюда перенесём подтверждённые настройки WheelLog: алармы, дополнительные параметры Veteran и управление колесом — без копирования старого интерфейса.", "More verified WheelLog-style controls will land here: alarms, Veteran parameters and wheel controls, without copying the old UI.")) }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = V4Surface2,
            title = { Text(lang.t("Сбросить Trip?", "Reset trip?"), color = Color.White) },
            text = { Text(lang.t("Это отправит команду CLEARMETER в колесо.", "This sends CLEARMETER to the wheel."), color = Color(0xFFD4DAE4)) },
            confirmButton = {
                TextButton(onClick = {
                    message = if (ble.resetTrip()) lang.t("Trip сброшен", "Trip reset") else lang.t("Команда не отправлена", "Command was not sent")
                    confirmReset = false
                }) { Text(lang.t("СБРОСИТЬ", "RESET"), color = V4Amber) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(lang.t("ОТМЕНА", "CANCEL"), color = V4Muted) } },
        )
    }
}

@Composable
private fun V4Rides(lang: V4Language) {
    val context = LocalContext.current
    val recording by WheelRepository.recording.collectAsState()
    val lastLog by WheelRepository.lastLogPath.collectAsState()
    val files = remember(recording, lastLog) { RideLogReader.listRides(context.filesDir) }
    var selected by remember { mutableStateOf<String?>(null) }
    val ride = remember(selected, recording, lastLog) { selected?.let { runCatching { RideLogReader.read(File(it)) }.getOrNull() } }
    if (ride != null) {
        V4Replay(ride, lang) { selected = null }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Spacer(Modifier.height(28.dp)) }
        item { V4Section(lang.t("Поездки", "Rides"), lang.t("Записанные CSV можно открыть и прокрутить", "Open and replay recorded CSV logs")) }
        if (files.isEmpty()) item { V4Empty(lang.t("Пока нет записанных поездок.", "No recorded rides yet.")) }
        else items(files, key = { it.absolutePath }) { file ->
            val itemRide = remember(file.absolutePath, file.length()) { runCatching { RideLogReader.read(file) }.getOrNull() }
            Card(modifier = Modifier.fillMaxWidth().clickable { selected = file.absolutePath }, colors = CardDefaults.cardColors(containerColor = V4Surface), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(SimpleDateFormat("dd.MM.yyyy · HH:mm", Locale.getDefault()).format(Date(file.lastModified())), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(file.name, color = V4Muted, fontSize = 9.sp)
                        }
                        Text("›", color = V4Accent, fontSize = 26.sp)
                    }
                    itemRide?.takeIf { it.samples.isNotEmpty() }?.let {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            V4Mini(lang.t("Макс.", "Max"), String.format(Locale.getDefault(), "%.1f km/h", it.maxSpeedKmh))
                            V4Mini("PWM", String.format(Locale.getDefault(), "%.0f%%", it.maxPwm))
                            V4Mini(lang.t("Мин. V", "Min V"), String.format(Locale.getDefault(), "%.1f V", it.minVoltageV))
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun V4Replay(ride: RideLog, lang: V4Language, back: () -> Unit) {
    val samples = ride.samples
    val last = (samples.size - 1).coerceAtLeast(0)
    var pos by remember(ride.file.absolutePath) { mutableStateOf(0f) }
    var playing by remember(ride.file.absolutePath) { mutableStateOf(false) }
    LaunchedEffect(playing, ride.file.absolutePath) {
        while (playing && pos.roundToInt() < last) {
            delay(100)
            pos = (pos + 1f).coerceAtMost(last.toFloat())
        }
        if (pos.roundToInt() >= last) playing = false
    }
    val sample = samples.getOrNull(pos.roundToInt().coerceIn(0, last))
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Spacer(Modifier.height(24.dp)) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = back) { Text("‹ ${lang.t("Назад", "Back")}", color = V4Accent) }
                Text(lang.t("Воспроизведение", "Ride replay"), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
            }
        }
        if (samples.isEmpty()) item { V4Empty(lang.t("В CSV нет читаемых точек.", "No readable samples in this CSV.")) }
        else {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = V4Surface), shape = RoundedCornerShape(26.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Text(lang.t("СКОРОСТЬ", "SPEED"), color = V4Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(String.format(Locale.getDefault(), "%.1f", sample?.speedKmh?.absoluteValue ?: 0f), color = Color.White, fontSize = 58.sp, fontWeight = FontWeight.Black)
                            Text(" km/h", color = V4Muted, modifier = Modifier.padding(bottom = 8.dp))
                        }
                        Text("PWM ${String.format(Locale.getDefault(), "%.0f", sample?.pwmPercent ?: 0f)}%", color = v4PwmColor(sample?.pwmPercent ?: 0f), fontSize = 18.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            item {
                Slider(value = pos.coerceIn(0f, last.toFloat().coerceAtLeast(1f)), onValueChange = { pos = it; playing = false }, valueRange = 0f..last.toFloat().coerceAtLeast(1f), enabled = last > 0)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${pos.roundToInt() + 1}/${samples.size}", color = V4Muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                    Button(onClick = { if (pos.roundToInt() >= last) pos = 0f; playing = !playing }, colors = ButtonDefaults.buttonColors(containerColor = V4Accent, contentColor = Color.Black), shape = RoundedCornerShape(14.dp)) {
                        Text(if (playing) lang.t("ПАУЗА", "PAUSE") else lang.t("ВОСПРОИЗВЕСТИ", "PLAY"), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    V4ReplayMetric(lang.t("НАПРЯЖЕНИЕ", "VOLTAGE"), sample?.let { String.format(Locale.getDefault(), "%.1f V", it.voltageV) } ?: "—", Modifier.weight(1f))
                    V4ReplayMetric(lang.t("ФАЗНЫЙ ТОК", "PHASE CURRENT"), sample?.let { String.format(Locale.getDefault(), "%.1f A", it.phaseCurrentA) } ?: "—", Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    V4ReplayMetric(lang.t("ТЕМП. MOSFET", "MOSFET TEMP"), sample?.let { String.format(Locale.getDefault(), "%.1f °C", it.mosfetTempC) } ?: "—", Modifier.weight(1f))
                    V4ReplayMetric(lang.t("УГОЛ", "PITCH"), sample?.let { String.format(Locale.getDefault(), "%.2f°", it.pitchDeg) } ?: "—", Modifier.weight(1f))
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun V4ReplayMetric(label: String, value: String, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = V4Surface2), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(15.dp)) {
            Text(label, color = V4Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(value, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun V4Settings(lang: V4Language, debug: Boolean, onLang: (V4Language) -> Unit, onDebug: (Boolean) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Spacer(Modifier.height(28.dp)) }
        item { V4Section(lang.t("Настройки", "Settings"), "EUC Lab · v0.0.4") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = V4Surface), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(17.dp)) {
                    Text(lang.t("ЯЗЫК", "LANGUAGE"), color = V4Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        V4LangButton("Русский", lang == V4Language.RU, Modifier.weight(1f)) { onLang(V4Language.RU) }
                        V4LangButton("English", lang == V4Language.EN, Modifier.weight(1f)) { onLang(V4Language.EN) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(lang.t("По умолчанию русский. PWM и km/h не переводятся.", "Russian is the default. PWM and km/h stay unchanged."), color = V4Muted, fontSize = 9.sp)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = V4Surface), shape = RoundedCornerShape(22.dp)) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(lang.t("RAW BLE / ОТЛАДКА", "RAW BLE / DEBUG"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(lang.t("Показывать сырые пакеты на главной", "Show raw BLE packets on Home"), color = V4Muted, fontSize = 9.sp)
                    }
                    Switch(checked = debug, onCheckedChange = onDebug, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = V4Accent))
                }
            }
        }
        item { V4Empty(lang.t("Следующий большой блок: GPS-карта, авто-лог, алармы, Black Box и SOS.", "Next major block: GPS map, auto logging, alarms, Black Box and SOS.")) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun V4LangButton(text: String, selected: Boolean, modifier: Modifier, click: () -> Unit) {
    Button(onClick = click, modifier = modifier, colors = ButtonDefaults.buttonColors(containerColor = if (selected) V4Accent else Color(0xFF202630), contentColor = if (selected) Color.Black else Color.White), shape = RoundedCornerShape(14.dp)) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}

@Composable
private fun V4Section(title: String, subtitle: String) {
    Column {
        Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = V4Muted, fontSize = 10.sp)
    }
}

@Composable
private fun V4Mini(label: String, value: String) {
    Column {
        Text(label, color = V4Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color(0xFFDDE3EC), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun V4Empty(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF10141A)), shape = RoundedCornerShape(20.dp)) {
        Text(text, color = V4Muted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.fillMaxWidth().padding(16.dp))
    }
}
