package com.euclab.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.euclab.app.data.RideLog
import kotlin.math.cos

/**
 * Native route renderer. No WebView, JS, Leaflet or network is required for the route.
 * Street tiles can be layered underneath later; the user's GPS trace must never depend
 * on a web runtime just to be visible.
 */
@Composable
fun V14NativeRouteMap(
    ride: RideLog,
    colorByPwm: Boolean,
    modifier: Modifier = Modifier,
) {
    val source = remember(ride.file.absolutePath, colorByPwm) {
        val all = ride.gpsSamples
        if (all.size <= 1600) all else {
            val step = (all.size / 1600).coerceAtLeast(1)
            all.filterIndexed { index, _ -> index % step == 0 }
        }
    }

    val projected = remember(source) {
        if (source.isEmpty()) emptyList()
        else {
            val meanLatRad = Math.toRadians(source.mapNotNull { it.latitude }.average())
            val lonScale = cos(meanLatRad).coerceAtLeast(0.15)
            source.mapNotNull { p ->
                val lat = p.latitude ?: return@mapNotNull null
                val lon = p.longitude ?: return@mapNotNull null
                Triple(lon * lonScale, lat, p)
            }
        }
    }

    Box(modifier = modifier.background(Color(0xFF0A0D12))) {
        if (projected.isNotEmpty()) {
            Canvas(Modifier.matchParentSize()) {
                val grid = Color(0xFF26303B).copy(alpha = .55f)
                val gridStroke = 1.dp.toPx()
                for (i in 1 until 6) {
                    val x = size.width * i / 6f
                    val y = size.height * i / 6f
                    drawLine(grid, Offset(x, 0f), Offset(x, size.height), gridStroke)
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), gridStroke)
                }

                val minX = projected.minOf { it.first }
                val maxX = projected.maxOf { it.first }
                val minY = projected.minOf { it.second }
                val maxY = projected.maxOf { it.second }
                val dx = (maxX - minX).takeIf { it > 1e-9 } ?: 1e-9
                val dy = (maxY - minY).takeIf { it > 1e-9 } ?: 1e-9
                val pad = 24.dp.toPx()
                val drawW = (size.width - pad * 2).coerceAtLeast(1f)
                val drawH = (size.height - pad * 2).coerceAtLeast(1f)

                fun point(index: Int): Offset {
                    val p = projected[index]
                    val x = pad + ((p.first - minX) / dx).toFloat() * drawW
                    val y = pad + (1f - ((p.second - minY) / dy).toFloat()) * drawH
                    return Offset(x, y)
                }

                fun routeColor(value: Float): Color {
                    val hue = if (colorByPwm) {
                        120f - value.coerceIn(0f, 100f) * 1.2f
                    } else {
                        210f - value.coerceIn(0f, 80f) * 2.1f
                    }
                    return Color.hsv(hue.coerceIn(0f, 240f), .92f, 1f)
                }

                val stroke = 4.5.dp.toPx()
                for (i in 1 until projected.size) {
                    val sample = projected[i].third
                    val value = if (colorByPwm) sample.pwmPercent else kotlin.math.abs(sample.speedKmh)
                    drawLine(
                        color = routeColor(value),
                        start = point(i - 1),
                        end = point(i),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }

                val dotR = 6.dp.toPx()
                drawCircle(Color(0xFF6DFF9A), dotR, point(0))
                drawCircle(Color(0xFFFF5060), dotR, point(projected.lastIndex))
                drawCircle(Color(0xFF0A0D12), dotR * .45f, point(0))
                drawCircle(Color(0xFF0A0D12), dotR * .45f, point(projected.lastIndex))
            }

            Text(
                text = "${if (colorByPwm) "PWM" else "SPEED"} · ${projected.size} GPS · NATIVE",
                color = Color(0xFFD0D6DF),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
            )
        } else {
            Text(
                text = "Нет GPS-точек в этом логе",
                color = Color(0xFF7C8798),
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}
