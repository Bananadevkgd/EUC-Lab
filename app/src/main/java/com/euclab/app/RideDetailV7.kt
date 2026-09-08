package com.euclab.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.euclab.app.data.RideLog
import com.euclab.app.data.RideSample
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private val R7Bg = Color(0xFF090B0F)
private val R7Card = Color(0xFF11151C)
private val R7Card2 = Color(0xFF171C24)
private val R7Muted = Color(0xFF7C8798)
private val R7Accent = Color(0xFFB6FF35)
private val R7Good = Color(0xFF6DFF9A)
private val R7Danger = Color(0xFFFF5060)
private val R7Amber = Color(0xFFFFC857)
private val R7Blue = Color(0xFF79C7FF)

private fun r7(ru: Boolean, r: String, e: String) = if (ru) r else e
private enum class RouteColorMode { PWM, SPEED }

@Composable
fun V7RideDetail(ride: RideLog, ru: Boolean, back: () -> Unit) {
    val context = LocalContext.current
    val samples = ride.samples
    val last = (samples.size - 1).coerceAtLeast(0)
    var pos by remember(ride.file.absolutePath) { mutableFloatStateOf(0f) }
    var playing by remember(ride.file.absolutePath) { mutableStateOf(false) }
    var mapMode by remember { mutableStateOf(RouteColorMode.PWM) }
    val sample = samples.getOrNull(pos.roundToInt().coerceIn(0, last))

    LaunchedEffect(playing, ride.file.absolutePath) {
        while (playing && pos.roundToInt() < last) {
            delay(100)
            pos = (pos + 1f).coerceAtMost(last.toFloat())
        }
        if (pos.roundToInt() >= last) playing = false
    }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize().background(R7Bg).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Spacer(Modifier.height(24.dp)) }
        item {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                TextButton(onClick = back) { Text("‹ ${r7(ru, "Назад", "Back")}", color = R7Accent) }
                Column(Modifier.weight(1f)) {
                    Text(r7(ru, "Расширенный лог поездки", "Expanded ride log"), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text(ride.file.name, color = R7Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                R7Stat(r7(ru, "Дистанция", "Distance"), String.format(Locale.getDefault(), "%.1f km", ride.distanceKm), Modifier.weight(1f))
                R7Stat(r7(ru, "Макс.", "Max speed"), String.format(Locale.getDefault(), "%.1f", ride.maxSpeedKmh) + " km/h", Modifier.weight(1f))
                R7Stat("MAX PWM", String.format(Locale.getDefault(), "%.0f%%", ride.maxPwm), Modifier.weight(1f))
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = R7Card), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r7(ru, "GPS-МАРШРУТ", "GPS ROUTE"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                            Text(if (ride.gpsSamples.isNotEmpty()) r7(ru, "${ride.gpsSamples.size} GPS-точек", "${ride.gpsSamples.size} GPS points") else r7(ru, "В этом логе GPS ещё нет", "This log has no GPS data"), color = R7Muted, fontSize = 9.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            FilterChip(selected = mapMode == RouteColorMode.PWM, onClick = { mapMode = RouteColorMode.PWM }, label = { Text("PWM", fontSize = 8.sp) })
                            FilterChip(selected = mapMode == RouteColorMode.SPEED, onClick = { mapMode = RouteColorMode.SPEED }, label = { Text(r7(ru, "СКОРОСТЬ", "SPEED"), fontSize = 8.sp) })
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (ride.gpsSamples.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(220.dp).background(Color(0xFF0D1117), RoundedCornerShape(18.dp)), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            Text(r7(ru, "Новые поездки v0.0.7 будут записываться с GPS", "New v0.0.7 rides will include GPS"), color = R7Muted, fontSize = 11.sp)
                        }
                    } else {
                        key(mapMode) { R7Map(ride, mapMode, Modifier.fillMaxWidth().height(270.dp)) }
                    }
                }
            }
        }

        if (samples.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = R7Card), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                            Text(String.format(Locale.getDefault(), "%.1f", sample?.speedKmh?.absoluteValue ?: 0f), color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Black)
                            Text(" km/h", color = R7Muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                            Spacer(Modifier.weight(1f))
                            Text("PWM ${String.format(Locale.getDefault(), "%.0f%%", sample?.pwmPercent ?: 0f)}", color = pwmColor(sample?.pwmPercent ?: 0f), fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                        Slider(value = pos.coerceIn(0f, last.toFloat()), onValueChange = { pos = it; playing = false }, valueRange = 0f..last.toFloat().coerceAtLeast(1f), colors = SliderDefaults.colors(thumbColor = R7Accent, activeTrackColor = R7Accent, inactiveTrackColor = Color(0xFF303743)))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { if (pos.roundToInt() >= last) pos = 0f; playing = !playing }, colors = ButtonDefaults.buttonColors(containerColor = if (playing) R7Danger else R7Accent, contentColor = Color.Black), modifier = Modifier.weight(1f)) {
                                Text(if (playing) r7(ru, "ПАУЗА", "PAUSE") else r7(ru, "ВОСПРОИЗВЕСТИ", "PLAY"), fontSize = 9.sp, fontWeight = FontWeight.Black)
                            }
                            OutlinedButton(onClick = { shareCsv(context, ride) }, modifier = Modifier.weight(1f), border = androidx.compose.foundation.BorderStroke(1.dp, R7Blue), colors = ButtonDefaults.outlinedButtonColors(contentColor = R7Blue)) {
                                Text(r7(ru, "ПОДЕЛИТЬСЯ CSV", "SHARE CSV"), fontSize = 9.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    R7Stat(r7(ru, "Напряжение", "Voltage"), sample?.let { String.format(Locale.getDefault(), "%.1f V", it.voltageV) } ?: "—", Modifier.weight(1f))
                    R7Stat(r7(ru, "Фазный ток", "Phase current"), sample?.let { String.format(Locale.getDefault(), "%.1f A", it.phaseCurrentA) } ?: "—", Modifier.weight(1f))
                    R7Stat(r7(ru, "MOSFET", "MOSFET"), sample?.let { String.format(Locale.getDefault(), "%.1f°C", it.mosfetTempC) } ?: "—", Modifier.weight(1f))
                }
            }

            item { R7GraphCard(r7(ru, "Скорость", "Speed"), samples, { it.speedKmh.absoluteValue }, R7Blue, "km/h") }
            item { R7GraphCard("PWM", samples, { it.pwmPercent }, R7Accent, "%") }
            item { R7GraphCard(r7(ru, "Напряжение", "Voltage"), samples, { it.voltageV }, R7Amber, "V") }
            item { R7GraphCard(r7(ru, "Фазный ток", "Phase current"), samples, { it.phaseCurrentA }, Color(0xFFD6A5FF), "A") }
            item { R7GraphCard(r7(ru, "Температура MOSFET", "MOSFET temperature"), samples, { it.mosfetTempC }, Color(0xFFFF8C66), "°C") }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = R7Card), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(r7(ru, "АВТОМАТИЧЕСКИЕ МЕТКИ", "AUTOMATIC HIGHLIGHTS"), color = R7Muted, fontSize = 9.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(8.dp))
                        R7Line("⚡ MAX PWM", String.format(Locale.getDefault(), "%.0f%%", ride.maxPwm))
                        R7Line("↗ ${r7(ru, "Макс. скорость", "Max speed")}", String.format(Locale.getDefault(), "%.1f km/h", ride.maxSpeedKmh))
                        R7Line("↓ ${r7(ru, "Мин. напряжение", "Min voltage")}", String.format(Locale.getDefault(), "%.1f V", ride.minVoltageV))
                        R7Line("🌡 ${r7(ru, "Макс. MOSFET", "Max MOSFET")}", String.format(Locale.getDefault(), "%.1f°C", ride.maxTempC))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

@Composable
private fun R7Map(ride: RideLog, mode: RouteColorMode, modifier: Modifier) {
    val points = remember(ride.file.absolutePath, mode) { ride.gpsSamples.filter { it.latitude != null && it.longitude != null }.let { if (it.size > 900) it.filterIndexed { i, _ -> i % ((it.size / 900).coerceAtLeast(1)) == 0 } else it } }
    val html = remember(points, mode) { buildMapHtml(points, mode) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                setBackgroundColor(android.graphics.Color.rgb(13, 17, 23))
                webViewClient = WebViewClient()
                loadDataWithBaseURL("https://euc-lab.local/", html, "text/html", "UTF-8", null)
            }
        },
        update = { it.loadDataWithBaseURL("https://euc-lab.local/", html, "text/html", "UTF-8", null) },
    )
}

