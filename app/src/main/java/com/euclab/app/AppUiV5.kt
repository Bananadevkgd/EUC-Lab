package com.euclab.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import kotlin.math.abs
import kotlin.math.roundToInt

private val V5Bg = Color(0xFF090B0F)
private val V5Surface = Color(0xFF11151C)
private val V5Surface2 = Color(0xFF171C24)
private val V5Muted = Color(0xFF7C8798)
private val V5Accent = Color(0xFFB6FF35)
private val V5Good = Color(0xFF6DFF9A)
private val V5Danger = Color(0xFFFF5060)
private val V5Amber = Color(0xFFFFC857)
private val V5Blue = Color(0xFF79C7FF)

private enum class V5Language { RU, EN }
private enum class V5Screen { HOME, RIDES, BATTERY, WHEEL, SETTINGS }
private enum class HeroMode { DRIVE, PARKING, CHARGING }
private enum class V5Metric { BATTERY, VOLTAGE, PHASE, MOSFET, PITCH, PWM, TRIP, ODOMETER, FIRMWARE, CHARGING }

private fun V5Language.t(ru: String, en: String): String = if (this == V5Language.RU) ru else en

private fun V5Screen.title(lang: V5Language): String = when (this) {
    V5Screen.HOME -> lang.t("Главная", "Home")
    V5Screen.RIDES -> lang.t("Поездки", "Rides")
    V5Screen.BATTERY -> lang.t("Батарея", "Battery")
    V5Screen.WHEEL -> lang.t("Колесо", "Wheel")
    V5Screen.SETTINGS -> lang.t("Настройки", "Settings")
}

private fun V5Screen.icon(): String = when (this) {
    V5Screen.HOME -> "●"
    V5Screen.RIDES -> "≋"
    V5Screen.BATTERY -> "▦"
    V5Screen.WHEEL -> "◉"
    V5Screen.SETTINGS -> "⚙"
}

@Composable
fun AppThemeV5(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(background = V5Bg, surface = V5Surface, primary = V5Accent),
        content = { Surface(Modifier.fillMaxSize(), color = V5Bg) { content() } },
    )
}

