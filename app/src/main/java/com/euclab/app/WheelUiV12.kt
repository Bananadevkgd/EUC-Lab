package com.euclab.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.euclab.app.data.FeatureSupportV12
import com.euclab.app.data.Telemetry
import com.euclab.app.data.WheelBrandV12
import com.euclab.app.data.WheelProfilesV12
import com.euclab.app.data.WheelRepository
import java.util.Locale

private val U12Bg = Color(0xFF090B0F)
private val U12Card = Color(0xFF11151C)
private val U12Card2 = Color(0xFF171C24)
private val U12Muted = Color(0xFF7C8798)
private val U12Accent = Color(0xFFB6FF35)
private val U12Good = Color(0xFF6DFF9A)
private val U12Blue = Color(0xFF79C7FF)
private val U12Danger = Color(0xFFFF5060)

private fun u12t(ru: Boolean, r: String, e: String) = if (ru) r else e

@Composable
fun V12WheelScreen(ble: BleWheelManager, ru: Boolean) {
    val telemetry by WheelRepository.telemetry.collectAsState()
    val profile = WheelProfilesV12.forModel(telemetry?.model)
    when (profile.brand) {
        WheelBrandV12.KINGSONG -> V12KingSongWheelScreen(ble, telemetry, ru)
        WheelBrandV12.LEAPERKIM -> V7WheelScreen(ble, ru)
        else -> V12UnknownWheelScreen(telemetry, profile.displayBrand, ru)
    }
}

