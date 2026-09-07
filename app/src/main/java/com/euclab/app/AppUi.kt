package com.euclab.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.euclab.app.data.BleCandidate
import com.euclab.app.data.BmsPack
import com.euclab.app.data.LinkState
import com.euclab.app.data.RideLog
import com.euclab.app.data.RideLogReader
import com.euclab.app.data.Telemetry
import com.euclab.app.data.WheelRepository
import com.euclab.app.service.RideRecorderService
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private val Bg = Color(0xFF090B0F)
private val Surface1 = Color(0xFF11151C)
private val Surface2 = Color(0xFF171C24)
private val Muted = Color(0xFF7C8798)
private val Accent = Color(0xFFB6FF35)
private val Good = Color(0xFF6DFF9A)
private val Danger = Color(0xFFFF5060)
private val Amber = Color(0xFFFFC857)

private enum class AppLanguage { RU, EN }
private enum class AppScreen { HOME, RIDES, BATTERY, SETTINGS }

private fun AppLanguage.t(ru: String, en: String): String = if (this == AppLanguage.RU) ru else en

private fun AppScreen.label(lang: AppLanguage): String = when (this) {
    AppScreen.HOME -> lang.t("Главная", "Home")
    AppScreen.RIDES -> lang.t("Поездки", "Rides")
    AppScreen.BATTERY -> lang.t("Батарея", "Battery")
    AppScreen.SETTINGS -> lang.t("Настройки", "Settings")
}

private fun AppScreen.glyph(): String = when (this) {
    AppScreen.HOME -> "●"
    AppScreen.RIDES -> "≋"
    AppScreen.BATTERY -> "▦"
    AppScreen.SETTINGS -> "⚙"
}

