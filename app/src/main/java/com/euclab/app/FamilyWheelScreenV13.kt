package com.euclab.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.euclab.app.data.Telemetry
import com.euclab.app.data.WheelBrandV12

private val F13Bg = Color(0xFF090B0F)
private val F13Card = Color(0xFF11151C)
private val F13Card2 = Color(0xFF171C24)
private val F13Muted = Color(0xFF7C8798)
private val F13Blue = Color(0xFF79C7FF)
private val F13Accent = Color(0xFFB6FF35)

@Composable
fun V13FamilyWheelScreen(t: Telemetry?, brand: WheelBrandV12, ru: Boolean) {
    val model = t?.model.orEmpty()
    val isP6 = model.contains("P6", ignoreCase = true)
    val title = when (brand) {
        WheelBrandV12.BEGODE -> "Begode"
        WheelBrandV12.EXTREME_BULL -> "Extreme Bull"
        WheelBrandV12.INMOTION -> "InMotion"
        else -> if (ru) "Колесо" else "Wheel"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(F13Bg).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Spacer(Modifier.height(28.dp)) }
        item {
            Column {
                Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                Text(if (model.isNotBlank()) model else if (ru) "Модель определяется…" else "Detecting model…", color = F13Muted, fontSize = 11.sp)
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = F13Card), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(17.dp)) {
                    Text(if (ru) "ПРОТОКОЛ" else "PROTOCOL", color = F13Blue, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    val protocol = when {
                        brand == WheelBrandV12.BEGODE || brand == WheelBrandV12.EXTREME_BULL -> "Gotway / Begode BLE · FFE0/FFE1"
                        isP6 -> "Lorin / InMotion V2 · Nordic UART"
                        else -> "InMotion V2 / Lorin"
                    }
                    Text(protocol, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    t?.let {
                        Spacer(Modifier.height(8.dp))
                        Text("${it.batteryPercent}% · ${"%.1f".format(it.voltageV)} V · PWM ${"%.0f".format(it.pwmPercent)}%", color = F13Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = F13Card), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(17.dp)) {
                    Text(if (ru) "НАСТРОЙКИ ЭТОГО СЕМЕЙСТВА" else "FAMILY SETTINGS", color = F13Blue, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    val rows = when {
                        isP6 -> listOf(
                            if (ru) "Лимит скорости / speed tilt-back" else "Speed limit / speed tilt-back",
                            if (ru) "Speed alarm" else "Speed alarm",
                            if (ru) "PWM tilt-back + PWM alarm 1/2" else "PWM tilt-back + PWM alarm 1/2",
                            if (ru) "Балансировочный угол" else "Balance angle",
                            if (ru) "Режим езды и чувствительность" else "Ride mode and sensitivity",
                            if (ru) "Лимит заряда" else "Charge limit",
                            if (ru) "Ток зарядки 110/220 В" else "110/220 V charging current",
                            if (ru) "Автофара и ДХО" else "Auto headlight and DRL",
                            if (ru) "Яркость логотипа" else "Logo light brightness",
                            if (ru) "Автоблокировка / транспортный режим" else "Auto lock / transport mode",
                            if (ru) "Мин. давление шины и игнор TPMS" else "Minimum tire pressure / ignore TPMS",
                            if (ru) "RideConnect и режим низкой батареи" else "RideConnect and low-battery mode",
                        )
                        brand == WheelBrandV12.BEGODE || brand == WheelBrandV12.EXTREME_BULL -> listOf(
                            if (ru) "Режим фары" else "Light mode",
                            if (ru) "LED-режим" else "LED mode",
                            if (ru) "Режим педалей" else "Pedal mode",
                            if (ru) "Наклон педалей" else "Pedal tilt",
                            if (ru) "Угол отключения" else "Cutout angle",
                            if (ru) "Roll-angle / extended roll-angle" else "Roll angle / extended roll angle",
                            if (ru) "Weak magnetism" else "Weak magnetism",
                            if (ru) "Громкость пищалки" else "Beeper volume",
                            if (ru) "Power alarm / speed alarm" else "Power alarm / speed alarm",
                            if (ru) "Единицы дисплея" else "Display units",
                            if (ru) "Калибровка" else "Calibration",
                        )
                        else -> listOf(if (ru) "Профиль настроек уточняется по модели" else "Settings profile is resolved per model")
                    }
                    rows.forEach { row ->
                        Text("• $row", color = Color(0xFFD0D6DF), fontSize = 11.sp, lineHeight = 16.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (ru) "В 0.0.13 подключаем транспорт и телеметрию. Управляющие переключатели включаем только там, где команда и диапазон подтверждены для конкретной модели — без случайных команд от другого бренда."
                        else "v0.0.13 enables transport and telemetry first. Controls are exposed only where the command and range are verified for the detected model.",
                        color = F13Muted,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
        }

        if (isP6) item {
            Card(colors = CardDefaults.cardColors(containerColor = F13Card2), shape = RoundedCornerShape(20.dp)) {
                Text(
                    if (ru) "У P6 нет подтверждённого ручного BLE-переключателя основной фары: приложение InMotion управляет автоматической фарой. Поэтому кнопку ON/OFF здесь специально не рисуем."
                    else "P6 has no verified manual BLE headlight toggle; InMotion exposes automatic headlight control instead, so EUC Lab intentionally does not show a fake ON/OFF button.",
                    color = F13Muted,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