@Composable
fun EucLabAppV5(ble: BleWheelManager) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("euc_lab_settings", Context.MODE_PRIVATE) }
    var lang by remember {
        mutableStateOf(
            runCatching { V5Language.valueOf(prefs.getString("language", V5Language.RU.name) ?: V5Language.RU.name) }
                .getOrDefault(V5Language.RU)
        )
    }
    var debug by remember { mutableStateOf(prefs.getBoolean("debug_enabled", false)) }
    var screen by remember { mutableStateOf(V5Screen.HOME) }

    Scaffold(
        containerColor = V5Bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0E1218), tonalElevation = 0.dp) {
                V5Screen.values().forEach { item ->
                    NavigationBarItem(
                        selected = screen == item,
                        onClick = { screen = item },
                        icon = { Text(item.icon(), fontSize = 15.sp, fontWeight = FontWeight.Black) },
                        label = { Text(item.title(lang), fontSize = 9.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = V5Accent,
                            indicatorColor = V5Accent,
                            unselectedIconColor = V5Muted,
                            unselectedTextColor = V5Muted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().background(V5Bg).padding(padding)) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    (fadeIn(tween(170)) + slideInVertically(tween(210)) { it / 20 }) togetherWith
                        (fadeOut(tween(120)) + slideOutVertically(tween(150)) { -it / 24 })
                },
                label = "v5_screen",
            ) { target ->
                when (target) {
                    V5Screen.HOME -> V5Home(ble, lang, debug) { screen = V5Screen.RIDES }
                    V5Screen.RIDES -> V5Rides(lang)
                    V5Screen.BATTERY -> V5Battery(lang)
                    V5Screen.WHEEL -> V7WheelScreen(ble, lang == V5Language.RU)
                    V5Screen.SETTINGS -> V5Settings(
                        ble = ble,
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
private fun V5Home(ble: BleWheelManager, lang: V5Language, debug: Boolean, openRides: () -> Unit) {
    val telemetry by WheelRepository.telemetry.collectAsState()
    val bms by WheelRepository.bms.collectAsState()
    val link by WheelRepository.linkState.collectAsState()
    val raw by WheelRepository.rawPacket.collectAsState()
    val recording by WheelRepository.recording.collectAsState()
    val lastLog by WheelRepository.lastLogPath.collectAsState()
    val candidates by ble.candidates.collectAsState()
    val lightOn by ble.lightOn.collectAsState()
    var showAll by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (ble.canScan() && ble.canConnect()) ble.startScan()
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
                }
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                }
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
        item { V5Header(telemetry, link, lang) }
        item { V5ContextHero(telemetry, bms, lang) }
        item { V5MetricGrid(telemetry, lang) }
        item { V5LightQuick(telemetry != null, lightOn, lang) { ble.setLight(!lightOn) } }
        if (debug) item { V5Debug(raw, lang) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = ::scan,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4A5361)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) {
                    Text(
                        when (link) {
                            LinkState.SCANNING -> lang.t("ПОИСК…", "SCANNING…")
                            LinkState.CONNECTED -> lang.t("СМЕНИТЬ КОЛЕСО", "CHANGE WHEEL")
                            else -> lang.t("ПОДКЛЮЧИТЬ", "CONNECT")
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
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
                        containerColor = if (recording) V5Danger else V5Accent,
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
                    Text(lang.t("НАЙДЕННЫЕ КОЛЁСА", "NEARBY EUC"), color = V5Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (candidates.size > euc.size) {
                        Text(
                            if (showAll) lang.t("ТОЛЬКО EUC", "EUC ONLY") else lang.t("ПОКАЗАТЬ ВСЕ", "SHOW ALL"),
                            color = V5Accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { showAll = !showAll },
                        )
                    }
                }
            }
            items(visible, key = { it.address }) { candidate -> V5Device(candidate) { ble.connect(candidate.address) } }
        }

        lastLog?.let { path ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = openRides),
                    colors = CardDefaults.cardColors(containerColor = V5Surface),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(lang.t("ПОСЛЕДНЯЯ ЗАПИСЬ", "LAST LOG"), color = V5Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(path.substringAfterLast('/'), color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(lang.t("ОТКРЫТЬ ›", "OPEN ›"), color = V5Accent, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun V5Header(t: Telemetry?, link: LinkState, lang: V5Language) {
    val online = t != null
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("EUC LAB", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("${t?.model ?: "Sherman L"} · v0.0.7", color = V5Muted, fontSize = 12.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            V5WheelModelBadge(t?.model, online)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (online) Color(0xFF173A26) else Color(0xFF222831))
                    .padding(horizontal = 13.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(99.dp)).background(if (online) V5Good else V5Muted))
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
}

@Composable
private fun V5WheelModelBadge(model: String?, online: Boolean) {
    val code = when {
        model?.contains("Sherman L", true) == true -> "SL"
        model?.contains("Sherman S", true) == true -> "SS"
        model?.contains("Sherman", true) == true -> "SH"
        model?.contains("Lynx S", true) == true -> "LS"
        model?.contains("Lynx", true) == true -> "LX"
        model?.contains("Patton S", true) == true -> "PS"
        model?.contains("Patton", true) == true -> "PT"
        model?.contains("Oryx", true) == true -> "OX"
        model?.contains("Apex", true) == true -> "AP"
        model?.contains("Aero", true) == true -> "AE"
        model?.contains("Aeon", true) == true -> "AN"
        else -> "EUC"
    }
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (online) Color(0xFF182218) else Color(0xFF181D24)),
        contentAlignment = Alignment.Center,
    ) {
        Text("◉", color = if (online) Color.White else V5Muted, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text(
            code,
            color = if (online) V5Accent else V5Muted,
            fontSize = 6.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp),
        )
    }
}

@Composable
private fun rememberParkingState(t: Telemetry?): Boolean {
    var parked by remember { mutableStateOf(false) }
    val candidate = t != null && !t.charging && abs(t.speedKmh) < 0.25f && abs(t.phaseCurrentA) < 0.35f && t.pwmPercent < 1.0f
    LaunchedEffect(candidate) {
        if (candidate) {
            delay(1300)
            parked = true
        } else parked = false
    }
    return parked
}

private fun v5PwmColor(pwm: Float): Color {
    val p = pwm.coerceIn(0f, 100f)
    return Color.hsv(120f * (1f - p / 100f), 0.78f, 1f)
}

private data class ChargeEstimate(val amps: Float, val watts: Float, val etaMinutes: Int?)

private fun chargeEstimate(t: Telemetry?, bms: BmsSnapshot?): ChargeEstimate {
    if (t == null || !t.charging) return ChargeEstimate(0f, 0f, null)
    val a1 = bms?.pack1?.currentA ?: 0f
    val a2 = bms?.pack2?.currentA ?: 0f
    val amps = abs(a1 + a2)
    val watts = t.voltageV * amps
    val capacityWh = if (t.model.contains("Sherman L", true)) 4000f else 0f
    val eta = if (capacityWh > 0f && watts > 40f && t.batteryPercent < 100) {
        val remainingWh = capacityWh * (100 - t.batteryPercent).coerceIn(0, 100) / 100f
        ((remainingWh / (watts * 0.92f)) * 60f * 1.08f).roundToInt().coerceAtLeast(1)
    } else null
    return ChargeEstimate(amps, watts, eta)
}

private fun formatEta(minutes: Int?, lang: V5Language): String {
    if (minutes == null) return lang.t("РАСЧЁТ…", "CALCULATING…")
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> lang.t("≈ ${h}ч ${m}м", "≈ ${h}h ${m}m")
        h > 0 -> lang.t("≈ ${h}ч", "≈ ${h}h")
        else -> lang.t("≈ ${m} мин", "≈ ${m} min")
    }
}

@Composable
private fun V5ContextHero(t: Telemetry?, bms: BmsSnapshot?, lang: V5Language) {
    val parked = rememberParkingState(t)
    val mode = when {
        t?.charging == true -> HeroMode.CHARGING
        parked -> HeroMode.PARKING
        else -> HeroMode.DRIVE
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = V5Surface),
        shape = RoundedCornerShape(30.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize(tween(300)),
    ) {
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                (fadeIn(tween(260)) + slideInVertically(tween(320)) { it / 3 }) togetherWith
                    (fadeOut(tween(170)) + slideOutVertically(tween(250)) { -it / 3 })
            },
            label = "hero_mode",
        ) { target ->
            when (target) {
                HeroMode.DRIVE -> V5DriveHero(t, lang)
                HeroMode.PARKING -> V5ParkingHero(t, lang)
                HeroMode.CHARGING -> V5ChargingHero(t, bms, lang)
            }
        }
    }
}

@Composable
private fun V5DriveHero(t: Telemetry?, lang: V5Language) {
    val speed = t?.speedKmh?.absoluteValue ?: 0f
    val pwm = t?.pwmPercent ?: 0f
    val color = v5PwmColor(pwm)
    Column(Modifier.padding(24.dp)) {
        Text(lang.t("СКОРОСТЬ", "SPEED"), color = V5Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(String.format(Locale.getDefault(), "%.1f", speed), color = Color.White, fontSize = 76.sp, lineHeight = 78.sp, fontWeight = FontWeight.Black)
            Text(" km/h", color = V5Muted, fontSize = 18.sp, modifier = Modifier.padding(bottom = 14.dp))
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

@Composable
private fun V5ParkingHero(t: Telemetry?, lang: V5Language) {
    Column(Modifier.padding(24.dp)) {
        Text(lang.t("СОСТОЯНИЕ КОЛЕСА", "WHEEL STATE"), color = V5Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("P", color = V5Blue, fontSize = 82.sp, lineHeight = 86.sp, fontWeight = FontWeight.Black)
            Column {
                Text(lang.t("ПАРКИНГ", "PARKING"), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text(lang.t("Мотор не создаёт тягу", "Motor torque is inactive"), color = V5Muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)).background(Color(0xFF24313A))) {
            Box(Modifier.fillMaxWidth().height(8.dp).background(V5Blue.copy(alpha = 0.55f)))
        }
        Spacer(Modifier.height(12.dp))
        Row {
            Text(lang.t("Можно перемещать колесо", "Wheel can be moved freely"), color = V5Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            t?.let { Text("${it.batteryPercent}% · ${String.format(Locale.getDefault(), "%.1f V", it.voltageV)}", color = Color(0xFFD9DEE7), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun V5ChargingHero(t: Telemetry?, bms: BmsSnapshot?, lang: V5Language) {
    val info = chargeEstimate(t, bms)
    val pct = t?.batteryPercent ?: 0
    Column(Modifier.padding(24.dp)) {
        Text(lang.t("ДО ПОЛНОЙ ЗАРЯДКИ", "UNTIL FULL"), color = V5Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(formatEta(info.etaMinutes, lang), color = V5Accent, fontSize = 48.sp, lineHeight = 54.sp, fontWeight = FontWeight.Black)
        Text(lang.t("Оценка по текущей мощности зарядки", "Estimate from current charging power"), color = V5Muted, fontSize = 11.sp)
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(99.dp)).background(Color(0xFF29303A))) {
            Box(Modifier.fillMaxWidth(pct.coerceIn(0, 100) / 100f).height(10.dp).background(V5Accent))
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⚡ ${if (info.watts > 20f) String.format(Locale.getDefault(), "%.0f W", info.watts) else "— W"}", color = V5Accent, fontSize = 19.sp, fontWeight = FontWeight.Black)
            Text("  ·  ${if (info.amps > 0.05f) String.format(Locale.getDefault(), "%.2f A", info.amps) else "— A"}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            t?.let { Text("${it.batteryPercent}% · ${String.format(Locale.getDefault(), "%.1f V", it.voltageV)}", color = Color(0xFFD9DEE7), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

private fun V5Metric.title(lang: V5Language): String = when (this) {
    V5Metric.BATTERY -> lang.t("БАТАРЕЯ", "BATTERY")
    V5Metric.VOLTAGE -> lang.t("НАПРЯЖЕНИЕ", "VOLTAGE")
    V5Metric.PHASE -> lang.t("ФАЗНЫЙ ТОК", "PHASE CURRENT")
    V5Metric.MOSFET -> lang.t("ТЕМП. MOSFET", "MOSFET TEMP")
    V5Metric.PITCH -> lang.t("УГОЛ", "PITCH")
    V5Metric.PWM -> "PWM"
    V5Metric.TRIP -> lang.t("ПРОБЕГ ПОЕЗДКИ", "TRIP")
    V5Metric.ODOMETER -> lang.t("ОДОМЕТР", "ODOMETER")
    V5Metric.FIRMWARE -> lang.t("ПРОШИВКА", "FIRMWARE")
    V5Metric.CHARGING -> lang.t("ЗАРЯДКА", "CHARGING")
}

private fun V5Metric.value(t: Telemetry?, lang: V5Language): String = when (this) {
    V5Metric.BATTERY -> t?.let { "${it.batteryPercent}%" } ?: "—"
    V5Metric.VOLTAGE -> t?.let { String.format(Locale.getDefault(), "%.1f V", it.voltageV) } ?: "—"
    V5Metric.PHASE -> t?.let { String.format(Locale.getDefault(), "%.1f A", it.phaseCurrentA) } ?: "—"
    V5Metric.MOSFET -> t?.let { String.format(Locale.getDefault(), "%.1f °C", it.mosfetTempC) } ?: "—"
    V5Metric.PITCH -> t?.let { String.format(Locale.getDefault(), "%.2f°", it.pitchDeg) } ?: "—"
    V5Metric.PWM -> t?.let { String.format(Locale.getDefault(), "%.0f %%", it.pwmPercent) } ?: "—"
    V5Metric.TRIP -> t?.let { String.format(Locale.getDefault(), "%.2f km", it.tripKm) } ?: "—"
    V5Metric.ODOMETER -> t?.let { String.format(Locale.getDefault(), "%.1f km", it.totalKm) } ?: "—"
    V5Metric.FIRMWARE -> t?.firmwareRaw?.toString() ?: "—"
    V5Metric.CHARGING -> t?.let { if (it.charging) lang.t("ДА", "YES") else lang.t("НЕТ", "NO") } ?: "—"
}

@Composable
private fun V5MetricGrid(t: Telemetry?, lang: V5Language) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("dashboard_metrics", Context.MODE_PRIVATE) }
    val defaults = listOf(V5Metric.BATTERY, V5Metric.VOLTAGE, V5Metric.PHASE, V5Metric.MOSFET)
    var selected by remember {
        mutableStateOf(defaults.mapIndexed { index, fallback ->
            runCatching { V5Metric.valueOf(prefs.getString("slot_$index", fallback.name) ?: fallback.name) }.getOrDefault(fallback)
        })
    }
    var picking by remember { mutableStateOf<Int?>(null) }

    Card(colors = CardDefaults.cardColors(containerColor = Color.Transparent), modifier = Modifier.fillMaxWidth().animateContentSize(tween(300))) {
        AnimatedContent(targetState = picking, label = "metric_picker") { slot ->
            if (slot == null) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        V5MetricTile(selected[0], t, lang, Modifier.weight(1f)) { picking = 0 }
                        V5MetricTile(selected[1], t, lang, Modifier.weight(1f)) { picking = 1 }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        V5MetricTile(selected[2], t, lang, Modifier.weight(1f)) { picking = 2 }
                        V5MetricTile(selected[3], t, lang, Modifier.weight(1f)) { picking = 3 }
                    }
                    Text(lang.t("Нажми на карточку — она развернётся", "Tap a card to change its metric"), color = Color(0xFF596373), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 2.dp))
                }
            } else {
                Card(colors = CardDefaults.cardColors(containerColor = V5Surface2), shape = RoundedCornerShape(26.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(lang.t("ЧТО ПОКАЗЫВАТЬ?", "CHOOSE METRIC"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                                Text(lang.t("Выбери показатель для этой карточки", "Choose what this card displays"), color = V5Muted, fontSize = 10.sp)
                            }
                            Text("×", color = V5Muted, fontSize = 24.sp, modifier = Modifier.clickable { picking = null }.padding(8.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        V5Metric.values().toList().chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                row.forEach { metric ->
                                    val active = metric == selected[slot]
                                    Card(
                                        modifier = Modifier.weight(1f).clickable {
                                            selected = selected.toMutableList().apply { this[slot] = metric }
                                            prefs.edit().putString("slot_$slot", metric.name).apply()
                                            picking = null
                                        },
                                        colors = CardDefaults.cardColors(containerColor = if (active) Color(0xFF263319) else Color(0xFF202630)),
                                        shape = RoundedCornerShape(15.dp),
                                    ) {
                                        Text(metric.title(lang), color = if (active) V5Accent else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp), maxLines = 1)
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun V5MetricTile(kind: V5Metric, t: Telemetry?, lang: V5Language, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = V5Surface2), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(17.dp)) {
            Text(kind.title(lang), color = V5Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text(kind.value(t, lang), color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun V5LightQuick(online: Boolean, lightOn: Boolean, lang: V5Language, toggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = online, onClick = toggle),
        colors = CardDefaults.cardColors(containerColor = if (lightOn) Color(0xFF243318) else Color(0xFF202630)),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (lightOn) "☀" else "◌", color = if (lightOn) V5Accent else Color.White, fontSize = 27.sp, modifier = Modifier.padding(end = 16.dp))
            Column(Modifier.weight(1f)) {
                Text(lang.t("ФАРА", "HEADLIGHT"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
                Text(
                    if (!online) lang.t("Сначала подключи колесо", "Connect the wheel first")
                    else if (lightOn) lang.t("Включена · нажми, чтобы выключить", "On · tap to switch off")
                    else lang.t("Выключена · нажми, чтобы включить", "Off · tap to switch on"),
                    color = V5Muted,
                    fontSize = 11.sp,
                )
            }
            Text(if (lightOn) "ON" else "OFF", color = if (lightOn) V5Accent else Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun V5Debug(raw: String, lang: V5Language) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1015)), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(15.dp)) {
            Text(lang.t("RAW BLE · ОТЛАДКА", "RAW BLE · DEBUG"), color = V5Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(raw, color = Color(0xFF8791A0), fontSize = 9.sp, lineHeight = 13.sp, minLines = 3, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun V5Device(candidate: BleCandidate, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (candidate.likelyEuc) Color(0xFF17241A) else Color(0xFF12161D)).clickable(onClick = onClick).padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(candidate.name, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(candidate.address, color = Color(0xFF687283), fontSize = 9.sp)
        }
        Text("${candidate.rssi} dBm", color = if (candidate.likelyEuc) V5Good else V5Muted, fontSize = 10.sp)
    }
}

@Composable
private fun V5Rides(lang: V5Language) {
    val context = LocalContext.current
    val telemetry by WheelRepository.telemetry.collectAsState()
    val recording by WheelRepository.recording.collectAsState()
    val lastLog by WheelRepository.lastLogPath.collectAsState()
    var selected by remember { mutableStateOf<File?>(null) }
    var expanded by remember { mutableStateOf<File?>(null) }
    val files = remember(lastLog, recording) { RideLogReader.listRides(context.filesDir) }

    expanded?.let { file ->
        V7RideDetail(RideLogReader.read(file), lang == V5Language.RU) { expanded = null }
        return
    }

    selected?.let { file ->
        Box(Modifier.fillMaxSize()) {
            V5Replay(RideLogReader.read(file), lang) { selected = null }
            Button(
                onClick = { expanded = file },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = V5Accent, contentColor = Color.Black),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(lang.t("ОТКРЫТЬ РАСШИРЕННЫЙ ЛОГ", "OPEN EXPANDED LOG"), fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Spacer(Modifier.height(28.dp)) }
        item { V5SectionHeader(lang.t("Мои поездки", "My rides"), lang.t("Логи, воспроизведение и живая сессия", "Logs, replay and live session")) }
        item { V5LiveSessionCard(telemetry, recording, lang) }
        if (files.isEmpty()) {
            item { V5Empty(lang.t("Записей пока нет. Нажми «Начать лог» на главной, чтобы создать первую поездку.", "No rides yet. Start a log from Home to create the first one.")) }
        } else {
            items(files, key = { it.absolutePath }) { file ->
                val ride = remember(file.lastModified()) { RideLogReader.read(file) }
                Card(modifier = Modifier.fillMaxWidth().clickable { selected = file }, colors = CardDefaults.cardColors(containerColor = V5Surface), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(SimpleDateFormat("dd.MM.yyyy · HH:mm", Locale.getDefault()).format(Date(file.lastModified())), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(7.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            V5Mini(lang.t("МАКС. СКОРОСТЬ", "MAX SPEED"), String.format(Locale.getDefault(), "%.1f km/h", ride.maxSpeedKmh))
                            V5Mini("MAX PWM", String.format(Locale.getDefault(), "%.0f%%", ride.maxPwm))
                            V5Mini(lang.t("ТОЧЕК", "SAMPLES"), ride.samples.size.toString())
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(file.name, color = V5Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun V5LiveSessionCard(t: Telemetry?, recording: Boolean, lang: V5Language) {
    Card(colors = CardDefaults.cardColors(containerColor = if (t != null) Color(0xFF102019) else V5Surface), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (t != null) lang.t("● ТЕЛЕМЕТРИЯ ОНЛАЙН", "● TELEMETRY ONLINE") else lang.t("● НЕТ ПОДКЛЮЧЕНИЯ", "● OFFLINE"), color = if (t != null) V5Good else V5Muted, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Text(if (recording) lang.t("● ЛОГ ПИШЕТСЯ", "● LOGGING") else lang.t("Лог выключен", "Log off"), color = if (recording) V5Amber else V5Muted, fontSize = 10.sp)
            }
            Spacer(Modifier.height(10.dp))
            if (t != null) {
                Text("${t.model} · ${t.batteryPercent}% · ${String.format(Locale.getDefault(), "%.1f V", t.voltageV)}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("${String.format(Locale.getDefault(), "%.1f km/h", t.speedKmh.absoluteValue)} · PWM ${String.format(Locale.getDefault(), "%.0f%%", t.pwmPercent)} · ${String.format(Locale.getDefault(), "%.1f°C", t.mosfetTempC)}", color = V5Muted, fontSize = 11.sp)
            } else Text(lang.t("Подключи колесо на главной — здесь появится живая сессия.", "Connect a wheel on Home to see the live session here."), color = V5Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun V5Replay(ride: RideLog, lang: V5Language, back: () -> Unit) {
    val samples = ride.samples
    var pos by remember(ride.file.absolutePath) { mutableStateOf(0f) }
    var playing by remember(ride.file.absolutePath) { mutableStateOf(false) }
    val last = (samples.size - 1).coerceAtLeast(0)
    LaunchedEffect(playing, ride.file.absolutePath) {
        while (playing && pos.roundToInt() < last) {
            delay(100)
            pos = (pos + 1f).coerceAtMost(last.toFloat())
        }
        if (pos.roundToInt() >= last) playing = false
    }
    val sample = samples.getOrNull(pos.roundToInt().coerceIn(0, last))

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Spacer(Modifier.height(24.dp)) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = back) { Text("‹ ${lang.t("Назад", "Back")}", color = V5Accent) }
                Column(Modifier.weight(1f)) {
                    Text(lang.t("Воспроизведение", "Ride replay"), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text(ride.file.name, color = V5Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (samples.isEmpty()) item { V5Empty(lang.t("В CSV нет читаемой телеметрии.", "No readable telemetry in this CSV.")) }
        else {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = V5Surface), shape = RoundedCornerShape(26.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Text(lang.t("СКОРОСТЬ", "SPEED"), color = V5Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(String.format(Locale.getDefault(), "%.1f", sample?.speedKmh?.absoluteValue ?: 0f), color = Color.White, fontSize = 58.sp, fontWeight = FontWeight.Black)
                            Text(" km/h", color = V5Muted, fontSize = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
                        }
                        Text("PWM ${String.format(Locale.getDefault(), "%.0f", sample?.pwmPercent ?: 0f)}%", color = v5PwmColor(sample?.pwmPercent ?: 0f), fontSize = 18.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            item {
                Slider(value = pos.coerceIn(0f, last.toFloat().coerceAtLeast(1f)), onValueChange = { pos = it; playing = false }, valueRange = 0f..last.toFloat().coerceAtLeast(1f), enabled = last > 0)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${pos.roundToInt() + 1}/${samples.size}", color = V5Muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                    Button(onClick = { if (pos.roundToInt() >= last) pos = 0f; playing = !playing }, colors = ButtonDefaults.buttonColors(containerColor = V5Accent, contentColor = Color.Black), shape = RoundedCornerShape(14.dp)) {
                        Text(if (playing) lang.t("ПАУЗА", "PAUSE") else lang.t("ВОСПРОИЗВЕСТИ", "PLAY"), fontWeight = FontWeight.Black, fontSize = 10.sp)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    V5ReplayMetric(lang.t("НАПРЯЖЕНИЕ", "VOLTAGE"), sample?.let { String.format(Locale.getDefault(), "%.1f V", it.voltageV) } ?: "—", Modifier.weight(1f))
                    V5ReplayMetric(lang.t("ФАЗНЫЙ ТОК", "PHASE CURRENT"), sample?.let { String.format(Locale.getDefault(), "%.1f A", it.phaseCurrentA) } ?: "—", Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    V5ReplayMetric(lang.t("ТЕМП. MOSFET", "MOSFET TEMP"), sample?.let { String.format(Locale.getDefault(), "%.1f °C", it.mosfetTempC) } ?: "—", Modifier.weight(1f))
                    V5ReplayMetric(lang.t("УГОЛ", "PITCH"), sample?.let { String.format(Locale.getDefault(), "%.2f°", it.pitchDeg) } ?: "—", Modifier.weight(1f))
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun V5ReplayMetric(label: String, value: String, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = V5Surface2), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(15.dp)) {
            Text(label, color = V5Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private data class BatteryAdvice(val title: String, val body: String, val color: Color)

private fun batteryAdvice(bms: BmsSnapshot?, lang: V5Language): BatteryAdvice {
    if (bms == null) return BatteryAdvice(lang.t("Жду данные Smart BMS", "Waiting for Smart BMS"), lang.t("После подключения нужно несколько секунд, чтобы получить все страницы обеих батарей.", "A few seconds are needed to receive all pages from both battery packs."), V5Blue)
    val packs = listOf(bms.pack1, bms.pack2)
    if (packs.any { !it.complete }) return BatteryAdvice(lang.t("Данные батареи ещё собираются", "Battery data is still loading"), lang.t("Не все 36 ячеек одного из паков получены. Подожди несколько секунд.", "Not all 36 cells have arrived yet. Wait a few seconds."), V5Blue)
    val maxDelta = packs.mapNotNull { it.deltaV }.maxOrNull() ?: 0f
    val temps = packs.flatMap { it.temperaturesC }.filter { it in -40f..120f && it != 0f }
    val minTemp = temps.minOrNull()
    val maxTemp = temps.maxOrNull()
    val minCell = packs.mapNotNull { it.minCellV }.minOrNull()
    val maxCell = packs.mapNotNull { it.maxCellV }.maxOrNull()
    return when {
        minCell != null && minCell < 2.8f -> BatteryAdvice(lang.t("Проверь батарею перед поездкой", "Check the battery before riding"), lang.t("Одна из ячеек имеет очень низкое напряжение (${String.format(Locale.getDefault(), "%.3f V", minCell)}).", "A cell voltage is very low (${String.format(Locale.getDefault(), "%.3f V", minCell)})."), V5Danger)
        maxCell != null && maxCell > 4.23f -> BatteryAdvice(lang.t("Высокое напряжение ячейки", "High cell voltage"), lang.t("Максимальная ячейка ${String.format(Locale.getDefault(), "%.3f V", maxCell)}. Проверь показания после окончания зарядки.", "Highest cell is ${String.format(Locale.getDefault(), "%.3f V", maxCell)}. Recheck after charging."), V5Danger)
        maxDelta > 0.050f -> BatteryAdvice(lang.t("Стоит проверить баланс ячеек", "Cell balance needs attention"), lang.t("Разброс достиг ${String.format(Locale.getDefault(), "%.0f mV", maxDelta * 1000f)}. Это заметно выше обычного.", "Cell spread is ${String.format(Locale.getDefault(), "%.0f mV", maxDelta * 1000f)}, which is noticeably elevated."), V5Danger)
        minTemp != null && minTemp < 0f -> BatteryAdvice(lang.t("Батарея очень холодная", "Battery is very cold"), lang.t("Минимальная температура ${String.format(Locale.getDefault(), "%.1f°C", minTemp)}. На холоде доступная мощность и допустимый зарядный ток снижаются.", "Minimum temperature is ${String.format(Locale.getDefault(), "%.1f°C", minTemp)}. Available power and safe charging current are reduced in the cold."), V5Amber)
        maxTemp != null && maxTemp > 50f -> BatteryAdvice(lang.t("Батарея горячая", "Battery is hot"), lang.t("Максимальная температура ${String.format(Locale.getDefault(), "%.1f°C", maxTemp)}. Дай батарее остыть.", "Maximum temperature is ${String.format(Locale.getDefault(), "%.1f°C", maxTemp)}. Let the battery cool down."), V5Amber)
        maxDelta > 0.030f -> BatteryAdvice(lang.t("Состояние хорошее, но следим за балансом", "Battery is good, watch the balance"), lang.t("Разброс ${String.format(Locale.getDefault(), "%.0f mV", maxDelta * 1000f)} — пока допустимо, но стоит наблюдать динамику.", "Cell spread is ${String.format(Locale.getDefault(), "%.0f mV", maxDelta * 1000f)}. Acceptable for now, but worth monitoring."), V5Amber)
        minTemp != null && minTemp < 5f -> BatteryAdvice(lang.t("Батарея холодная", "Battery is cold"), lang.t("Температура около ${String.format(Locale.getDefault(), "%.1f°C", minTemp)}. До прогрева избегай максимальной нагрузки.", "Temperature is around ${String.format(Locale.getDefault(), "%.1f°C", minTemp)}. Avoid maximum load until it warms up."), V5Amber)
        else -> BatteryAdvice(lang.t("Батарея в отличном состоянии", "Battery is in excellent condition"), lang.t("Обе BMS получены полностью, максимальный разброс ${String.format(Locale.getDefault(), "%.0f mV", maxDelta * 1000f)}. По текущим данным обслуживание не требуется.", "Both BMS packs are complete and maximum cell spread is ${String.format(Locale.getDefault(), "%.0f mV", maxDelta * 1000f)}. No maintenance is indicated by current data."), V5Good)
    }
}

@Composable
private fun V5Battery(lang: V5Language) {
    val bms by WheelRepository.bms.collectAsState()
    val telemetry by WheelRepository.telemetry.collectAsState()
    val advice = batteryAdvice(bms, lang)
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Spacer(Modifier.height(28.dp)) }
        item { V5SectionHeader("Smart BMS", lang.t("Два аккумуляторных пакета Sherman L", "Sherman L dual battery packs")) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = advice.color.copy(alpha = 0.10f)), shape = RoundedCornerShape(22.dp), modifier = Modifier.border(1.dp, advice.color.copy(alpha = 0.32f), RoundedCornerShape(22.dp))) {
                Column(Modifier.padding(17.dp)) {
                    Text(lang.t("✦ AI-ПОДСКАЗКА · ЛОКАЛЬНАЯ ДИАГНОСТИКА", "✦ AI TIP · LOCAL DIAGNOSTICS"), color = advice.color, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(7.dp))
                    Text(advice.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(4.dp))
                    Text(advice.body, color = Color(0xFFD0D6DF), fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = V5Surface), shape = RoundedCornerShape(22.dp)) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(telemetry?.model ?: "Sherman L", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(lang.t("Общее напряжение колеса", "Wheel voltage"), color = V5Muted, fontSize = 10.sp)
                    }
                    Text(telemetry?.let { "${it.batteryPercent}% · ${String.format(Locale.getDefault(), "%.1f V", it.voltageV)}" } ?: "—", color = V5Accent, fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        if (bms == null) item { V5Empty(lang.t("Жду страницы Smart BMS от колеса.", "Waiting for Smart BMS pages from the wheel.")) }
        else {
            item { V5BmsPack(bms!!.pack1, lang) }
            item { V5BmsPack(bms!!.pack2, lang) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun V5BmsPack(pack: BmsPack, lang: V5Language) {
    val min = pack.minCellV
    val max = pack.maxCellV
    Card(colors = CardDefaults.cardColors(containerColor = V5Surface), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("BMS ${pack.index}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text("${pack.validCells.size}/36 ${lang.t("ячеек получено", "cells received")}", color = V5Muted, fontSize = 10.sp)
                }
                Text(pack.deltaV?.let { "Δ ${String.format(Locale.getDefault(), "%.0f mV", it * 1000f)}" } ?: "Δ —", color = if ((pack.deltaV ?: 0f) > 0.03f) V5Danger else V5Good, fontSize = 14.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                V5Mini(lang.t("Мин.", "Min"), min?.let { String.format(Locale.getDefault(), "%.3f V", it) } ?: "—")
                V5Mini(lang.t("Макс.", "Max"), max?.let { String.format(Locale.getDefault(), "%.3f V", it) } ?: "—")
                V5Mini(lang.t("Сред.", "Avg"), pack.avgCellV?.let { String.format(Locale.getDefault(), "%.3f V", it) } ?: "—")
                V5Mini(lang.t("Ток", "Current"), pack.currentA?.let { String.format(Locale.getDefault(), "%.2f A", it) } ?: "—")
            }
            Spacer(Modifier.height(16.dp))
            Text(lang.t("ЯЧЕЙКИ", "CELLS"), color = V5Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            pack.cells.chunked(6).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEachIndexed { colIndex, value ->
                        val cellIndex = rowIndex * 6 + colIndex + 1
                        val isMin = value > 1f && min != null && abs(value - min) < 0.0005f
                        val isMax = value > 1f && max != null && abs(value - max) < 0.0005f
                        Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFF1B212A)), shape = RoundedCornerShape(10.dp)) {
                            Column(Modifier.padding(vertical = 7.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(cellIndex.toString(), color = Color(0xFF687384), fontSize = 8.sp)
                                Text(if (value > 1f) String.format(Locale.getDefault(), "%.3f", value) else "—", color = when { isMin -> V5Danger; isMax -> V5Good; else -> Color(0xFFE3E8EF) }, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
            }
            val temps = pack.temperaturesC.filter { it in -40f..120f && it != 0f }
            if (temps.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(lang.t("Температуры: ", "Temperatures: ") + temps.joinToString(" · ") { String.format(Locale.getDefault(), "%.1f°C", it) }, color = V5Muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun V5Wheel(ble: BleWheelManager, lang: V5Language) {
    val telemetry by WheelRepository.telemetry.collectAsState()
    val lightOn by ble.lightOn.collectAsState()
    val online = telemetry != null
    val stopped = (telemetry?.speedKmh?.absoluteValue ?: 0f) < 1f
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("wheel_controls", Context.MODE_PRIVATE) }
    var result by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var keyTone by remember {
        mutableFloatStateOf((telemetry?.keyTonePercent ?: prefs.getInt("key_tone_percent", 70)).coerceIn(0, 100).toFloat())
    }
    var draggingTone by remember { mutableStateOf(false) }

    LaunchedEffect(telemetry?.keyTonePercent, draggingTone) {
        val reported = telemetry?.keyTonePercent
        if (!draggingTone && reported != null) {
            keyTone = reported.coerceIn(0, 100).toFloat()
            prefs.edit().putInt("key_tone_percent", reported.coerceIn(0, 100)).apply()
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = V5Surface2,
            titleContentColor = Color.White,
            textContentColor = Color(0xFFD4DAE3),
            title = { Text(lang.t("Сбросить пробег поездки?", "Reset trip distance?"), fontWeight = FontWeight.Black) },
            text = {
                Text(
                    lang.t(
                        "Вы точно уверены, что хотите сбросить пробег текущей поездки на колесе? Это действие нельзя отменить.",
                        "Are you sure you want to reset the current trip distance on the wheel? This cannot be undone.",
                    ),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    result = if (ble.resetTrip()) lang.t("Пробег поездки сброшен", "Trip distance reset") else lang.t("Команда не отправлена", "Command failed")
                }) {
                    Text(lang.t("СБРОСИТЬ", "RESET"), color = V5Danger, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(lang.t("ОТМЕНА", "CANCEL"), color = V5Muted, fontWeight = FontWeight.Bold)
                }
            },
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Spacer(Modifier.height(28.dp)) }
        item { V5SectionHeader(lang.t("Колесо", "Wheel"), telemetry?.model ?: lang.t("Управление Veteran / LeaperKim", "Veteran / LeaperKim controls")) }
        item {
            V5LightQuick(online, lightOn, lang) {
                result = if (ble.setLight(!lightOn)) lang.t("Команда фары отправлена", "Headlight command sent") else lang.t("Не удалось отправить команду", "Command failed")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { result = if (ble.beep()) lang.t("Команда сигнала отправлена", "Horn command sent") else lang.t("Команда не отправлена", "Command failed") },
                    enabled = online,
                    modifier = Modifier.weight(0.8f),
                    colors = ButtonDefaults.buttonColors(containerColor = V5Surface2, contentColor = Color.White),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(lang.t("СИГНАЛ", "BEEP"), fontWeight = FontWeight.Black, fontSize = 10.sp)
                }
                Button(
                    onClick = { if (stopped) confirmReset = true },
                    enabled = online && stopped,
                    modifier = Modifier.weight(1.2f),
                    colors = ButtonDefaults.buttonColors(containerColor = V5Surface2, contentColor = Color.White),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 11.dp),
                ) {
                    Text(lang.t("СБРОСИТЬ ПРОБЕГ ПОЕЗДКИ", "RESET TRIP DISTANCE"), fontWeight = FontWeight.Black, fontSize = 9.sp, maxLines = 2)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = V5Surface), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(17.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(lang.t("ГРОМКОСТЬ КНОПОК", "KEY TONE VOLUME"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                            Text(lang.t("Звук нажатий и клавиш самого колеса", "Sounds made by the wheel's buttons and keys"), color = V5Muted, fontSize = 10.sp)
                        }
                        Text("${keyTone.roundToInt()}%", color = V5Accent, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = keyTone,
                        onValueChange = { draggingTone = true; keyTone = it },
                        onValueChangeFinished = {
                            val value = keyTone.roundToInt().coerceIn(0, 100)
                            prefs.edit().putInt("key_tone_percent", value).apply()
                            result = if (ble.setKeyToneVolume(value)) lang.t("Громкость кнопок: $value%", "Key tone volume: $value%") else lang.t("Команда громкости не отправлена", "Volume command failed")
                            draggingTone = false
                        },
                        valueRange = 0f..100f,
                        enabled = online,
                        colors = SliderDefaults.colors(thumbColor = V5Accent, activeTrackColor = V5Accent, inactiveTrackColor = Color(0xFF303743)),
                    )
                    Text(
                        lang.t(
                            "ⓘ Этот ползунок не влияет на предупреждающие сигналы по PWM, скорости и другим аварийным событиям — только на звуки кнопок.",
                            "ⓘ This slider does not affect PWM, speed or other safety warning alarms — only key/button tones.",
                        ),
                        color = V5Amber,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = V5Surface), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(17.dp)) {
                    Text(lang.t("РЕЖИМ ПЕДАЛЕЙ", "PEDAL MODE"), color = V5Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to lang.t("ЖЁСТКИЙ", "HARD"), 1 to lang.t("СРЕДНИЙ", "MEDIUM"), 2 to lang.t("МЯГКИЙ", "SOFT")).forEach { (mode, title) ->
                            OutlinedButton(
                                onClick = { if (stopped) result = if (ble.setPedalMode(mode)) lang.t("Режим отправлен", "Mode sent") else lang.t("Команда не отправлена", "Command failed") },
                                enabled = online && stopped,
                                modifier = Modifier.weight(1f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3A424E)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                            ) {
                                Text(title, fontSize = 8.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (stopped) lang.t("Настройки доступны только при остановке.", "Settings are available only while stopped.")
                        else lang.t("Остановись, чтобы менять настройки колеса.", "Stop before changing wheel settings."),
                        color = V5Muted,
                        fontSize = 9.sp,
                    )
                }
            }
        }
        result?.let { text -> item { V5Empty(text) } }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun V5Settings(ble: BleWheelManager, lang: V5Language, debug: Boolean, onLang: (V5Language) -> Unit, onDebug: (Boolean) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Spacer(Modifier.height(28.dp)) }
        item { V5SectionHeader(lang.t("Настройки", "Settings"), "EUC Lab · v0.0.7") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = V5Surface), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(17.dp)) {
                    Text(lang.t("ЯЗЫК", "LANGUAGE"), color = V5Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        V5LanguageButton("Русский", lang == V5Language.RU, Modifier.weight(1f)) { onLang(V5Language.RU) }
                        V5LanguageButton("English", lang == V5Language.EN, Modifier.weight(1f)) { onLang(V5Language.EN) }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = V5Surface), shape = RoundedCornerShape(22.dp)) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(lang.t("RAW BLE / ОТЛАДКА", "RAW BLE / DEBUG"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(lang.t("Нужно для поиска новых полей протокола, включая точный флаг паркинга", "Useful for discovering protocol fields, including the exact parking flag"), color = V5Muted, fontSize = 10.sp)
                    }
                    Switch(checked = debug, onCheckedChange = onDebug, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = V5Accent))
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF10141A)), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(17.dp)) {
                    Text(lang.t("ПАРКИНГ · BETA", "PARKING · BETA"), color = V5Blue, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(6.dp))
                    Text(lang.t("Сейчас режим P определяется по отсутствию скорости, PWM и моторного тока. После теста на Sherman L заменим это на точный флаг протокола, если он доступен в телеметрии.", "P mode is currently inferred from zero speed, PWM and motor current. After Sherman L testing we will switch to an exact protocol flag if one is present."), color = V5Muted, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                V7AppSettingsExtras(ble, lang == V5Language.RU)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun V5LanguageButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier, colors = ButtonDefaults.buttonColors(containerColor = if (selected) V5Accent else Color(0xFF202630), contentColor = if (selected) Color.Black else Color.White), shape = RoundedCornerShape(15.dp)) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}

@Composable
private fun V5SectionHeader(title: String, subtitle: String) {
    Column {
        Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = V5Muted, fontSize = 10.sp)
    }
}

@Composable
private fun V5Empty(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = V5Surface), shape = RoundedCornerShape(20.dp)) {
        Text(text, color = Color(0xFFD0D6DF), fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun V5Mini(label: String, value: String) {
    Column {
        Text(label, color = V5Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color(0xFFDDE3EC), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
