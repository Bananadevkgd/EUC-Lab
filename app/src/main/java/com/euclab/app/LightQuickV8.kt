package com.euclab.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.euclab.app.ble.BleWheelManager
import com.euclab.app.data.Telemetry
import com.euclab.app.data.WheelCapabilitiesV8

private val L8Accent = Color(0xFFB6FF35)
private val L8Muted = Color(0xFF7C8798)
private val L8Surface = Color(0xFF202630)
private val L8On = Color(0xFF243318)

@Composable
fun V8LightQuick(
    telemetry: Telemetry?,
    lightOn: Boolean,
    highBeamOn: Boolean,
    ru: Boolean,
    ble: BleWheelManager,
) {
    val online = telemetry != null
    val caps = WheelCapabilitiesV8.forModel(telemetry?.model)

    AnimatedContent(
        targetState = caps.supportsHighBeam,
        label = "lynx_s_headlight",
        modifier = Modifier.fillMaxWidth().animateContentSize(tween(260)),
    ) { lynxS ->
        if (!lynxS) {
            LightHalf(
                modifier = Modifier.fillMaxWidth(),
                title = if (ru) "ФАРА" else "HEADLIGHT",
                subtitle = when {
                    !online -> if (ru) "Сначала подключи колесо" else "Connect the wheel first"
                    lightOn -> if (ru) "Включена · нажми, чтобы выключить" else "On · tap to switch off"
                    else -> if (ru) "Выключена · нажми, чтобы включить" else "Off · tap to switch on"
                },
                active = lightOn,
                enabled = online,
                stateText = if (lightOn) "ON" else "OFF",
                onClick = { ble.setLight(!lightOn) },
            )
        } else {
            // Lynx S mirrors the physical power-button sequence:
            // OFF -> low beam -> high beam -> OFF.
            val title = when {
                !lightOn -> if (ru) "ФАРА · ВЫКЛ" else "LIGHT · OFF"
                highBeamOn -> if (ru) "ДАЛЬНИЙ" else "HIGH BEAM"
                else -> if (ru) "БЛИЖНИЙ" else "LOW BEAM"
            }
            val state = when {
                !lightOn -> "OFF"
                highBeamOn -> if (ru) "ДАЛЬНИЙ" else "HIGH"
                else -> if (ru) "БЛИЖНИЙ" else "LOW"
            }
            LightHalf(
                modifier = Modifier.fillMaxWidth(),
                title = title,
                subtitle = if (!online) {
                    if (ru) "Сначала подключи колесо" else "Connect the wheel first"
                } else if (ru) {
                    "Нажатие: OFF → ближний → дальний → OFF"
                } else {
                    "Tap: OFF → low → high → OFF"
                },
                active = lightOn,
                enabled = online,
                stateText = state,
                onClick = {
                    when {
                        !lightOn -> ble.setLight(true)
                        !highBeamOn -> ble.setHighBeam(true)
                        else -> {
                            // Return to OFF in the same order as the wheel: remove high beam,
                            // then switch the base/low beam off.
                            ble.setHighBeam(false)
                            ble.setLight(false)
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun LightHalf(
    modifier: Modifier,
    title: String,
    subtitle: String,
    active: Boolean,
    enabled: Boolean,
    stateText: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (active) L8On else L8Surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            TextSymbol(active)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                androidx.compose.material3.Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                androidx.compose.material3.Text(subtitle, color = if (enabled) L8Muted else Color(0xFF59616D), fontSize = 9.sp, lineHeight = 12.sp)
            }
            androidx.compose.material3.Text(stateText, color = if (active) L8Accent else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun TextSymbol(active: Boolean) {
    Box(
        Modifier.size(30.dp).background(if (active) L8Accent.copy(alpha = .14f) else Color.Transparent, RoundedCornerShape(99.dp)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(if (active) "☀" else "◌", color = if (active) L8Accent else Color.White, fontSize = 22.sp)
    }
}