private fun buildMapHtml(points: List<RideSample>, mode: RouteColorMode): String {
    val center = points.getOrNull(points.size / 2)
    val segments = buildString {
        for (i in 1 until points.size) {
            val a = points[i - 1]; val b = points[i]
            val value = if (mode == RouteColorMode.PWM) b.pwmPercent else b.speedKmh.absoluteValue
            val hue = if (mode == RouteColorMode.PWM) (120 - value.coerceIn(0f, 100f) * 1.2f) else (210 - value.coerceIn(0f, 80f) * 2.1f)
            append("L.polyline([[${a.latitude},${a.longitude}],[${b.latitude},${b.longitude}]],{color:'hsl(${hue.toInt()},90%,55%)',weight:5,opacity:.92}).addTo(map);\n")
        }
    }
    return """
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
        <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
        <style>html,body,#map{height:100%;margin:0;background:#0d1117}.leaflet-control-attribution{font-size:8px}</style></head>
        <body><div id="map"></div><script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script><script>
        var map=L.map('map',{zoomControl:true}).setView([${center?.latitude ?: 0.0},${center?.longitude ?: 0.0}],14);
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap'}).addTo(map);
        $segments
        var pts=[${points.joinToString(",") { "[${it.latitude},${it.longitude}]" }}];
        if(pts.length>1){map.fitBounds(L.latLngBounds(pts),{padding:[18,18]});}
        if(pts.length>0){L.circleMarker(pts[0],{radius:6,color:'#6DFF9A'}).addTo(map);L.circleMarker(pts[pts.length-1],{radius:6,color:'#FF5060'}).addTo(map);}
        </script></body></html>
    """.trimIndent()
}

