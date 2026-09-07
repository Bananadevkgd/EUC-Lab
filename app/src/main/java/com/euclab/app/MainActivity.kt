package com.euclab.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.euclab.app.ble.BleWheelManager
import com.euclab.app.data.BleCandidate
import com.euclab.app.data.LinkState
import com.euclab.app.data.Telemetry
import com.euclab.app.data.WheelRepository
import com.euclab.app.service.RideRecorderService
import kotlin.math.absoluteValue

class MainActivity : ComponentActivity() {
    private lateinit var ble: BleWheelManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ble = BleWheelManager(applicationContext)
        setContent {
            AppTheme {
                Dashboard(ble)
            }
        }
    }

    override fun onDestroy() {
        ble.stopScan()
        super.onDestroy()
    }
}

@Composable
private fun Dashboard(ble: BleWheelManager) {
    val telemetry by WheelRepository.telemetry.collectAsState()
    val linkState by WheelRepository.linkState.collectAsState()
    val linkMessage by WheelRepository.linkMessage.collectAsState()
    val rawPacket by WheelRepository.rawPacket.collectAsState()
    val recording by WheelRepository.recording.collectAsState()
    val lastLog by WheelRepository.lastLogPath.collectAsState()
    val candidates by ble.candidates.collectAsState()
    var demo by remember { mutableStateOf(telemetry == null) }
    val context = LocalContext.current

    val demoTelemetry = Telemetry(
        timestampMs = System.currentTimeMillis(),
        speedKmh = 47.2f,
        voltageV = 146.8f,
        phaseCurrentA = 18.4f,
        mosfetTempC = 43.6f,
        pitchDeg = 1.3f,
        pwmPercent = 54.0f,
        tripKm = 28.7f,
        totalKm = 0f,
        firmwareRaw = 8000,
        charging = false,
    )
    val shown = if (demo) demoTelemetry else telemetry

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) ble.startScan()
    }

    fun scan() {
        if (ble.canScan() && ble.canConnect()) {
            ble.startScan()
        } else {
            val permissions = buildList {
                if (Build.VERSION.SDK_INT >= 31) {
                    add(Manifest.permission.BLUETOOTH_SCAN)
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                } else {
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }.toTypedArray()
            permissionLauncher.launch(permissions)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090B0F))
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(Modifier.height(38.dp)) }
        item {
            Header(
                demo = demo,
                linkState = linkState,
                onToggleDemo = { demo = !demo },
            )
        }
        item {
            SpeedHero(shown)
        }
        item {
            MetricGrid(shown)
        }
        item {
            StatusCard(linkMessage, rawPacket, telemetry != null)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { scan() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF20242C))
                ) {
                    Text(if (linkState == LinkState.SCANNING) "SCANNING…" else "SCAN / CONNECT")
                }
                Button(
                    onClick = {
                        if (recording) {
                            val intent = Intent(context, RideRecorderService::class.java)
                                .setAction(RideRecorderService.ACTION_STOP)
                            context.startService(intent)
                        } else {
                            val intent = Intent(context, RideRecorderService::class.java)
                                .setAction(RideRecorderService.ACTION_START)
                            ContextCompat.startForegroundService(context, intent)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (recording) Color(0xFFFF4D5A) else Color(0xFFB8FF39),
                        contentColor = Color(0xFF090B0F)
                    )
                ) {
                    Text(if (recording) "STOP RIDE" else "START RIDE", fontWeight = FontWeight.Bold)
                }
            }
        }
        if (candidates.isNotEmpty()) {
            item {
                Text("NEARBY BLE", color = Color(0xFF8E97A8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            items(candidates, key = { it.address }) { candidate ->
                DeviceRow(candidate) {
                    demo = false
                    ble.connect(candidate.address)
                }
            }
        }
        if (lastLog != null) {
            item {
                Text(
                    "Last log: ${lastLog?.substringAfterLast('/')}",
                    color = Color(0xFF6E7788),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        item { Spacer(Modifier.height(42.dp)) }
    }
}

@Composable
private fun Header(demo: Boolean, linkState: LinkState, onToggleDemo: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("EUC LAB", color = Color.White, fontWeight = FontWeight.Black, fontSize = 21.sp)
            Text("Sherman L · v0.0.1", color = Color(0xFF7D8798), fontSize = 13.sp)
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (demo) Color(0xFF2B313B) else Color(0xFF183623))
                .clickable(onClick = onToggleDemo)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (demo) Color(0xFFFFC857) else Color(0xFF6DFF9A))
            )
            Text(if (demo) "DEMO" else linkState.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SpeedHero(t: Telemetry?) {
    val speed = t?.speedKmh ?: 0f
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(30.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(listOf(Color(0xFF191D25), Color(0xFF11141A))),
                shape = RoundedCornerShape(30.dp)
            )
    ) {
        Column(Modifier.padding(24.dp)) {
            Text("SPEED", color = Color(0xFF8791A2), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    String.format("%.1f", speed.absoluteValue),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 76.sp,
                    lineHeight = 78.sp
                )
                Text(" km/h", color = Color(0xFF8A94A6), fontSize = 18.sp, modifier = Modifier.padding(bottom = 14.dp))
            }
            val pwm = t?.pwmPercent ?: 0f
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0xFF282D36))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((pwm.coerceIn(0f, 100f) / 100f))
                        .height(8.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF62E6FF), Color(0xFFB8FF39), Color(0xFFFFC857), Color(0xFFFF4D5A))
                            )
                        )
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("PWM ${String.format("%.0f", pwm)}%", color = Color(0xFFB7BFCD), fontSize = 13.sp)
        }
    }
}

@Composable
private fun MetricGrid(t: Telemetry?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("VOLTAGE", t?.let { "%.1f V".format(it.voltageV) } ?: "—", Modifier.weight(1f))
            Metric("PHASE", t?.let { "%.1f A".format(it.phaseCurrentA) } ?: "—", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("MOSFET", t?.let { "%.1f °C".format(it.mosfetTempC) } ?: "—", Modifier.weight(1f))
            Metric("PITCH", t?.let { "%.2f°".format(it.pitchDeg) } ?: "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141820)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(17.dp)) {
            Text(label, color = Color(0xFF737D8E), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text(value, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatusCard(message: String, rawPacket: String, hasTelemetry: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10131A)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(17.dp)) {
            Text(if (hasTelemetry) "● LIVE TELEMETRY" else "● LINK STATUS", color = if (hasTelemetry) Color(0xFF79F29C) else Color(0xFF8D97A8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text(message, color = Color(0xFFD6DBE5), fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Text("RAW · $rawPacket", color = Color(0xFF5F6878), fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DeviceRow(candidate: BleCandidate, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (candidate.likelyEuc) Color(0xFF17221A) else Color(0xFF12161D))
            .clickable(onClick = onClick)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(candidate.name, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(candidate.address, color = Color(0xFF687283), fontSize = 11.sp)
        }
        Text("${candidate.rssi} dBm", color = if (candidate.likelyEuc) Color(0xFF9EF2A8) else Color(0xFF7C8696), fontSize = 12.sp)
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            background = Color(0xFF090B0F),
            surface = Color(0xFF11141A),
        ),
        content = { Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF090B0F)) { content() } }
    )
}
