package com.euclab.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.euclab.app.ble.BleWheelManager
import com.euclab.app.data.BegodeSettingsV14
import com.euclab.app.data.Telemetry
import com.euclab.app.data.WheelVoltageCatalogV14

private val G14Bg = Color(0xFF090B0F)
private val G14Card = Color(0xFF11151C)
private val G14Card2 = Color(0xFF171C24)
private val G14Muted = Color(0xFF7C8798)
private val G14Accent = Color(0xFFB6FF35)
private val G14Blue = Color(0xFF79C7FF)
private val G14Danger = Color(0xFFFF5060)

@Composable
fun V14BegodeWheelScreen(ble: BleWheelManager, telemetry: Telemetry?, ru: Boolean) {
    val settings by ble.begodeSettings.collectAsState()
    val model = telemetry?.model ?: "Begode"
    val profile = WheelVoltageCatalogV14.match(model)
    var ledDraft by remember(settings.ledMode) { mutableIntStateOf(settings.ledMode ?: 0) }
    var weakDraft by remember(settings.weakMagnetism) { mutableIntStateOf(settings.weakMagnetism ?: 0) }
    var beeperDraft by remember(settings.beeperVolume) { mutableIntStateOf(settings.beeperVolume ?: 5) }
    var cutoutDraft by remember(settings.cutoutAngleDeg) { mutableIntStateOf(settings.cutoutAngleDeg ?: 70) }
    var result by remember { mutableStateOf<String?>(null) }
    var confirmCutout by remember { mutableStateOf(false) }
    var confirmCalibration by remember { mutableStateOf(false) }

    if (confirmCutout) {
        AlertDialog(
            onDismissRequest = { confirmCutout = false },
            title = { Text(if (ru) "Изменить угол отключения?" else "Change cutout angle?") },
            text = { Text(if (ru) "Будет отправлено $cutoutDraft°. Это защитная настройка колеса — применяй только осознанно." else "$cutoutDraft° will be sent. This is a safety-related wheel setting.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmCutout = false
                    result = if (ble.begodeSetCutoutAngle(cutoutDraft)) {
                        if (ru) "Угол $cutoutDraft° отправлен" else "$cutoutDraft° sent"
                    } else if (ru) "Команда не отправлена" else "Command not sent"
                }) { Text(if (ru) "ПРИМЕНИТЬ" else "APPLY", color = G14Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmCutout = false }) { Text(if (ru) "Отмена" else "Cancel") } },
        )
    }

    if (confirmCalibration) {
        AlertDialog(
            onDismissRequest = { confirmCalibration = false },
            title = { Text(if (ru) "Калибровка Begode" else "Begode calibration") },
            text = { Text(if (ru) "Колесо должно стоять неподвижно и ровно. Команда калибровки будет отправлена только после подтверждения." else "Keep the wheel stationary and level. Calibration will only be sent after confirmation.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmCalibration = false
                    result = if (ble.begodeCalibrate()) if (ru) "Калибровка отправлена" else "Calibration sent" else if (ru) "Команда не отправлена" else "Command not sent"
                }) { Text(if (ru) "КАЛИБРОВАТЬ" else "CALIBRATE", color = G14Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmCalibration = false }) { Text(if (ru) "Отмена" else "Cancel") } },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(G14Bg).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Spacer(Modifier.height(28.dp)) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(model, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                    Text(
                        profile?.let { "${it.brand} · ${v14Fmt(it.fullVoltageV)} V · ${it.seriesCells}S" }
                            ?: "Gotway / Begode BLE · FFE0/FFE1",
                        color = G14Muted,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        item { V14WheelArtwork(model, Modifier.fillMaxWidth().height(180.dp)) }

        item {
            G14Card(if (ru) "ТЕЛЕМЕТРИЯ" else "TELEMETRY") {
                Text(
                    telemetry?.let { "${it.batteryPercent}% · ${v14Fmt(it.voltageV)} V · PWM ${"%.0f".format(it.pwmPercent)}%" }
                        ?: if (ru) "Жду данные…" else "Waiting for data…",
                    color = G14Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    if (ru) "Настройки ниже показывают readback, который реально прислал контроллер. «—» значит, поле ещё не получено."
                    else "Controls below use real controller readback. ‘—’ means that field has not arrived yet.",
                    color = G14Muted,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
        }

        item {
            G14Card(if (ru) "ФАРА" else "LIGHT") {
                G14ChoiceRow(
                    options = listOf("OFF", "ON", if (ru) "СТРОБ" else "STROBE"),
                    selected = settings.lightMode,
                ) { mode ->
                    result = if (ble.begodeSetLightMode(mode)) if (ru) "Режим фары отправлен" else "Light mode sent" else if (ru) "Не отправлено" else "Not sent"
                }
            }
        }

        item {
            G14Card(if (ru) "РЕЖИМ ПЕДАЛЕЙ" else "PEDAL MODE") {
                G14ChoiceRow(
                    options = if (ru) listOf("ЖЁСТК.", "FAST", "МЯГК.", "СРЕДН.") else listOf("HARD", "FAST", "SOFT", "MID"),
                    selected = settings.pedalsMode,
                ) { mode ->
                    result = if (ble.begodeSetPedalsMode(mode)) if (ru) "Режим педалей отправлен" else "Pedal mode sent" else if (ru) "Не отправлено" else "Not sent"
                }
            }
        }

        item {
            G14Card("ROLL-ANGLE") {
                G14ChoiceRow(
                    options = if (ru) listOf("ОБЫЧН.", "РАВНО", "ОБРАТН.") else listOf("NORMAL", "EQUAL", "REVERSE"),
                    selected = settings.rollAngleMode,
                ) { mode ->
                    result = if (ble.begodeSetRollAngleMode(mode)) "Roll-angle: ${mode}" else if (ru) "Не отправлено" else "Not sent"
                }
            }
        }

        item {
            G14Card(if (ru) "LED-РЕЖИМ" else "LED MODE") {
                G14NumberControl(ledDraft, 0, 9, onValue = { ledDraft = it }) {
                    result = if (ble.begodeSetLedMode(ledDraft)) "LED $ledDraft" else if (ru) "Не отправлено" else "Not sent"
                }
                G14Readback(if (ru) "Сейчас" else "Readback", settings.ledMode?.toString())
            }
        }

        item {
            G14Card(if (ru) "ГРОМКОСТЬ ПИЩАЛКИ" else "BEEPER VOLUME") {
                G14NumberControl(beeperDraft, 0, 9, onValue = { beeperDraft = it }) {
                    result = if (ble.begodeSetBeeperVolume(beeperDraft)) if (ru) "Громкость $beeperDraft отправлена" else "Volume $beeperDraft sent" else if (ru) "Не отправлено" else "Not sent"
                }
                G14Readback(if (ru) "Сейчас" else "Readback", settings.beeperVolume?.toString())
            }
        }

        item {
            G14Card("WEAK MAGNETISM") {
                G14NumberControl(weakDraft, 0, 9, onValue = { weakDraft = it }) {
                    result = if (ble.begodeSetWeakMagnetism(weakDraft)) "Weak magnetism $weakDraft" else if (ru) "Не отправлено" else "Not sent"
                }
                G14Readback(if (ru) "Сейчас" else "Readback", settings.weakMagnetism?.toString())
            }
        }

        item {
            G14Card(if (ru) "УГОЛ ОТКЛЮЧЕНИЯ" else "CUTOUT ANGLE") {
                G14NumberControl(cutoutDraft, 45, 90, step = 5, suffix = "°", onValue = { cutoutDraft = it }) {
                    confirmCutout = true
                }
                G14Readback(if (ru) "Сейчас" else "Readback", settings.cutoutAngleDeg?.let { "$it°" })
            }
        }

        item {
            G14Card(if (ru) "ЕДИНИЦЫ ДИСПЛЕЯ" else "DISPLAY UNITS") {
                G14ChoiceRow(
                    options = listOf("KM", "MI"),
                    selected = settings.inMiles?.let { if (it) 1 else 0 },
                ) { mode ->
                    result = if (ble.begodeSetMiles(mode == 1)) if (ru) "Единицы отправлены" else "Units sent" else if (ru) "Не отправлено" else "Not sent"
                }
            }
        }

        item {
            G14Card(if (ru) "СЕРВИС" else "SERVICE") {
                Button(
                    onClick = { confirmCalibration = true },
                    colors = ButtonDefaults.buttonColors(containerColor = G14Card2, contentColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (ru) "КАЛИБРОВКА" else "CALIBRATION", fontWeight = FontWeight.Black, fontSize = 10.sp) }
                settings.tiltBackSpeedKmh?.let {
                    Spacer(Modifier.height(8.dp))
                    G14Readback(if (ru) "Текущий tilt-back" else "Current tilt-back", "$it km/h")
                }
            }
        }

        result?.let { message ->
            item {
                Text(message, color = G14Muted, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun G14Card(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = G14Card), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, color = G14Blue, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun G14ChoiceRow(options: List<String>, selected: Int?, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { index, label ->
            Button(
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected == index) G14Accent else G14Card2,
                    contentColor = if (selected == index) Color.Black else Color.White,
                ),
            ) { Text(label, fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1) }
        }
    }
}

@Composable
private fun G14NumberControl(
    value: Int,
    min: Int,
    max: Int,
    step: Int = 1,
    suffix: String = "",
    onValue: (Int) -> Unit,
    onApply: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onValue((value - step).coerceAtLeast(min)) }) { Text("−") }
        Text("$value$suffix", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onValue((value + step).coerceAtMost(max)) }) { Text("+") }
        Button(onClick = onApply, colors = ButtonDefaults.buttonColors(containerColor = G14Accent, contentColor = Color.Black)) {
            Text("OK", fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun G14Readback(label: String, value: String?) {
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = G14Muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
        Text(value ?: "—", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

private fun v14Fmt(value: Float): String = if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(java.util.Locale.US, value)