@Composable
fun EucLabApp(ble: BleWheelManager) {
    val context = LocalContext.current
    val settings = remember { context.getSharedPreferences("euc_lab_settings", Context.MODE_PRIVATE) }
    var language by remember {
        mutableStateOf(
            runCatching { AppLanguage.valueOf(settings.getString("language", AppLanguage.RU.name) ?: AppLanguage.RU.name) }
                .getOrDefault(AppLanguage.RU)
        )
    }
    var debugEnabled by remember { mutableStateOf(settings.getBoolean("debug_enabled", false)) }
    var screen by remember { mutableStateOf(AppScreen.HOME) }

    Scaffold(
        containerColor = Bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0E1218), tonalElevation = 0.dp) {
                AppScreen.values().forEach { item ->
                    NavigationBarItem(
                        selected = screen == item,
                        onClick = { screen = item },
                        icon = {
                            Text(
                                item.glyph(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black
                            )
                        },
                        label = { Text(item.label(language), fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = Accent,
                            indicatorColor = Accent,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted,
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(Bg)
                .padding(padding)
        ) {
            when (screen) {
                AppScreen.HOME -> HomeScreen(
                    ble = ble,
                    lang = language,
                    debugEnabled = debugEnabled,
                    onOpenRides = { screen = AppScreen.RIDES },
                )
                AppScreen.RIDES -> RidesScreen(language)
                AppScreen.BATTERY -> BatteryScreen(language)
                AppScreen.SETTINGS -> SettingsScreen(
                    lang = language,
                    debugEnabled = debugEnabled,
                    onLanguage = {
                        language = it
                        settings.edit().putString("language", it.name).apply()
                    },
                    onDebug = {
                        debugEnabled = it
                        settings.edit().putBoolean("debug_enabled", it).apply()
                    }
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    ble: BleWheelManager,
    lang: AppLanguage,
    debugEnabled: Boolean,
    onOpenRides: () -> Unit,
) {
    val telemetry by WheelRepository.telemetry.collectAsState()
    val linkState by WheelRepository.linkState.collectAsState()
    val linkMessage by WheelRepository.linkMessage.collectAsState()
    val rawPacket by WheelRepository.rawPacket.collectAsState()
    val recording by WheelRepository.recording.collectAsState()
    val lastLog by WheelRepository.lastLogPath.collectAsState()
    val candidates by ble.candidates.collectAsState()
    var showAllBle by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) ble.startScan()
    }

    fun scan() {
        showAllBle = false
        if (ble.canScan() && ble.canConnect()) {
            ble.startScan()
        } else {
            val permissions = buildList {
                if (Build.VERSION.SDK_INT >= 31) {
                    add(Manifest.permission.BLUETOOTH_SCAN)
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                } else {
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }.toTypedArray()
            permissionLauncher.launch(permissions)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(Modifier.height(28.dp)) }
        item { Header(telemetry, linkState, lang) }
        item { SpeedHero(telemetry, lang) }
        item { MetricGrid(telemetry, lang) }
        item { ConnectionCard(telemetry, linkState, linkMessage, recording, lang) }
        if (debugEnabled) item { DebugRawCard(rawPacket, lang) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { scan() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202630))
                ) {
                    Text(
                        when (linkState) {
                            LinkState.SCANNING -> lang.t("ПОИСК…", "SCANNING…")
                            LinkState.CONNECTED -> lang.t("СМЕНИТЬ КОЛЕСО", "CHANGE WHEEL")
                            else -> lang.t("ПОДКЛЮЧИТЬ", "CONNECT")
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                }
                Button(
                    onClick = {
                        if (recording) {
                            context.startService(
                                Intent(context, RideRecorderService::class.java)
                                    .setAction(RideRecorderService.ACTION_STOP)
                            )
                        } else {
                            ContextCompat.startForegroundService(
                                context,
                                Intent(context, RideRecorderService::class.java)
                                    .setAction(RideRecorderService.ACTION_START)
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (recording) Danger else Accent,
                        contentColor = Color.Black,
                    )
                ) {
                    Text(
                        if (recording) lang.t("СТОП ЛОГ", "STOP LOG") else lang.t("НАЧАТЬ ЛОГ", "START LOG"),
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        if (linkState == LinkState.SCANNING && candidates.isNotEmpty()) {
            val eucs = candidates.filter { it.likelyEuc }
            val visible = if (showAllBle) candidates else eucs
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        lang.t("НАЙДЕННЫЕ КОЛЁСА", "NEARBY EUC"),
                        modifier = Modifier.weight(1f),
                        color = Muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (candidates.size > eucs.size) {
                        Text(
                            if (showAllBle) lang.t("ТОЛЬКО EUC", "EUC ONLY") else lang.t("ПОКАЗАТЬ ВСЕ (${candidates.size})", "SHOW ALL (${candidates.size})"),
                            modifier = Modifier.clickable { showAllBle = !showAllBle },
                            color = Accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            if (visible.isEmpty()) {
                item {
                    Text(
                        lang.t("Пока не вижу устройство, похожее на EUC. Можно показать все BLE-устройства.", "No EUC-looking device yet. You can show all BLE devices."),
                        color = Muted,
                        fontSize = 12.sp,
                    )
                }
            } else {
                items(visible, key = { it.address }) { candidate ->
                    DeviceRow(candidate) { ble.connect(candidate.address) }
                }
            }
        }

        if (lastLog != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenRides),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF10141A)),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Row(
                        Modifier.padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(lang.t("ПОСЛЕДНЯЯ ЗАПИСЬ", "LAST LOG"), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(lastLog?.substringAfterLast('/') ?: "", color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(lang.t("ОТКРЫТЬ ›", "OPEN ›"), color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Header(t: Telemetry?, state: LinkState, lang: AppLanguage) {
    val online = t != null
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("EUC LAB", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
            Text("${t?.model ?: "Sherman L"} · v0.0.3", color = Muted, fontSize = 13.sp)
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (online) Color(0xFF173A26) else Color(0xFF222831))
                .padding(horizontal = 13.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (online) Good else Muted)
            )
            Text(
                if (online) lang.t("ОНЛАЙН", "ONLINE") else when (state) {
                    LinkState.SCANNING -> lang.t("ПОИСК", "SCAN")
                    LinkState.CONNECTING, LinkState.DISCOVERING -> lang.t("СВЯЗЬ…", "LINKING…")
                    LinkState.ERROR -> lang.t("ОШИБКА", "ERROR")
                    else -> lang.t("НЕ В СЕТИ", "OFFLINE")
                },
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SpeedHero(t: Telemetry?, lang: AppLanguage) {
    val speed = t?.speedKmh?.absoluteValue ?: 0f
    val pwm = t?.pwmPercent ?: 0f
    val pColor = pwmColor(pwm)

    Card(
        colors = CardDefaults.cardColors(containerColor = Surface1),
        shape = RoundedCornerShape(30.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(lang.t("СКОРОСТЬ", "SPEED"), color = Muted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    String.format(Locale.getDefault(), "%.1f", speed),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 76.sp,
                    lineHeight = 78.sp,
                )
                Text(" km/h", color = Muted, fontSize = 18.sp, modifier = Modifier.padding(bottom = 14.dp))
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0xFF2A303A))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((pwm.coerceIn(0f, 100f) / 100f))
                        .height(10.dp)
                        .background(pColor)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "PWM ${String.format(Locale.getDefault(), "%.0f", pwm)}%",
                    color = pColor,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f),
                )
                t?.let {
                    Text(
                        "${lang.t("Батарея", "Battery")} ${it.batteryPercent}%",
                        color = Color(0xFFD5DBE5),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

private fun pwmColor(pwm: Float): Color {
    val p = pwm.coerceIn(0f, 100f)
    val hue = 120f * (1f - p / 100f)
    return Color.hsv(hue, 0.78f, 1f)
}

private enum class MetricKind {
    BATTERY, VOLTAGE, PHASE, MOSFET, PITCH, PWM, TRIP, ODOMETER, FIRMWARE, CHARGING;

    fun title(lang: AppLanguage): String = when (this) {
        BATTERY -> lang.t("БАТАРЕЯ", "BATTERY")
        VOLTAGE -> lang.t("НАПРЯЖЕНИЕ", "VOLTAGE")
        PHASE -> lang.t("ФАЗНЫЙ ТОК", "PHASE CURRENT")
        MOSFET -> lang.t("ТЕМП. MOSFET", "MOSFET TEMP")
        PITCH -> lang.t("УГОЛ", "PITCH")
        PWM -> "PWM"
        TRIP -> lang.t("ПРОБЕГ ПОЕЗДКИ", "TRIP")
        ODOMETER -> lang.t("ОДОМЕТР", "ODOMETER")
        FIRMWARE -> lang.t("ПРОШИВКА", "FIRMWARE")
        CHARGING -> lang.t("ЗАРЯДКА", "CHARGING")
    }

    fun value(t: Telemetry?, lang: AppLanguage): String = when (this) {
        BATTERY -> t?.let { "${it.batteryPercent}%" } ?: "—"
        VOLTAGE -> t?.let { String.format(Locale.getDefault(), "%.1f V", it.voltageV) } ?: "—"
        PHASE -> t?.let { String.format(Locale.getDefault(), "%.1f A", it.phaseCurrentA) } ?: "—"
        MOSFET -> t?.let { String.format(Locale.getDefault(), "%.1f °C", it.mosfetTempC) } ?: "—"
        PITCH -> t?.let { String.format(Locale.getDefault(), "%.2f°", it.pitchDeg) } ?: "—"
        PWM -> t?.let { String.format(Locale.getDefault(), "%.0f %%", it.pwmPercent) } ?: "—"
        TRIP -> t?.let { String.format(Locale.getDefault(), "%.2f km", it.tripKm) } ?: "—"
        ODOMETER -> t?.let { String.format(Locale.getDefault(), "%.1f km", it.totalKm) } ?: "—"
        FIRMWARE -> t?.firmwareRaw?.toString() ?: "—"
        CHARGING -> t?.let { if (it.charging) lang.t("ДА", "YES") else lang.t("НЕТ", "NO") } ?: "—"
    }
}

@Composable
private fun MetricGrid(t: Telemetry?, lang: AppLanguage) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("dashboard_metrics", Context.MODE_PRIVATE) }
    val defaults = listOf(MetricKind.BATTERY, MetricKind.VOLTAGE, MetricKind.PHASE, MetricKind.MOSFET)
    var selected by remember {
        mutableStateOf(
            defaults.mapIndexed { index, fallback ->
                runCatching { MetricKind.valueOf(prefs.getString("slot_$index", fallback.name) ?: fallback.name) }
                    .getOrDefault(fallback)
            }
        )
    }
    var pickingSlot by remember { mutableStateOf<Int?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile(selected[0], t, lang, Modifier.weight(1f)) { pickingSlot = 0 }
            MetricTile(selected[1], t, lang, Modifier.weight(1f)) { pickingSlot = 1 }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile(selected[2], t, lang, Modifier.weight(1f)) { pickingSlot = 2 }
            MetricTile(selected[3], t, lang, Modifier.weight(1f)) { pickingSlot = 3 }
        }
        Text(
            lang.t("Нажми на плитку, чтобы изменить показатель", "Tap a tile to choose its metric"),
            color = Color(0xFF596373),
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }

    pickingSlot?.let { slot ->
        MetricPickerDialog(
            current = selected[slot],
            lang = lang,
            onDismiss = { pickingSlot = null },
            onSelect = { kind ->
                selected = selected.toMutableList().apply { this[slot] = kind }
                prefs.edit().putString("slot_$slot", kind.name).apply()
                pickingSlot = null
            }
        )
    }
}

@Composable
private fun MetricTile(kind: MetricKind, t: Telemetry?, lang: AppLanguage, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Surface2),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.padding(17.dp)) {
            Text(kind.title(lang), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text(kind.value(t, lang), color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MetricPickerDialog(current: MetricKind, lang: AppLanguage, onDismiss: () -> Unit, onSelect: (MetricKind) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF171C24),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text(lang.t("Что показывать?", "Choose metric"), fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 470.dp)) {
                items(MetricKind.values()) { kind ->
                    Text(
                        text = if (kind == current) "✓  ${kind.title(lang)}" else kind.title(lang),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(kind) }
                            .padding(vertical = 13.dp),
                        color = if (kind == current) Accent else Color(0xFFDDE2EA),
                        fontSize = 14.sp,
                        fontWeight = if (kind == current) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(lang.t("ОТМЕНА", "CANCEL"), color = Accent) }
        }
    )
}

@Composable
private fun ConnectionCard(t: Telemetry?, state: LinkState, message: String, recording: Boolean, lang: AppLanguage) {
    val online = t != null
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10141A)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (online) lang.t("● ТЕЛЕМЕТРИЯ ОНЛАЙН", "● TELEMETRY ONLINE") else lang.t("● СОЕДИНЕНИЕ", "● CONNECTION"),
                    modifier = Modifier.weight(1f),
                    color = if (online) Good else if (state == LinkState.ERROR) Danger else Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (recording) lang.t("● ПИШЕМ ЛОГ", "● LOGGING") else lang.t("Лог выключен", "Log off"),
                    color = if (recording) Amber else Color(0xFF626C7B),
                    fontSize = 10.sp,
                    fontWeight = if (recording) FontWeight.Bold else FontWeight.Normal,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (online) "${t?.model} · ${t?.batteryPercent}% · ${String.format(Locale.getDefault(), "%.1f V", t?.voltageV ?: 0f)}"
                else when (state) {
                    LinkState.ERROR -> message
                    LinkState.SCANNING -> lang.t("Ищу Bluetooth-устройства…", "Scanning Bluetooth devices…")
                    LinkState.CONNECTING, LinkState.DISCOVERING -> lang.t("Подключаюсь к колесу…", "Connecting to wheel…")
                    else -> lang.t("Колесо не подключено", "Wheel is not connected")
                },
                color = Color(0xFFD8DEE8),
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DebugRawCard(raw: String, lang: AppLanguage) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1015)), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(15.dp)) {
            Text(lang.t("RAW BLE · ОТЛАДКА", "RAW BLE · DEBUG"), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(raw, color = Color(0xFF8791A0), fontSize = 10.sp, lineHeight = 14.sp, minLines = 3, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DeviceRow(candidate: BleCandidate, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (candidate.likelyEuc) Color(0xFF17241A) else Color(0xFF12161D))
            .clickable(onClick = onClick)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(candidate.name, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(candidate.address, color = Color(0xFF687283), fontSize = 10.sp)
        }
        Text("${candidate.rssi} dBm", color = if (candidate.likelyEuc) Good else Muted, fontSize = 11.sp)
    }
}

@Composable
private fun RidesScreen(lang: AppLanguage) {
    val context = LocalContext.current
    val recording by WheelRepository.recording.collectAsState()
    val lastLog by WheelRepository.lastLogPath.collectAsState()
    val files = remember(recording, lastLog) { RideLogReader.listRides(context.filesDir) }
    var selectedPath by remember { mutableStateOf<String?>(null) }
    val selectedRide = remember(selectedPath, recording, lastLog) {
        selectedPath?.let { runCatching { RideLogReader.read(File(it)) }.getOrNull() }
    }

    if (selectedRide != null) {
        RideReplay(selectedRide, lang, onBack = { selectedPath = null })
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(28.dp)) }
        item { SectionHeader(lang.t("Поездки", "Rides"), lang.t("Записи CSV теперь можно открыть и прокрутить", "Open and replay recorded CSV logs")) }
        if (files.isEmpty()) {
            item { EmptyCard(lang.t("Пока нет записанных поездок. На главной нажми «Начать лог».", "No recorded rides yet. Start a log from Home.")) }
        } else {
            items(files, key = { it.absolutePath }) { file ->
                val ride = remember(file.absolutePath, file.length()) { runCatching { RideLogReader.read(file) }.getOrNull() }
                RideListCard(file, ride, lang) { selectedPath = file.absolutePath }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun RideListCard(file: File, ride: RideLog?, lang: AppLanguage, onClick: () -> Unit) {
    val date = remember(file.lastModified()) {
        SimpleDateFormat("dd.MM.yyyy · HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Surface1),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(date, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(file.name, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("›", color = Accent, fontSize = 28.sp, fontWeight = FontWeight.Light)
            }
            ride?.takeIf { it.samples.isNotEmpty() }?.let {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MiniStat(lang.t("Макс.", "Max"), String.format(Locale.getDefault(), "%.1f km/h", it.maxSpeedKmh))
                    MiniStat("PWM", String.format(Locale.getDefault(), "%.0f%%", it.maxPwm))
                    MiniStat(lang.t("Мин. V", "Min V"), String.format(Locale.getDefault(), "%.1f V", it.minVoltageV))
                }
            }
        }
    }
}

@Composable
private fun RowScopeMiniStat(label: String, value: String) = Unit

@Composable
private fun MiniStat(label: String, value: String) {
    Column {
        Text(label, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color(0xFFDDE3EC), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RideReplay(ride: RideLog, lang: AppLanguage, onBack: () -> Unit) {
    val samples = ride.samples
    var position by remember(ride.file.absolutePath) { mutableStateOf(0f) }
    var playing by remember(ride.file.absolutePath) { mutableStateOf(false) }
    val lastIndex = (samples.size - 1).coerceAtLeast(0)

    LaunchedEffect(playing, ride.file.absolutePath) {
        while (playing && position.roundToInt() < lastIndex) {
            delay(100)
            position = (position + 1f).coerceAtMost(lastIndex.toFloat())
        }
        if (position.roundToInt() >= lastIndex) playing = false
    }

    val sample = samples.getOrNull(position.roundToInt().coerceIn(0, lastIndex))

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Spacer(Modifier.height(24.dp)) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ ${lang.t("Назад", "Back")}", color = Accent) }
                Column(Modifier.weight(1f)) {
                    Text(lang.t("Воспроизведение поездки", "Ride replay"), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text(ride.file.name, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (samples.isEmpty()) {
            item { EmptyCard(lang.t("В этом CSV нет читаемых точек телеметрии.", "No readable telemetry samples in this CSV.")) }
        } else {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Surface1), shape = RoundedCornerShape(26.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Text(lang.t("СКОРОСТЬ", "SPEED"), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(String.format(Locale.getDefault(), "%.1f", sample?.speedKmh?.absoluteValue ?: 0f), color = Color.White, fontSize = 58.sp, fontWeight = FontWeight.Black)
                            Text(" km/h", color = Muted, fontSize = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
                        }
                        Text("PWM ${String.format(Locale.getDefault(), "%.0f", sample?.pwmPercent ?: 0f)}%", color = pwmColor(sample?.pwmPercent ?: 0f), fontSize = 18.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            item {
                Column {
                    Slider(
                        value = position.coerceIn(0f, lastIndex.toFloat().coerceAtLeast(1f)),
                        onValueChange = { position = it; playing = false },
                        valueRange = 0f..lastIndex.toFloat().coerceAtLeast(1f),
                        enabled = lastIndex > 0,
                    )
                    val offsetSec = sample?.let { ((it.timestampMs - (ride.startedAtMs ?: it.timestampMs)) / 1000.0) } ?: 0.0
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(String.format(Locale.getDefault(), "+%.1f s", offsetSec), color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Button(
                            onClick = {
                                if (position.roundToInt() >= lastIndex) position = 0f
                                playing = !playing
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(if (playing) lang.t("ПАУЗА", "PAUSE") else lang.t("ВОСПРОИЗВЕСТИ", "PLAY"), fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReplayMetric(lang.t("НАПРЯЖЕНИЕ", "VOLTAGE"), sample?.let { String.format(Locale.getDefault(), "%.1f V", it.voltageV) } ?: "—", Modifier.weight(1f))
                    ReplayMetric(lang.t("ФАЗНЫЙ ТОК", "PHASE CURRENT"), sample?.let { String.format(Locale.getDefault(), "%.1f A", it.phaseCurrentA) } ?: "—", Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReplayMetric(lang.t("ТЕМП. MOSFET", "MOSFET TEMP"), sample?.let { String.format(Locale.getDefault(), "%.1f °C", it.mosfetTempC) } ?: "—", Modifier.weight(1f))
                    ReplayMetric(lang.t("УГОЛ", "PITCH"), sample?.let { String.format(Locale.getDefault(), "%.2f°", it.pitchDeg) } ?: "—", Modifier.weight(1f))
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF10141A)), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(lang.t("ИТОГ ПОЕЗДКИ", "RIDE SUMMARY"), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("${lang.t("Максимальная скорость", "Max speed")}: ${String.format(Locale.getDefault(), "%.1f km/h", ride.maxSpeedKmh)}", color = Color.White, fontSize = 13.sp)
                        Text("${lang.t("Максимальный PWM", "Max PWM")}: ${String.format(Locale.getDefault(), "%.0f%%", ride.maxPwm)}", color = Color.White, fontSize = 13.sp)
                        Text("${lang.t("Минимальное напряжение", "Min voltage")}: ${String.format(Locale.getDefault(), "%.1f V", ride.minVoltageV)}", color = Color.White, fontSize = 13.sp)
                        Text("${lang.t("Максимальная температура", "Max temperature")}: ${String.format(Locale.getDefault(), "%.1f °C", ride.maxTempC)}", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ReplayMetric(label: String, value: String, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Surface2), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(15.dp)) {
            Text(label, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BatteryScreen(lang: AppLanguage) {
    val bms by WheelRepository.bms.collectAsState()
    val telemetry by WheelRepository.telemetry.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Spacer(Modifier.height(28.dp)) }
        item { SectionHeader("Smart BMS", lang.t("Два аккумуляторных пакета Sherman L", "Sherman L dual battery packs")) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Surface1), shape = RoundedCornerShape(22.dp)) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(telemetry?.model ?: "Sherman L", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(lang.t("Общее напряжение колеса", "Wheel voltage"), color = Muted, fontSize = 10.sp)
                    }
                    Text(
                        telemetry?.let { "${it.batteryPercent}% · ${String.format(Locale.getDefault(), "%.1f V", it.voltageV)}" } ?: "—",
                        color = Accent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
        if (bms == null) {
            item {
                EmptyCard(
                    lang.t(
                        "Жду страницы Smart BMS от колеса. После подключения Sherman L может понадобиться несколько секунд, чтобы пришли все ячейки обоих паков.",
                        "Waiting for Smart BMS pages. Sherman L may need a few seconds after connecting to send all cells from both packs.",
                    )
                )
            }
        } else {
            item { BmsPackCard(bms!!.pack1, lang) }
            item { BmsPackCard(bms!!.pack2, lang) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun BmsPackCard(pack: BmsPack, lang: AppLanguage) {
    val min = pack.minCellV
    val max = pack.maxCellV
    Card(colors = CardDefaults.cardColors(containerColor = Surface1), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("BMS ${pack.index}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text("${pack.validCells.size}/36 ${lang.t("ячеек получено", "cells received")}", color = Muted, fontSize = 10.sp)
                }
                Text(
                    pack.deltaV?.let { "Δ ${String.format(Locale.getDefault(), "%.0f mV", it * 1000f)}" } ?: "Δ —",
                    color = if ((pack.deltaV ?: 0f) > 0.03f) Danger else Good,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                MiniStat(lang.t("Мин.", "Min"), min?.let { String.format(Locale.getDefault(), "%.3f V", it) } ?: "—")
                MiniStat(lang.t("Макс.", "Max"), max?.let { String.format(Locale.getDefault(), "%.3f V", it) } ?: "—")
                MiniStat(lang.t("Сред.", "Avg"), pack.avgCellV?.let { String.format(Locale.getDefault(), "%.3f V", it) } ?: "—")
                MiniStat(lang.t("Ток", "Current"), pack.currentA?.let { String.format(Locale.getDefault(), "%.2f A", it) } ?: "—")
            }
            Spacer(Modifier.height(16.dp))
            Text(lang.t("ЯЧЕЙКИ", "CELLS"), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            pack.cells.chunked(6).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEachIndexed { colIndex, value ->
                        val cellIndex = rowIndex * 6 + colIndex + 1
                        val isMin = value > 1f && min != null && kotlin.math.abs(value - min) < 0.0005f
                        val isMax = value > 1f && max != null && kotlin.math.abs(value - max) < 0.0005f
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B212A)),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Column(Modifier.padding(vertical = 7.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(cellIndex.toString(), color = Color(0xFF687384), fontSize = 8.sp)
                                Text(
                                    if (value > 1f) String.format(Locale.getDefault(), "%.3f", value) else "—",
                                    color = when { isMin -> Danger; isMax -> Good; else -> Color(0xFFE3E8EF) },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
            }
            val temps = pack.temperaturesC.filter { it in -40f..120f && it != 0f }
            if (temps.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    lang.t("Температуры: ", "Temperatures: ") + temps.joinToString(" · ") { String.format(Locale.getDefault(), "%.1f°C", it) },
                    color = Muted,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    lang: AppLanguage,
    debugEnabled: Boolean,
    onLanguage: (AppLanguage) -> Unit,
    onDebug: (Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Spacer(Modifier.height(28.dp)) }
        item { SectionHeader(lang.t("Настройки", "Settings"), "EUC Lab · v0.0.3") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Surface1), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(17.dp)) {
                    Text(lang.t("ЯЗЫК", "LANGUAGE"), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LanguageButton("Русский", lang == AppLanguage.RU, Modifier.weight(1f)) { onLanguage(AppLanguage.RU) }
                        LanguageButton("English", lang == AppLanguage.EN, Modifier.weight(1f)) { onLanguage(AppLanguage.EN) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(lang.t("По умолчанию используется русский. PWM и km/h не переводятся.", "Russian is the default. PWM and km/h stay unchanged."), color = Muted, fontSize = 10.sp)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Surface1), shape = RoundedCornerShape(22.dp)) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(lang.t("RAW BLE / ОТЛАДКА", "RAW BLE / DEBUG"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(lang.t("Показывать сырые пакеты на главном экране", "Show raw BLE packets on Home"), color = Muted, fontSize = 10.sp)
                    }
                    Switch(
                        checked = debugEnabled,
                        onCheckedChange = onDebug,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Accent),
                    )
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF10141A)), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(17.dp)) {
                    Text(lang.t("КУДА ДВИГАЕМСЯ", "ROADMAP"), color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        lang.t(
                            "Берём функциональность WheelLog как ориентир: GPS-карта и графики, авто-лог, алармы, управление Veteran, экспорт, часы/виджеты. Сверху добавляем наше: Smart BMS, чёрный ящик, SOS и AI-разбор поездок.",
                            "WheelLog is our functionality baseline: GPS maps and charts, auto logging, alarms, Veteran controls, export, watches/widgets. Then we add our own layer: Smart BMS, black box, SOS and AI ride analysis.",
                        ),
                        color = Color(0xFFD6DCE5),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun LanguageButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Accent else Color(0xFF252B34),
            contentColor = if (selected) Color.Black else Color.White,
        )
    ) {
        Text(if (selected) "✓ $text" else text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column {
        Text(title, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun EmptyCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Surface1), shape = RoundedCornerShape(22.dp)) {
        Text(text, modifier = Modifier.padding(18.dp), color = Color(0xFFD4DAE3), fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color.Black,
            background = Bg,
            surface = Surface1,
            onSurface = Color.White,
        ),
        content = { Surface(modifier = Modifier.fillMaxSize(), color = Bg) { content() } },
    )
}
