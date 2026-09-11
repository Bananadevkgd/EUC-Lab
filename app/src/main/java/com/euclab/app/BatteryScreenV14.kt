package com.euclab.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.euclab.app.data.BmsPack
import com.euclab.app.data.WheelRepository
import com.euclab.app.data.WheelVoltageCatalogV14
import java.util.Locale

private val B14Bg = Color(0xFF090B0F)
private val B14Card = Color(0xFF11151C)
private val B14Card2 = Color(0xFF171C24)
private val B14Muted = Color(0xFF7C8798)
private val B14Accent = Color(0xFFB6FF35)
private val B14Blue = Color(0xFF79C7FF)

@Composable
fun V14BatteryScreen(ru: Boolean) {
    val telemetry by WheelRepository.telemetry.collectAsState()
    val bms by WheelRepository.bms.collectAsState()
    val model = telemetry?.model ?: if (ru) "Колесо не подключено" else "Wheel not connected"
    val profile = WheelVoltageCatalogV14.match(telemetry?.model)
    val packs = bms?.packs.orEmpty().filter { pack ->
        pack.validCells.isNotEmpty() || pack.currentA != null || pack.temperaturesC.isNotEmpty()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(B14Bg).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Spacer(Modifier.height(28.dp)) }
        item {
            Column {
                Text("Smart BMS", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                Text(
                    buildString {
                        append(model)
                        profile?.let {
                            append(" · ${fmt1(it.fullVoltageV)} V · ${it.seriesCells}S")
                            it.smartBmsPacks?.let { count -> append(" · BMS ×$count") }
                        }
                    },
                    color = B14Muted,
                    fontSize = 11.sp,
                )
            }
        }

        telemetry?.let { t ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = B14Card), shape = RoundedCornerShape(24.dp)) {
                    Row(Modifier.fillMaxWidth().padding(17.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(t.model, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (profile != null) {
                                    if (ru) "Профиль ${fmt1(profile.fullVoltageV)} В · ${profile.seriesCells}S"
                                    else "${fmt1(profile.fullVoltageV)} V · ${profile.seriesCells}S profile"
                                } else {
                                    if (ru) "Профиль батареи уточняется" else "Resolving battery profile"
                                },
                                color = B14Muted,
                                fontSize = 10.sp,
                            )
                        }
                        Text(
                            "${t.batteryPercent}% · ${fmt1(t.voltageV)} V",
                            color = B14Accent,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
        }

        if (packs.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = B14Blue.copy(alpha = .10f)), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(17.dp)) {
                        Text(
                            if (ru) "✦ ДИАГНОСТИКА SMART BMS" else "✦ SMART BMS DIAGNOSTICS",
                            color = B14Blue,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Spacer(Modifier.height(8.dp))
                        val expected = profile?.smartBmsPacks
                        Text(
                            when {
                                expected != null && expected > 0 -> if (ru) "Жду BMS-пакеты: ожидается $expected" else "Waiting for BMS packs: $expected expected"
                                else -> if (ru) "Поклеточные данные пока не пришли" else "No per-cell data received yet"
                            },
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (ru) "EUC Lab больше не подставляет схему Sherman L. Здесь появятся только пакеты и ячейки, реально полученные по BLE."
                            else "EUC Lab no longer assumes a Sherman L battery layout. Only packs and cells actually received over BLE will appear here.",
                            color = Color(0xFFD0D6DF),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }
        } else {
            items(packs.size) { index ->
                V14PackCard(packs[index], ru)
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun V14PackCard(pack: BmsPack, ru: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = B14Card2), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    if (ru) "БАТАРЕЯ ${pack.index}" else "BATTERY ${pack.index}",
                    color = B14Blue,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (pack.expectedCells != null) "${pack.validCells.size}/${pack.expectedCells}S" else "${pack.validCells.size} cells",
                    color = if (pack.complete) B14Accent else B14Muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(9.dp))
            val min = pack.minCellV
            val max = pack.maxCellV
            val delta = pack.deltaV
            Text(
                when {
                    min != null && max != null && delta != null ->
                        "min ${fmt3(min)} V · max ${fmt3(max)} V · Δ ${String.format(Locale.US, "%.0f", delta * 1000f)} mV"
                    else -> if (ru) "Поклеточные страницы ещё собираются…" else "Collecting cell pages…"
                },
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            pack.totalV?.let {
                Spacer(Modifier.height(5.dp))
                Text(
                    if (ru) "Сумма полученных ячеек: ${fmt1(it)} В" else "Received cell sum: ${fmt1(it)} V",
                    color = B14Muted,
                    fontSize = 10.sp,
                )
            }
            pack.currentA?.let {
                Text("I ${fmt1(it)} A", color = B14Muted, fontSize = 10.sp)
            }
            if (pack.temperaturesC.isNotEmpty()) {
                Text(
                    "T ${pack.temperaturesC.joinToString(" / ") { "${fmt1(it)}°C" }}",
                    color = B14Muted,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

private fun fmt1(value: Float): String = String.format(Locale.US, "%.1f", value)
private fun fmt3(value: Float): String = String.format(Locale.US, "%.3f", value)
