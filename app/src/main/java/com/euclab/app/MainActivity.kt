package com.euclab.app

import android.Manifest
import android.content.Context
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    val recording by WheelRepository.recording.collectAsState()
    val lastLog by WheelRepository.lastLogPath.collectAsState()
    val candidates by ble.candidates.collectAsState()
    var demo by remember { mutableStateOf(telemetry == null) }
    var showDebug by remember { mutableStateOf(false) }
    var showAllBle by remember { mutableStateOf(false) }
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
        totalKm = 1246.3f,
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
        showAllBle = false
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
        item { SpeedHero(shown) }
        item { MetricGrid(shown) }
        item {
            ConnectionCard(
                linkState = linkState,
                message = linkMessage,
                hasTelemetry = telemetry != null,
                recording = recording,
                showDebug = showDebug,
                onToggleDebug = { showDebug = !showDebug },
            )
        }
        if (showDebug) {
            item { DebugRawCard() }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { scan() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF20242C))
                ) {
                    Text(
                        when (linkState) {
                            LinkState.SCANNING -> "SCANNING…"
                            LinkState.CONNECTED -> "CHANGE WHEEL"
                            else -> "SCAN / CONNECT"
                        }
                    )
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
                    Text(if (recording) "STOP LOG" else "START LOG", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (linkState == LinkState.SCANNING && candidates.isNotEmpty()) {
            val eucCandidates = candidates.filter { it.likelyEuc }
            val shownCandidates = if (showAllBle) candidates else eucCandidates

            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "NEARBY EUC",
                        modifier = Modifier.weight(1f),
                        color = Color(0xFF8E97A8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (candidates.size > eucCandidates.size) {
                        Text(
                            if (showAllBle) "EUC ONLY" else "SHOW ALL (${candidates.size})",
                            modifier = Modifier.clickable { showAllBle = !showAllBle },
                            color = Color(0xFFB8FF39),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (shownCandidates.isEmpty()) {
                item {
                    Text(
                        "No EUC-looking devices yet. Tap SHOW ALL if your wheel uses an unusual BLE name.",
                        color = Color(0xFF737D8E),
                        fontSize = 12.sp
                    )
                }
            } else {
                items(shownCandidates, key = { it.address }) { candidate ->
                    DeviceRow(candidate) {
                        demo = false
                        ble.connect(candidate.address)
                    }
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
            Text("Sherman L · v0.0.2", color = Color(0xFF7D8798), fontSize = 13.sp)
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

private enum class MetricKind(val title: String) {
    VOLTAGE("VOLTAGE"),
    PHASE("PHASE CURRENT"),
    MOSFET("MOSFET TEMP"),
    PITCH("PITCH"),
    PWM("PWM"),
    TRIP("TRIP"),
    ODOMETER("ODOMETER"),
    FIRMWARE("FIRMWARE RAW"),
    CHARGING("CHARGING");

    fun value(t: Telemetry?): String = when (this) {
        VOLTAGE -> t?.let { "%.1f V".format(it.voltageV) } ?: "—"
        PHASE -> t?.let { "%.1f A".format(it.phaseCurrentA) } ?: "—"
        MOSFET -> t?.let { "%.1f °C".format(it.mosfetTempC) } ?: "—"
        PITCH -> t?.let { "%.2f°".format(it.pitchDeg) } ?: "—"
        PWM -> t?.let { "%.0f %%".format(it.pwmPercent) } ?: "—"
        TRIP -> t?.let { "%.2f km".format(it.tripKm) } ?: "—"
        ODOMETER -> t?.let { "%.1f km".format(it.totalKm) } ?: "—"
        FIRMWARE -> t?.firmwareRaw?.toString() ?: "—"
        CHARGING -> t?.let { if (it.charging) "YES" else "NO" } ?: "—"
    }
}

@Composable
private fun MetricGrid(t: Telemetry?) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("dashboard_metrics", Context.MODE_PRIVATE) }
    val defaults = listOf(MetricKind.VOLTAGE, MetricKind.PHASE, MetricKind.MOSFET, MetricKind.PITCH)
    var selected by remember {
        mutableStateOf(
            defaults.mapIndexed { index, fallback ->
                runCatching {
                    MetricKind.valueOf(prefs.getString("slot_$index", fallback.name) ?: fallback.name)
                }.getOrDefault(fallback)
            }
        )
    }
    var pickingSlot by remember { mutableStateOf<Int?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric(selected[0], t, Modifier.weight(1f)) { pickingSlot = 0 }
            Metric(selected[1], t, Modifier.weight(1f)) { pickingSlot = 1 }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric(selected[2], t, Modifier.weight(1f)) { pickingSlot = 2 }
            Metric(selected[3], t, Modifier.weight(1f)) { pickingSlot = 3 }
        }
        Text(
            "Tap any tile to choose what it shows",
            color = Color(0xFF5F6878),
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }

    pickingSlot?.let { slot ->
        MetricPickerDialog(
            current = selected[slot],
            onDismiss = { pickingSlot = null },
            onSelect = { kind ->
                val next = selected.toMutableList().apply { this[slot] = kind }
                selected = next
                prefs.edit().putString("slot_$slot", kind.name).apply()
                pickingSlot = null
            }
        )
    }
}

@Composable
private fun Metric(kind: MetricKind, t: Telemetry?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141820)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(17.dp)) {
            Text(kind.title, color = Color(0xFF737D8E), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text(kind.value(t), color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MetricPickerDialog(current: MetricKind, onDismiss: () -> Unit, onSelect: (MetricKind) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose metric") },
        text = {
            Column {
                MetricKind.values().forEach { kind ->
                    TextButton(
                        onClick = { onSelect(kind) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (kind == current) "✓ ${kind.title}" else kind.title,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL") }
        }
    )
}

@Composable
private fun ConnectionCard(
    linkState: LinkState,
    message: String,
    hasTelemetry: Boolean,
    recording: Boolean,
    showDebug: Boolean,
    onToggleDebug: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10131A)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(17.dp)) {
            Text(
                when {
                    hasTelemetry -> "● TELEMETRY ONLINE"
                    linkState == LinkState.ERROR -> "● CONNECTION ERROR"
                    else -> "● CONNECTION"
                },
                color = when {
                    hasTelemetry -> Color(0xFF79F29C)
                    linkState == LinkState.ERROR -> Color(0xFFFF6A75)
                    else -> Color(0xFF8D97A8)
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(7.dp))
            Text(
                if (hasTelemetry) "Receiving live data from the wheel" else message,
                color = Color(0xFFD6DBE5),
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (recording) "● LOGGING CSV" else "Ride log is off",
                    modifier = Modifier.weight(1f),
                    color = if (recording) Color(0xFFFFC857) else Color(0xFF687283),
                    fontSize = 11.sp,
                    fontWeight = if (recording) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    if (showDebug) "HIDE DEBUG" else "DEBUG",
                    modifier = Modifier.clickable(onClick = onToggleDebug),
                    color = Color(0xFF8E97A8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DebugRawCard() {
    val rawPacket by WheelRepository.rawPacket.collectAsState()
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1015)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(15.dp)) {
            Text("RAW BLE · DEBUG", color = Color(0xFF6E7788), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                rawPacket,
                color = Color(0xFF7C8696),
                fontSize = 10.sp,
                lineHeight = 14.sp,
                minLines = 3,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
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
