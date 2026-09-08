package com.euclab.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.euclab.app.ble.BleWheelManager
import com.euclab.app.data.Telemetry
import com.euclab.app.data.VeteranSettingsSnapshot
import com.euclab.app.data.WheelRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private val V7Bg = Color(0xFF090B0F)
private val V7Card = Color(0xFF11151C)
private val V7Card2 = Color(0xFF171C24)
private val V7Muted = Color(0xFF7C8798)
private val V7Accent = Color(0xFFB6FF35)
private val V7Good = Color(0xFF6DFF9A)
private val V7Danger = Color(0xFFFF5060)
private val V7Amber = Color(0xFFFFC857)
private val V7Blue = Color(0xFF79C7FF)

private fun tr(ru: Boolean, r: String, e: String) = if (ru) r else e

private data class V7Backup(
    val settings: VeteranSettingsSnapshot,
    val alertSpeed: Int,
    val pedalModeRaw: Int,
)

@Composable
fun V7WheelScreen(ble: BleWheelManager, ru: Boolean) {
    val telemetry by WheelRepository.telemetry.collectAsState()
    val lightOn by ble.lightOn.collectAsState()
    val online = telemetry != null
    val stopped = (telemetry?.speedKmh?.absoluteValue ?: 0f) < 1f
    val s = telemetry?.veteranSettings ?: VeteranSettingsSnapshot()
    val scope = rememberCoroutineScope()
    var advanced by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    var resetConfirm by remember { mutableStateOf(false) }
    var restoreConfirm by remember { mutableStateOf(false) }
    var backup by remember { mutableStateOf<V7Backup?>(null) }

    LaunchedEffect(telemetry?.timestampMs) {
        val t = telemetry ?: return@LaunchedEffect
        val st = t.veteranSettings
        val hasSettings = listOf(
            st.keyTonePercent, st.pedalHardness, st.screenBacklightPercent,
            st.dynamicAssist, st.accelerationLimit, st.pwmLimitRaw,
        ).any { it != null }
        if (backup == null && hasSettings) backup = V7Backup(st, t.alertSpeedKmh, t.pedalsModeRaw)
    }

    info?.let { (title, text) ->
        AlertDialog(
            onDismissRequest = { info = null },
            containerColor = V7Card2,
            title = { Text(title, color = Color.White, fontWeight = FontWeight.Black) },
            text = { Text(text, color = Color(0xFFD6DBE3), fontSize = 12.sp, lineHeight = 17.sp) },
            confirmButton = { TextButton(onClick = { info = null }) { Text("OK", color = V7Accent, fontWeight = FontWeight.Bold) } },
        )
    }

    if (resetConfirm) V7Confirm(
        title = tr(ru, "Сбросить пробег поездки?", "Reset trip distance?"),
        text = tr(ru, "Пробег текущей поездки на колесе будет обнулён. Это действие нельзя отменить.", "The current trip distance on the wheel will be reset. This cannot be undone."),
        confirm = tr(ru, "СБРОСИТЬ", "RESET"),
        onDismiss = { resetConfirm = false },
    ) {
        resetConfirm = false
        result = if (ble.resetTrip()) tr(ru, "Пробег поездки сброшен", "Trip distance reset") else tr(ru, "Команда не отправлена", "Command failed")
    }

    if (restoreConfirm) V7Confirm(
        title = tr(ru, "Вернуть настройки?", "Restore settings?"),
        text = tr(ru, "EUC Lab отправит на колесо снимок настроек, считанный при открытии этой сессии.", "EUC Lab will send back the settings snapshot captured at the start of this session."),
        confirm = tr(ru, "ВЕРНУТЬ", "RESTORE"),
        onDismiss = { restoreConfirm = false },
    ) {
        restoreConfirm = false
        val b = backup
        if (b == null) result = tr(ru, "Снимок ещё не получен", "Settings snapshot is not ready")
        else scope.launch {
            result = tr(ru, "Восстанавливаю настройки…", "Restoring settings…")
            val x = b.settings
            suspend fun step(block: () -> Boolean) { block(); delay(220) }
            x.keyTonePercent?.let { step { ble.setKeyToneVolume(it) } }
            x.pedalHardness?.let { step { ble.setPedalHardness(it) } }
            x.screenBacklightPercent?.let { step { ble.setScreenBacklight(it) } }
            x.transportMode?.let { step { ble.setTransportMode(it) } }
            x.highSpeedMode?.let { step { ble.setHighSpeedMode(it) } }
            x.lowVoltageMode?.let { step { ble.setLowVoltageMode(it) } }
            x.voltageCorrection?.let { step { ble.setVoltageCorrection(it) } }
            x.pwmLimitRaw?.let { step { ble.setPwmLimitRaw(it) } }
            x.maxChargeVoltageRaw?.let { step { ble.setMaxChargeVoltageRaw(it) } }
            x.brakePressureAlarm?.let { step { ble.setBrakePressureAlarm(it) } }
            x.lateralCutoffAngle?.let { step { ble.setLateralCutoffAngle(it) } }
            x.dynamicAssist?.let { step { ble.setDynamicAssist(it) } }
            x.accelerationLimit?.let { step { ble.setAccelerationLimit(it) } }
            x.wheelDisplayMiles?.let { step { ble.setWheelDisplayMiles(it) } }
            if (b.alertSpeed > 0) step { ble.setAlarmSpeed(b.alertSpeed) }
            result = tr(ru, "Снимок настроек отправлен", "Settings snapshot restored")
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(V7Bg).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Spacer(Modifier.height(28.dp)) }
        item {
            Column {
                Text(tr(ru, "Колесо", "Wheel"), color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                Text(telemetry?.let { "${it.model} · FW ${it.firmwareRaw}" } ?: tr(ru, "Подключи Veteran / LeaperKim", "Connect a Veteran / LeaperKim wheel"), color = V7Muted, fontSize = 10.sp)
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = V7Card), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(ru, "БЫСТРЫЕ ДЕЙСТВИЯ", "QUICK ACTIONS"), color = V7Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                        Text(if (online) tr(ru, "● ОНЛАЙН", "● ONLINE") else tr(ru, "● НЕ В СЕТИ", "● OFFLINE"), color = if (online) V7Good else V7Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        V7QuickButton(if (lightOn) tr(ru, "ФАРА ON", "LIGHT ON") else tr(ru, "ФАРА OFF", "LIGHT OFF"), Modifier.weight(1f), online) {
                            result = if (ble.setLight(!lightOn)) tr(ru, "Команда фары отправлена", "Headlight command sent") else tr(ru, "Ошибка команды", "Command failed")
                        }
                        V7QuickButton(tr(ru, "СИГНАЛ", "BEEP"), Modifier.weight(1f), online) { result = if (ble.beep()) tr(ru, "Сигнал отправлен", "Beep sent") else tr(ru, "Ошибка команды", "Command failed") }
                    }
                    Spacer(Modifier.height(8.dp))
                    V7QuickButton(tr(ru, "СБРОСИТЬ ПРОБЕГ ПОЕЗДКИ", "RESET TRIP DISTANCE"), Modifier.fillMaxWidth(), online && stopped) { resetConfirm = true }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { advanced = !advanced },
                colors = CardDefaults.cardColors(containerColor = if (advanced) Color(0xFF1A2415) else V7Card),
                shape = RoundedCornerShape(24.dp),
            ) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr(ru, "РАСШИРЕННЫЕ НАСТРОЙКИ КОЛЕСА", "ADVANCED WHEEL SETTINGS"), color = if (advanced) V7Accent else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        Text(tr(ru, "Настройки из протокола LeaperKim с пояснениями", "LeaperKim protocol settings with explanations"), color = V7Muted, fontSize = 10.sp)
                    }
                    Text(if (advanced) "⌃" else "⌄", color = V7Accent, fontSize = 22.sp)
                }
            }
        }

        if (advanced) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF121921)), shape = RoundedCornerShape(22.dp), modifier = Modifier.border(1.dp, V7Blue.copy(alpha = .3f), RoundedCornerShape(22.dp))) {
                    Column(Modifier.padding(16.dp)) {
                        Text(tr(ru, "СНИМОК НАСТРОЕК", "SETTINGS SNAPSHOT"), color = V7Blue, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        Text(tr(ru, "При первом получении блока настроек EUC Lab запоминает исходные значения на эту сессию.", "EUC Lab captures the original settings when the control block is first received in this session."), color = Color(0xFFC8D0DA), fontSize = 10.sp, lineHeight = 15.sp)
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { restoreConfirm = true }, enabled = backup != null && stopped, border = androidx.compose.foundation.BorderStroke(1.dp, V7Blue), colors = ButtonDefaults.outlinedButtonColors(contentColor = V7Blue)) {
                            Text(tr(ru, "ВЕРНУТЬ КАК БЫЛО", "RESTORE ORIGINAL"), fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            item { V7AutoOffCard(telemetry, ru) }

            item { V7SectionTitle(tr(ru, "Поведение и педали", "Ride behaviour & pedals")) }
            item {
                V7PedalModes(ble, telemetry, ru, stopped) { result = it }
            }
            item { V7SliderSetting(tr(ru, "Жёсткость педалей", "Pedal hardness"), s.pedalHardness, 0, 100, "%", stopped && online, tr(ru, "Тонкая настройка жёсткости отклика педалей. Не путать с тремя базовыми режимами Hard/Medium/Soft.", "Fine pedal response stiffness. Separate from the three basic Hard/Medium/Soft modes."), { info = it }) { result = commandResult(ru, ble.setPedalHardness(it)) } }
            item { V7SliderSetting(tr(ru, "Dynamic Assist", "Dynamic Assist"), s.dynamicAssist, 0, 100, "%", stopped && online, tr(ru, "Динамическая помощь LeaperKim. Меняет характер поддержки при разгоне/нагрузке. Меняй постепенно и тестируй на малой скорости.", "LeaperKim dynamic assist. Changes the feel under acceleration/load. Adjust gradually and test at low speed."), { info = it }) { result = commandResult(ru, ble.setDynamicAssist(it)) } }
            item { V7SliderSetting(tr(ru, "Ограничение ускорения", "Acceleration limit"), s.accelerationLimit, 0, 100, "%", stopped && online, tr(ru, "Ограничивает агрессивность разгона в логике контроллера. Это не лимит максимальной скорости.", "Limits acceleration aggressiveness in the controller. It is not a top-speed limit."), { info = it }) { result = commandResult(ru, ble.setAccelerationLimit(it)) } }

            item { V7SectionTitle(tr(ru, "Скорость и запас мощности", "Speed & power margin")) }
            item { V7SliderSetting(tr(ru, "Предупреждение скорости", "Wheel speed alarm"), telemetry?.alertSpeedKmh?.takeIf { it > 0 }, 10, 100, "km/h", stopped && online, tr(ru, "Порог штатного звукового предупреждения колеса. Дополнительный PWM-сигнал EUC Lab работает независимо.", "Wheel's built-in speed alarm threshold. EUC Lab's additional PWM alarm is independent."), { info = it }) { result = commandResult(ru, ble.setAlarmSpeed(it)) } }
            item { V7ToggleSetting("High Speed Mode", s.highSpeedMode, stopped && online, tr(ru, "Режим высокой скорости меняет рабочие ограничения колеса. Используй только если понимаешь влияние на запас по напряжению/PWM.", "High Speed Mode changes the wheel's operating limits. Use only if you understand the effect on voltage/PWM margin."), { info = it }) { result = commandResult(ru, ble.setHighSpeedMode(it)) } }
            item { V7ToggleSetting("Low Voltage Mode", s.lowVoltageMode, stopped && online, tr(ru, "Позволяет использовать более глубокую часть разряда. При низком напряжении запас мощности уменьшается — это настройка повышенного риска.", "Allows use of a deeper discharge region. Power margin falls at low voltage; this is a higher-risk setting."), { info = it }) { result = commandResult(ru, ble.setLowVoltageMode(it)) } }
            item { V7SliderSetting(tr(ru, "Лимит PWM · RAW", "PWM limit · RAW"), s.pwmLimitRaw, 0, 100, "", stopped && online, tr(ru, "Сохраняем и отправляем именно значение протокола. Пока не показываем пересчитанный процент, чтобы не выдать неверную трактовку за факт.", "The raw protocol value is shown and sent. We intentionally do not relabel it as a percentage until the conversion is fully verified."), { info = it }, danger = true) { result = commandResult(ru, ble.setPwmLimitRaw(it)) } }

            item { V7SectionTitle(tr(ru, "Интерфейс и сервис", "Interface & service")) }
            item { V7SliderSetting(tr(ru, "Громкость кнопок", "Key tone volume"), s.keyTonePercent, 0, 100, "%", stopped && online, tr(ru, "Громкость звуков кнопок/клавиш колеса. Не меняет аварийные сигналы скорости, PWM и защит.", "Volume of wheel key/button tones. Does not change speed, PWM or safety alarms."), { info = it }) { result = commandResult(ru, ble.setKeyToneVolume(it)) } }
            item { V7SliderSetting(tr(ru, "Яркость экрана", "Screen brightness"), s.screenBacklightPercent, 0, 100, "%", stopped && online, tr(ru, "Яркость штатного дисплея колеса.", "Brightness of the wheel's built-in display."), { info = it }) { result = commandResult(ru, ble.setScreenBacklight(it)) } }
            item { V7ToggleSetting(tr(ru, "Мили на дисплее колеса", "Miles on wheel display"), s.wheelDisplayMiles, stopped && online, tr(ru, "Меняет единицы только на штатном дисплее колеса. EUC Lab продолжает использовать выбранные единицы приложения.", "Changes units on the wheel display only. EUC Lab keeps its own app unit setting."), { info = it }) { result = commandResult(ru, ble.setWheelDisplayMiles(it)) } }
            item { V7ToggleSetting(tr(ru, "Транспортировочный режим", "Transport mode"), s.transportMode, stopped && online, tr(ru, "Режим транспортировки блокирует тягу для безопасной перевозки. Не включай во время движения.", "Transport mode disables drive torque for transport. Never enable while moving."), { info = it }, danger = true) { result = commandResult(ru, ble.setTransportMode(it)) } }

            item { V7SectionTitle(tr(ru, "Защиты и калибровка", "Protection & calibration")) }
            item { V7SliderSetting(tr(ru, "Угол отключения при падении", "Lateral cut-off angle"), s.lateralCutoffAngle, 1, 90, "°", stopped && online, tr(ru, "Угол, при котором контроллер считает колесо упавшим и отключает тягу. Слишком большое значение может быть опасно.", "Angle at which the controller considers the wheel fallen and cuts drive. Excessively large values can be dangerous."), { info = it }, danger = true) { result = commandResult(ru, ble.setLateralCutoffAngle(it)) } }
            item { V7SliderSetting(tr(ru, "Коррекция напряжения", "Voltage correction"), s.voltageCorrection, -15, 15, "", stopped && online, tr(ru, "Сервисная поправка измерения напряжения. Без контрольного мультиметра лучше не менять.", "Service correction for voltage measurement. Do not change without a reference meter."), { info = it }, danger = true) { result = commandResult(ru, ble.setVoltageCorrection(it)) } }
            item { V7SliderSetting(tr(ru, "Макс. напряжение заряда · RAW", "Max charge voltage · RAW"), s.maxChargeVoltageRaw, 0, 120, "", stopped && online, tr(ru, "Сервисное значение протокола зарядного лимита. Оставлено RAW, пока формула полного напряжения для конкретной модели не подтверждена на железе.", "Raw protocol charge-voltage limit. Kept RAW until the model-specific full-voltage conversion is verified on hardware."), { info = it }, danger = true) { result = commandResult(ru, ble.setMaxChargeVoltageRaw(it)) } }
            item { V7SliderSetting(tr(ru, "Сигнал давления торможения", "Brake pressure alarm"), s.brakePressureAlarm, 0, 150, "", stopped && online, tr(ru, "Порог штатного предупреждения при интенсивном торможении/нагрузке. Значение протокола отображается напрямую.", "Threshold for the wheel's built-in hard-braking/load warning. Protocol value is shown directly."), { info = it }) { result = commandResult(ru, ble.setBrakePressureAlarm(it)) } }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = V7Card), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(tr(ru, "КАЛИБРОВКА", "CALIBRATION"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        Text(tr(ru, "Перед калибровкой колесо должно стоять неподвижно и ровно.", "The wheel must be stationary and level before calibration."), color = V7Muted, fontSize = 10.sp)
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { result = commandResult(ru, ble.calibrate()) }, enabled = online && stopped, border = androidx.compose.foundation.BorderStroke(1.dp, V7Amber), colors = ButtonDefaults.outlinedButtonColors(contentColor = V7Amber)) {
                            Text(tr(ru, "КАЛИБРОВАТЬ", "CALIBRATE"), fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }

        result?.let { item { V7Message(it) } }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

private fun commandResult(ru: Boolean, ok: Boolean) = if (ok) tr(ru, "Команда отправлена", "Command sent") else tr(ru, "Команда не отправлена", "Command failed")

@Composable
private fun V7AutoOffCard(t: Telemetry?, ru: Boolean) {
    val sec = t?.autoOffSec ?: 0
    Card(colors = CardDefaults.cardColors(containerColor = V7Card), shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr(ru, "АВТОВЫКЛЮЧЕНИЕ", "AUTO POWER-OFF"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                Text(tr(ru, "Колесо передаёт оставшееся время. Команда изменения таймера пока не подтверждена.", "The wheel reports the remaining time. A command to change the timer is not yet verified."), color = V7Muted, fontSize = 10.sp, lineHeight = 14.sp)
            }
            Text(if (sec > 0) "%d:%02d".format(sec / 60, sec % 60) else "—", color = if (sec in 1..60) V7Amber else V7Blue, fontSize = 22.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun V7PedalModes(ble: BleWheelManager, t: Telemetry?, ru: Boolean, stopped: Boolean, result: (String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = V7Card), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(tr(ru, "БАЗОВЫЙ РЕЖИМ ПЕДАЛЕЙ", "BASE PEDAL MODE"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(0 to tr(ru, "ЖЁСТКИЙ", "HARD"), 1 to tr(ru, "СРЕДНИЙ", "MEDIUM"), 2 to tr(ru, "МЯГКИЙ", "SOFT")).forEach { (mode, name) ->
                    OutlinedButton(onClick = { result(commandResult(ru, ble.setPedalMode(mode))) }, enabled = t != null && stopped, modifier = Modifier.weight(1f), contentPadding = PaddingValues(3.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3A424E)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                        Text(name, fontSize = 8.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun V7SliderSetting(title: String, current: Int?, min: Int, max: Int, unit: String, enabled: Boolean, help: String, info: (Pair<String, String>) -> Unit, danger: Boolean = false, set: (Int) -> Unit) {
    var value by remember(current) { mutableFloatStateOf((current ?: min).coerceIn(min, max).toFloat()) }
    Card(colors = CardDefaults.cardColors(containerColor = V7Card), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(if (current == null) "Жду значение от колеса / Waiting for wheel" else "${value.roundToInt()}$unit", color = if (danger) V7Amber else V7Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text("ⓘ", color = V7Blue, fontSize = 18.sp, modifier = Modifier.clickable { info(title to help) }.padding(6.dp))
            }
            Slider(value = value, onValueChange = { value = it }, onValueChangeFinished = { if (current != null && enabled) set(value.roundToInt()) }, valueRange = min.toFloat()..max.toFloat(), enabled = current != null && enabled, colors = SliderDefaults.colors(thumbColor = if (danger) V7Amber else V7Accent, activeTrackColor = if (danger) V7Amber else V7Accent, inactiveTrackColor = Color(0xFF303743)))
        }
    }
}

@Composable
private fun V7ToggleSetting(title: String, current: Boolean?, enabled: Boolean, help: String, info: (Pair<String, String>) -> Unit, danger: Boolean = false, set: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = V7Card), shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("ⓘ", color = V7Blue, fontSize = 17.sp, modifier = Modifier.clickable { info(title to help) }.padding(horizontal = 7.dp))
                }
                Text(if (current == null) "Жду значение / Waiting" else if (current) "ON" else "OFF", color = if (danger) V7Amber else V7Muted, fontSize = 9.sp)
            }
            Switch(checked = current ?: false, onCheckedChange = { if (current != null && enabled) set(it) }, enabled = current != null && enabled, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = if (danger) V7Amber else V7Accent))
        }
    }
}

@Composable private fun V7SectionTitle(text: String) { Text(text.uppercase(), color = V7Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 4.dp, start = 2.dp)) }

@Composable
private fun V7QuickButton(text: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = V7Card2, contentColor = Color.White, disabledContainerColor = Color(0xFF141820), disabledContentColor = Color(0xFF59616D))) {
        Text(text, fontSize = 9.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun V7Message(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF152019)), shape = RoundedCornerShape(18.dp)) { Text(text, color = V7Good, fontSize = 11.sp, modifier = Modifier.padding(14.dp)) }
}

@Composable
private fun V7Confirm(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = V7Card2, title = { Text(title, color = Color.White, fontWeight = FontWeight.Black) }, text = { Text(text, color = Color(0xFFD5DAE2), fontSize = 12.sp, lineHeight = 17.sp) }, confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = V7Danger, fontWeight = FontWeight.Black) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("ОТМЕНА / CANCEL", color = V7Muted) } })
}
