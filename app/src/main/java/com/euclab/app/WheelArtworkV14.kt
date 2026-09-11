package com.euclab.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.euclab.app.data.WheelVoltageCatalogV14

/**
 * Built-in fallback artwork that is always available in the APK. It is intentionally
 * schematic rather than pretending to be a photo of a model we did not bundle.
 */
@Composable
fun V14WheelArtwork(model: String, modifier: Modifier = Modifier) {
    val profile = WheelVoltageCatalogV14.match(model)
    val label = when {
        model.contains("RACE", true) -> "RACE"
        model.contains("SHERMAN L", true) -> "SHERMAN L"
        model.contains("LYNX S", true) -> "LYNX S"
        model.contains("LYNX", true) -> "LYNX"
        model.contains("PATTON", true) -> "PATTON"
        model.contains("P6", true) -> "P6"
        model.isNotBlank() -> model.removePrefix("Begode ").removePrefix("Veteran ").take(14).uppercase()
        else -> "EUC"
    }

    Box(
        modifier = modifier.background(Color(0xFF11151C), RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 34.dp, vertical = 14.dp)) {
            val cx = size.width * .50f
            val cy = size.height * .54f
            val r = minOf(size.width, size.height) * .30f
            val accent = Color(0xFFB6FF35)
            val body = Color(0xFF252D37)
            val edge = Color(0xFF6B7787)

            drawCircle(Color(0xFF050608), r, Offset(cx, cy))
            drawCircle(edge, r, Offset(cx, cy), style = Stroke(width = 3.dp.toPx()))
            drawCircle(Color(0xFF171C24), r * .60f, Offset(cx, cy))

            // Side battery/controller shells.
            drawRoundRect(
                body,
                topLeft = Offset(cx - r * .93f, cy - r * .72f),
                size = Size(r * .58f, r * 1.34f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
            )
            drawRoundRect(
                body,
                topLeft = Offset(cx + r * .35f, cy - r * .72f),
                size = Size(r * .58f, r * 1.34f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
            )

            // Suspension rails / upper handle silhouette.
            drawLine(edge, Offset(cx - r * .55f, cy - r * .82f), Offset(cx - r * .25f, cy - r * 1.18f), 5.dp.toPx(), StrokeCap.Round)
            drawLine(edge, Offset(cx + r * .55f, cy - r * .82f), Offset(cx + r * .25f, cy - r * 1.18f), 5.dp.toPx(), StrokeCap.Round)
            drawLine(accent.copy(alpha = .7f), Offset(cx - r * .28f, cy - r * 1.18f), Offset(cx + r * .28f, cy - r * 1.18f), 4.dp.toPx(), StrokeCap.Round)

            // Pedals.
            drawLine(edge, Offset(cx - r * .62f, cy + r * .50f), Offset(cx - r * 1.12f, cy + r * .66f), 7.dp.toPx(), StrokeCap.Round)
            drawLine(edge, Offset(cx + r * .62f, cy + r * .50f), Offset(cx + r * 1.12f, cy + r * .66f), 7.dp.toPx(), StrokeCap.Round)
        }

        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
        ) {
            Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
            profile?.let {
                Text("${it.brand} · ${formatV14(it.fullVoltageV)} V · ${it.seriesCells}S", color = Color(0xFF7C8798), fontSize = 9.sp)
            }
        }
    }
}

private fun formatV14(v: Float): String = if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(java.util.Locale.US, v)
