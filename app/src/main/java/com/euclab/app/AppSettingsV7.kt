package com.euclab.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.euclab.app.ble.BleWheelManager
import kotlin.math.roundToInt

@Composable
fun V7AppSettingsExtras(ble: BleWheelManager, ru: Boolean) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("euc_lab_settings", Context.MODE_PRIVATE) }
    var autoConnect by remember { mutableStateOf(ble.isAutoConnectEnabled()) }
    var pwmAlarm by remember { mutableStateOf(prefs.getBoolean("app_pwm_alarm_enabled", true)) }
    var pwmThreshold by remember { mutableFloatStateOf(prefs.getInt("app_pwm_alarm_threshold", 75).toFloat()) }
    val gpsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF11151C)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(17.dp)) {
            Text(if (ru) "ПОДКЛЮЧЕНИЕ" else "CONNECTION", color = Color(0xFF7C8798), fontSize = 10.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(9.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text(if (ru) "Автоподключение к последнему колесу" else "Auto-connect last wheel", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(if (ru) "При запуске EUC Lab сам найдёт сохранённое колесо и восстановит BLE-связь." else "EUC Lab scans for the saved wheel and restores BLE on launch.", color = Color(0xFF7C8798), fontSize = 9.sp, lineHeight = 13.sp)
                }
                Switch(checked = autoConnect, onCheckedChange = { autoConnect = it; ble.setAutoConnectEnabled(it); if (it) ble.startAutoConnect() }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color(0xFFB6FF35)))
            }
            val saved = ble.rememberedWheelName() ?: ble.rememberedWheelAddress()
            if (saved != null) Text((if (ru) "Сохранено: " else "Saved: ") + saved, color = Color(0xFF79C7FF), fontSize = 9.sp)
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF11151C)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(17.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text(if (ru) "Предупреждение EUC Lab по PWM" else "EUC Lab PWM warning", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(if (ru) "Отдельный сигнал телефона, независимо от штатных писков колеса." else "A phone alarm independent of the wheel's built-in beeps.", color = Color(0xFF7C8798), fontSize = 9.sp)
                }
                Switch(checked = pwmAlarm, onCheckedChange = { pwmAlarm = it; prefs.edit().putBoolean("app_pwm_alarm_enabled", it).apply() }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color(0xFFB6FF35)))
            }
            Spacer(Modifier.height(8.dp))
            Row {
                Text(if (ru) "Первый сигнал" else "First warning", color = Color(0xFF7C8798), fontSize = 10.sp, modifier = Modifier.weight(1f))
                Text("${pwmThreshold.roundToInt()}% PWM", color = Color(0xFFFFC857), fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
            Slider(value = pwmThreshold, onValueChange = { pwmThreshold = it }, onValueChangeFinished = { prefs.edit().putInt("app_pwm_alarm_threshold", pwmThreshold.roundToInt()).apply() }, valueRange = 50f..90f, enabled = pwmAlarm, colors = SliderDefaults.colors(thumbColor = Color(0xFFFFC857), activeTrackColor = Color(0xFFFFC857), inactiveTrackColor = Color(0xFF303743)))
            Text(if (ru) "Дальше сигнал автоматически становится чаще и жёстче после 85% и 92% PWM." else "The warning automatically escalates above 85% and 92% PWM.", color = Color(0xFF7C8798), fontSize = 9.sp)
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF11151C)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(17.dp)) {
            Text(if (ru) "GPS И ФАЙЛЫ ПОЕЗДОК" else "GPS & RIDE FILES", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(if (gpsGranted) (if (ru) "● GPS разрешён — новые логи будут содержать координаты." else "● GPS permission granted — new logs include coordinates.") else (if (ru) "● GPS не разрешён. При следующем запуске Android попросит доступ к геопозиции." else "● GPS permission missing. Android will request location access on next launch."), color = if (gpsGranted) Color(0xFF6DFF9A) else Color(0xFFFFC857), fontSize = 10.sp, lineHeight = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text(if (ru) "После остановки лога копия CSV сохраняется в: Download/EUC Lab/Rides" else "After stopping a log, a CSV copy is exported to: Download/EUC Lab/Rides", color = Color(0xFF7C8798), fontSize = 9.sp)
        }
    }
}