@Composable
private fun R7GraphCard(title: String, samples: List<RideSample>, selector: (RideSample) -> Float, color: Color, unit: String) {
    val values = remember(samples) { samples.map(selector) }
    val min = values.minOrNull() ?: 0f
    val max = values.maxOrNull() ?: 0f
    Card(colors = CardDefaults.cardColors(containerColor = R7Card), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(15.dp)) {
            Row { Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(String.format(Locale.getDefault(), "%.1f–%.1f %s", min, max, unit), color = R7Muted, fontSize = 9.sp) }
            Spacer(Modifier.height(8.dp))
            Canvas(Modifier.fillMaxWidth().height(92.dp)) {
                if (values.size < 2) return@Canvas
                val span = (max - min).takeIf { it > 0.0001f } ?: 1f
                val path = Path()
                values.forEachIndexed { index, v ->
                    val x = size.width * index / (values.size - 1).toFloat()
                    val y = size.height - ((v - min) / span) * size.height
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color, style = Stroke(width = 3f))
            }
        }
    }
}

@Composable private fun R7Stat(label: String, value: String, modifier: Modifier) { Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = R7Card2), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(12.dp)) { Text(label.uppercase(), color = R7Muted, fontSize = 7.sp, fontWeight = FontWeight.Bold); Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1) } } }
@Composable private fun R7Line(label: String, value: String) { Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text(label, color = Color(0xFFD2D8E1), fontSize = 10.sp, modifier = Modifier.weight(1f)); Text(value, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) } }
private fun pwmColor(p: Float): Color = Color.hsv(120f * (1f - p.coerceIn(0f,100f) / 100f), .8f, 1f)

private fun shareCsv(context: Context, ride: RideLog) {
    runCatching {
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", ride.file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "EUC Lab · CSV"))
    }
}