@Composable
private fun V12KingSongWheelScreen(ble: BleWheelManager, telemetry: Telemetry?, ru: Boolean) {
    val lightOn by ble.lightOn.collectAsState()
    val profile = WheelProfilesV12.forModel(telemetry?.model)
    var result by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(U12Bg).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Spacer(Modifier.height(28.dp)) }
        item {
            Column {
                Text("KingSong", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                Text(
                    telemetry?.model ?: u12t(ru, "Модель определяется…", "Detecting model…"),
                    color = U12Muted,
                    fontSize = 11.sp,
                )
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = U12Card), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(u12t(ru, "БЫСТРЫЕ ДЕЙСТВИЯ", "QUICK ACTIONS"), color = U12Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                        Text("● KINGSONG", color = U12Good, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val ok = ble.setLight(!lightOn)
                                result = if (ok) u12t(ru, "Команда фары отправлена", "Headlight command sent") else u12t(ru, "Фара не поддержана этой моделью/прошивкой", "Headlight is not supported by this model/firmware")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = if (lightOn) U12Accent else U12Card2, contentColor = if (lightOn) Color.Black else Color.White),
                            shape = RoundedCornerShape(18.dp),
                        ) { Text(if (lightOn) u12t(ru, "ФАРА ON", "LIGHT ON") else u12t(ru, "ФАРА OFF", "LIGHT OFF"), fontSize = 10.sp, fontWeight = FontWeight.Black) }

                        Button(
                            onClick = {
                                val ok = ble.beep()
                                result = if (ok) u12t(ru, "Сигнал отправлен", "Beep sent") else u12t(ru, "Сигнал не поддержан этой моделью/прошивкой", "Beep is not supported by this model/firmware")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = U12Card2, contentColor = Color.White),
                            shape = RoundedCornerShape(18.dp),
                        ) { Text(u12t(ru, "СИГНАЛ", "BEEP"), fontSize = 10.sp, fontWeight = FontWeight.Black) }
                    }
                    result?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = U12Muted, fontSize = 10.sp)
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = U12Card), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(17.dp)) {
                    Text(u12t(ru, "ПРОФИЛЬ КОЛЕСА", "WHEEL PROFILE"), color = U12Blue, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(10.dp))
                    V12ProfileRow(u12t(ru, "Протокол", "Protocol"), "KingSong Classic BLE")
                    V12ProfileRow("Smart BMS", when (profile.smartBms) {
                        FeatureSupportV12.NO -> u12t(ru, "Нет", "No")
                        FeatureSupportV12.YES -> u12t(ru, "Есть", "Yes")
                        FeatureSupportV12.UNKNOWN -> u12t(ru, "Проверяется", "Checking")
                    })
                    profile.voltageClass?.let { V12ProfileRow(u12t(ru, "Класс батареи", "Battery class"), it) }
                    profile.seriesCells?.let { V12ProfileRow(u12t(ru, "Последовательно", "Series cells"), "${it}S") }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF121921)),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.border(1.dp, U12Blue.copy(alpha = .28f), RoundedCornerShape(22.dp)),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(u12t(ru, "НАСТРОЙКИ KINGSONG", "KINGSONG SETTINGS"), color = U12Blue, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(7.dp))
                    Text(
                        u12t(
                            ru,
                            "EUC Lab показывает только команды, подтверждённые для протокола этого семейства. LeaperKim-настройки здесь больше не отображаются. Расширенные параметры KingSong будут включаться по модели по мере проверки их readback и диапазонов.",
                            "EUC Lab only shows commands verified for this protocol family. LeaperKim settings are no longer shown here. Advanced KingSong controls will be enabled per model as their readback and ranges are verified.",
                        ),
                        color = Color(0xFFC8D0DA),
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun V12ProfileRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = U12Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun V12UnknownWheelScreen(t: Telemetry?, brand: String, ru: Boolean) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(U12Bg).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Spacer(Modifier.height(28.dp)) }
        item { Text(u12t(ru, "Колесо", "Wheel"), color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = U12Card), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(17.dp)) {
                    Text(t?.model ?: brand, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        u12t(ru, "Протокол подключён в диагностическом режиме. Управляющие команды скрыты, пока семейство и возможности не подтверждены.", "The protocol is connected in diagnostic mode. Control commands stay hidden until the family and capabilities are confirmed."),
                        color = U12Muted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun V12NoSmartBmsBattery(telemetry: Telemetry?, ru: Boolean) {
    val model = telemetry?.model ?: "KingSong"
    val voltage = telemetry?.voltageV ?: 0f
    val percent = telemetry?.batteryPercent ?: 0
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(U12Bg).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Spacer(Modifier.height(28.dp)) }
        item {
            Column {
                Text(u12t(ru, "Батарея", "Battery"), color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                Text("$model · ${u12t(ru, "без Smart BMS", "no Smart BMS")}", color = U12Muted, fontSize = 11.sp)
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = U12Blue.copy(alpha = .10f)),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.border(1.dp, U12Blue.copy(alpha = .32f), RoundedCornerShape(22.dp)),
            ) {
                Column(Modifier.padding(17.dp)) {
                    Text(u12t(ru, "✦ AI-ПОДСКАЗКА · ЛОКАЛЬНАЯ ДИАГНОСТИКА", "✦ AI TIP · LOCAL DIAGNOSTICS"), color = U12Blue, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    Text(u12t(ru, "Smart BMS здесь не завезли 🙂", "No Smart BMS on this generation 🙂"), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        if (voltage > 0f) u12t(
                            ru,
                            "Это колесо старого поколения и не передаёт напряжение отдельных ячеек. Поэтому состояние банок по Bluetooth определить нельзя. Но зато мы узнали общее напряжение — ${String.format(Locale.getDefault(), "%.1f В", voltage)}. Уже не совсем вслепую 😄",
                            "This older wheel does not expose individual cell voltages, so cell health cannot be determined over Bluetooth. But we did get total pack voltage: ${String.format(Locale.getDefault(), "%.1f V", voltage)}. Better than flying completely blind 😄",
                        ) else u12t(
                            ru,
                            "Это колесо старого поколения и не передаёт поклеточные данные. EUC Lab покажет всё, что реально отдаёт контроллер, без выдуманных значений.",
                            "This older wheel does not expose per-cell data. EUC Lab will show only values the controller actually provides.",
                        ),
                        color = Color(0xFFD0D6DF),
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = U12Card), shape = RoundedCornerShape(22.dp)) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(model, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(u12t(ru, "Расчёт по общему напряжению", "Estimated from total voltage"), color = U12Muted, fontSize = 10.sp)
                    }
                    Text("$percent% · ${String.format(Locale.getDefault(), "%.1f V", voltage)}", color = U12Accent, fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = U12Card2), shape = RoundedCornerShape(20.dp)) {
                Text(
                    u12t(ru, "Поклеточные напряжения, баланс и температуры BMS недоступны на этом поколении — поэтому пустые 36 ячеек мы больше не рисуем.", "Cell voltages, balance and BMS temperatures are unavailable on this generation, so EUC Lab no longer draws 36 empty cells."),
                    color = U12Muted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
