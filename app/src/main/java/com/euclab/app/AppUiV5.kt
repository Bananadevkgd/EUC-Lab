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
                    V5Screen.WHEEL -> V5Wheel(ble, lang)
                    V5Screen.SETTINGS -> V5Settings(
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
            Text("${t?.model ?: "Sherman L"} · v0.0.6", color = V5Muted, fontSize = 12.sp)
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
        mutableFloatStateOf(
            (telemetry?.keyTonePercent ?: prefs.getInt("key_tone_percent", 70)).coerceIn(0, 100).toFloat()
        )
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
                    result = if (ble.resetTrip()) {
                        lang.t("Пробег поездки сброшен", "Trip distance reset")
                    } else {
                        lang.t("Команда не отправлена", "Command failed")
                    }
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
                    onClick = {
                        result = if (ble.beep()) lang.t("Команда сигнала отправлена", "Horn command sent") else lang.t("Команда не отправлена", "Command failed")
                    },
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
                        onValueChange = {
                            draggingTone = true
                            keyTone = it
                        },
                        onValueChangeFinished = {
                            val value = keyTone.roundToInt().coerceIn(0, 100)
                            prefs.edit().putInt("key_tone_percent", value).apply()
                            result = if (ble.setKeyToneVolume(value)) {
                                lang.t("Громкость кнопок: $value%", "Key tone volume: $value%")
                            } else {
                                lang.t("Команда громкости не отправлена", "Volume command failed")
                            }
                            draggingTone = false
                        },
                        valueRange = 0f..100f,
                        enabled = online,
                        colors = SliderDefaults.colors(
                            thumbColor = V5Accent,
                            activeTrackColor = V5Accent,
                            inactiveTrackColor = Color(0xFF303743),
                        ),
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
                                onClick = {
                                    if (stopped) result = if (ble.setPedalMode(mode)) lang.t("Режим отправлен", "Mode sent") else lang.t("Команда не отправлена", "Command failed")
                                },
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
private fun V5Settings(lang: V5Language, debug: Boolean, onLang: (V5Language) -> Unit, onDebug: (Boolean) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Spacer(Modifier.height(28.dp)) }
        item { V5SectionHeader(lang.t("Настройки", "Settings"), "EUC Lab · v0.0.6") }
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
