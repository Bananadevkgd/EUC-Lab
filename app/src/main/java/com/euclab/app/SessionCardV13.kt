package com.euclab.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.euclab.app.data.SessionTrackerV13
import java.util.Locale

private val S13Surface = Color(0xFF11151C)
private val S13Surface2 = Color(0xFF171C24)
private val S13Muted = Color(0xFF7C8798)
private val S13Accent = Color(0xFFB6FF35)
private val S13Blue = Color(0xFF79C7FF)

@Composable
fun V13SessionCard(ru: Boolean) {
    val s by SessionTrackerV13.state.collectAsState()
    if (s.samples <= 0) return

    Card(colors = CardDefaults.cardColors(containerColor = S13Surface), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (ru) "СЕССИЯ БЕЗ ЛОГА" else "LIVE SESSION · NO LOG", color = S13Blue, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text(if (ru) "Считается автоматически, пока колесо подключено" else "Tracked automatically while the wheel is connected", color = S13Muted, fontSize = 9.sp)
                }
                Text(
                    if (ru) "СБРОС" else "RESET",
                    color = S13Accent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .background(S13Surface2, RoundedCornerShape(12.dp))
                        .clickable { SessionTrackerV13.resetKeepingWheel() }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                S13Tile(if (ru) "ПРОЕХАЛ" else "DISTANCE", String.format(Locale.getDefault(), "%.2f km", s.distanceKm), Modifier.weight(1f))
                S13Tile(if (ru) "MAX PWM" else "MAX PWM", String.format(Locale.getDefault(), "%.0f%%", s.maxPwmPercent), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                S13Tile(if (ru) "MAX СКОРОСТЬ" else "MAX SPEED", String.format(Locale.getDefault(), "%.1f km/h", s.maxSpeedKmh), Modifier.weight(1f))
                S13Tile(if (ru) "MIN НАПР." else "MIN VOLTAGE", if (s.minVoltageV > 0f) String.format(Locale.getDefault(), "%.1f V", s.minVoltageV) else "—", Modifier.weight(1f))
            }
            if (s.maxMosfetTempC > 0f) {
                Spacer(Modifier.height(8.dp))
                Text(
                    (if (ru) "Макс. температура: " else "Max temperature: ") + String.format(Locale.getDefault(), "%.1f °C", s.maxMosfetTempC),
                    color = S13Muted,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun S13Tile(label: String, value: String, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = S13Surface2), shape = RoundedCornerShape(17.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = S13Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
    }
}
