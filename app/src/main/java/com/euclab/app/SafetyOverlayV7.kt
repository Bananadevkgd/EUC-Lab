package com.euclab.app

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.euclab.app.ble.BleWheelManager
import com.euclab.app.data.WheelRepository
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue

@Composable
fun V7SafetyOverlay(ble: BleWheelManager) {
    val telemetry by WheelRepository.telemetry.collectAsState()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("euc_lab_settings", Context.MODE_PRIVATE) }
    val alarmEnabled = remember { prefs.getBoolean("app_pwm_alarm_enabled", true) }
    val alarmThreshold = remember { prefs.getInt("app_pwm_alarm_threshold", 75).coerceIn(50, 95) }
    val tone = remember { ToneGenerator(AudioManager.STREAM_ALARM, 92) }
    DisposableEffect(Unit) { onDispose { tone.release() } }

    val pwm = telemetry?.pwmPercent ?: 0f
    val speed = telemetry?.speedKmh?.absoluteValue ?: 0f
    val band = when {
        !alarmEnabled || speed < 3f || pwm < alarmThreshold -> 0
        pwm >= 92f -> 3
        pwm >= 85f -> 2
        else -> 1
    }

    LaunchedEffect(band, telemetry != null) {
        if (band == 0) return@LaunchedEffect
        while (true) {
            when (band) {
                1 -> tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                2 -> tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 180)
                else -> tone.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 260)
            }
            delay(when (band) { 1 -> 3000L; 2 -> 1500L; else -> 700L })
        }
    }

    val autoOff = telemetry?.autoOffSec ?: 0
    val autoOffWarning = telemetry != null && !telemetry!!.charging && speed < 1f && autoOff in 1..60
    var autoOffArmed by remember { mutableStateOf(true) }
    LaunchedEffect(autoOff, telemetry?.timestampMs) {
        if (autoOff > 90 || autoOff == 0) autoOffArmed = true
        if (autoOffWarning && autoOffArmed) {
            autoOffArmed = false
            ble.beep()
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 220)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedVisibility(
            visible = autoOffWarning,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            val mm = autoOff / 60
            val ss = autoOff % 60
            WarningPill(
                color = Color(0xFFFFC857),
                title = "АВТОВЫКЛЮЧЕНИЕ ЧЕРЕЗ %d:%02d".format(mm, ss),
                body = "Поставь колесо на подножку — питание может отключиться.",
            )
        }
        AnimatedVisibility(
            visible = band > 0,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            val color = when (band) { 1 -> Color(0xFFFFD44A); 2 -> Color(0xFFFF8C42); else -> Color(0xFFFF4055) }
            WarningPill(
                color = color,
                title = "PWM ${pwm.toInt()}% · ПРЕДУПРЕЖДЕНИЕ EUC LAB",
                body = when (band) {
                    1 -> "Запас мощности уменьшается."
                    2 -> "Высокая нагрузка — снизь скорость."
                    else -> "КРИТИЧЕСКИЙ PWM — немедленно разгрузи колесо."
                },
            )
        }
    }
}

@Composable
private fun WarningPill(color: Color, title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xF21A1E25))
            .border(1.dp, color.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(99.dp)).background(color))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = color, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text(body, color = Color(0xFFD7DCE4), fontSize = 9.sp)
        }
    }
}
