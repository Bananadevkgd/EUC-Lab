from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
BASE = "18791fcb4ea059ec2044a6c4fb4605291cde1b92"


def git_show(path: str) -> str:
    result = subprocess.run(
        ["git", "show", f"{BASE}:{path}"],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout


def replace_between(text: str, start: str, end: str, replacement: str) -> str:
    a = text.index(start)
    b = text.index(end, a)
    return text[:a] + replacement.rstrip() + "\n\n" + text[b:]


# Restore the known-good v0.0.5 UI, then apply v0.0.6 changes safely.
ui_rel = "app/src/main/java/com/euclab/app/AppUiV5.kt"
ui_path = ROOT / ui_rel
ui = git_show(ui_rel).replace("v0.0.5", "v0.0.6")

header = r'''@Composable
private fun V5Header(t: Telemetry?, link: LinkState, lang: V5Language) {
    val online = t != null
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("EUC LAB", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("${t?.model ?: "Sherman L"} · v0.0.6", color = V5Muted, fontSize = 12.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            V5WheelModelBadge(t?.model, online)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (online) Color(0xFF173A26) else Color(0xFF222831))
                    .padding(horizontal = 13.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(99.dp)).background(if (online) V5Good else V5Muted))
                Text(
                    if (online) lang.t("ОНЛАЙН", "ONLINE") else when (link) {
                        LinkState.SCANNING -> lang.t("ПОИСК", "SCAN")
                        LinkState.CONNECTING, LinkState.DISCOVERING -> lang.t("СВЯЗЬ…", "LINKING…")
                        LinkState.ERROR -> lang.t("ОШИБКА", "ERROR")
                        else -> lang.t("НЕ В СЕТИ", "OFFLINE")
                    },
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun V5WheelModelBadge(model: String?, online: Boolean) {
    val code = when {
        model?.contains("Sherman L", true) == true -> "SL"
        model?.contains("Sherman S", true) == true -> "SS"
        model?.contains("Sherman", true) == true -> "SH"
        model?.contains("Lynx S", true) == true -> "LS"
        model?.contains("Lynx", true) == true -> "LX"
        model?.contains("Patton S", true) == true -> "PS"
        model?.contains("Patton", true) == true -> "PT"
        model?.contains("Oryx", true) == true -> "OX"
        model?.contains("Apex", true) == true -> "AP"
        model?.contains("Aero", true) == true -> "AE"
        model?.contains("Aeon", true) == true -> "AN"
        else -> "EUC"
    }
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (online) Color(0xFF182218) else Color(0xFF181D24)),
        contentAlignment = Alignment.Center,
    ) {
        Text("◉", color = if (online) Color.White else V5Muted, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text(
            code,
            color = if (online) V5Accent else V5Muted,
            fontSize = 6.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp),
        )
    }
}'''
ui = replace_between(
    ui,
    "@Composable\nprivate fun V5Header(",
    "@Composable\nprivate fun rememberParkingState",
    header,
)

wheel = r'''@Composable
private fun V5Wheel(ble: BleWheelManager, lang: V5Language) {
    val telemetry by WheelRepository.telemetry.collectAsState()
    val lightOn by ble.lightOn.collectAsState()
    val online = telemetry != null
    val stopped = (telemetry?.speedKmh?.absoluteValue ?: 0f) < 1f
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("wheel_controls", Context.MODE_PRIVATE) }
    var result by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var keyTone by remember {
        mutableFloatStateOf((telemetry?.keyTonePercent ?: prefs.getInt("key_tone_percent", 70)).coerceIn(0, 100).toFloat())
    }
    var draggingTone by remember { mutableStateOf(false) }

    LaunchedEffect(telemetry?.keyTonePercent, draggingTone) {
        val reported = telemetry?.keyTonePercent
        if (!draggingTone && reported != null) {
            keyTone = reported.coerceIn(0, 100).toFloat()
            prefs.edit().putInt("key_tone_percent", reported.coerceIn(0, 100)).apply()
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = V5Surface2,
            titleContentColor = Color.White,
            textContentColor = Color(0xFFD4DAE3),
            title = { Text(lang.t("Сбросить пробег поездки?", "Reset trip distance?"), fontWeight = FontWeight.Black) },
            text = {
                Text(
                    lang.t(
                        "Вы точно уверены, что хотите сбросить пробег текущей поездки на колесе? Это действие нельзя отменить.",
                        "Are you sure you want to reset the current trip distance on the wheel? This cannot be undone.",
                    ),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    result = if (ble.resetTrip()) lang.t("Пробег поездки сброшен", "Trip distance reset") else lang.t("Команда не отправлена", "Command failed")
                }) {
                    Text(lang.t("СБРОСИТЬ", "RESET"), color = V5Danger, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(lang.t("ОТМЕНА", "CANCEL"), color = V5Muted, fontWeight = FontWeight.Bold)
                }
            },
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Spacer(Modifier.height(28.dp)) }
        item { V5SectionHeader(lang.t("Колесо", "Wheel"), telemetry?.model ?: lang.t("Управление Veteran / LeaperKim", "Veteran / LeaperKim controls")) }
        item {
            V5LightQuick(online, lightOn, lang) {
                result = if (ble.setLight(!lightOn)) lang.t("Команда фары отправлена", "Headlight command sent") else lang.t("Не удалось отправить команду", "Command failed")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { result = if (ble.beep()) lang.t("Команда сигнала отправлена", "Horn command sent") else lang.t("Команда не отправлена", "Command failed") },
                    enabled = online,
                    modifier = Modifier.weight(0.8f),
                    colors = ButtonDefaults.buttonColors(containerColor = V5Surface2, contentColor = Color.White),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(lang.t("СИГНАЛ", "BEEP"), fontWeight = FontWeight.Black, fontSize = 10.sp)
                }
                Button(
                    onClick = { if (stopped) confirmReset = true },
                    enabled = online && stopped,
                    modifier = Modifier.weight(1.2f),
                    colors = ButtonDefaults.buttonColors(containerColor = V5Surface2, contentColor = Color.White),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 11.dp),
                ) {
                    Text(lang.t("СБРОСИТЬ ПРОБЕГ ПОЕЗДКИ", "RESET TRIP DISTANCE"), fontWeight = FontWeight.Black, fontSize = 9.sp, maxLines = 2)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = V5Surface), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(17.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(lang.t("ГРОМКОСТЬ КНОПОК", "KEY TONE VOLUME"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                            Text(lang.t("Звук нажатий и клавиш самого колеса", "Sounds made by the wheel's buttons and keys"), color = V5Muted, fontSize = 10.sp)
                        }
                        Text("${keyTone.roundToInt()}%", color = V5Accent, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = keyTone,
                        onValueChange = { draggingTone = true; keyTone = it },
                        onValueChangeFinished = {
                            val value = keyTone.roundToInt().coerceIn(0, 100)
                            prefs.edit().putInt("key_tone_percent", value).apply()
                            result = if (ble.setKeyToneVolume(value)) lang.t("Громкость кнопок: $value%", "Key tone volume: $value%") else lang.t("Команда громкости не отправлена", "Volume command failed")
                            draggingTone = false
                        },
                        valueRange = 0f..100f,
                        enabled = online,
                        colors = SliderDefaults.colors(thumbColor = V5Accent, activeTrackColor = V5Accent, inactiveTrackColor = Color(0xFF303743)),
                    )
                    Text(
                        lang.t(
                            "ⓘ Этот ползунок не влияет на предупреждающие сигналы по PWM, скорости и другим аварийным событиям — только на звуки кнопок.",
                            "ⓘ This slider does not affect PWM, speed or other safety warning alarms — only key/button tones.",
                        ),
                        color = V5Amber,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = V5Surface), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(17.dp)) {
                    Text(lang.t("РЕЖИМ ПЕДАЛЕЙ", "PEDAL MODE"), color = V5Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to lang.t("ЖЁСТКИЙ", "HARD"), 1 to lang.t("СРЕДНИЙ", "MEDIUM"), 2 to lang.t("МЯГКИЙ", "SOFT")).forEach { (mode, title) ->
                            OutlinedButton(
                                onClick = { if (stopped) result = if (ble.setPedalMode(mode)) lang.t("Режим отправлен", "Mode sent") else lang.t("Команда не отправлена", "Command failed") },
                                enabled = online && stopped,
                                modifier = Modifier.weight(1f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3A424E)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                            ) {
                                Text(title, fontSize = 8.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (stopped) lang.t("Настройки доступны только при остановке.", "Settings are available only while stopped.")
                        else lang.t("Остановись, чтобы менять настройки колеса.", "Stop before changing wheel settings."),
                        color = V5Muted,
                        fontSize = 9.sp,
                    )
                }
            }
        }
        result?.let { text -> item { V5Empty(text) } }
        item { Spacer(Modifier.height(24.dp)) }
    }
}'''
ui = replace_between(
    ui,
    "@Composable\nprivate fun V5Wheel(",
    "@Composable\nprivate fun V5Settings(",
    wheel,
)
ui_path.write_text(ui, encoding="utf-8")

# Restore other source files from v0.0.5 before applying their v0.0.6 changes.
for rel in [
    "app/src/main/java/com/euclab/app/data/Telemetry.kt",
    "app/src/main/java/com/euclab/app/ble/VeteranFrameDecoder.kt",
    "app/src/main/java/com/euclab/app/ble/BleWheelManager.kt",
    "app/build.gradle.kts",
]:
    (ROOT / rel).write_text(git_show(rel), encoding="utf-8")

# Telemetry: keep the wheel-reported key tone value when available.
tel_path = ROOT / "app/src/main/java/com/euclab/app/data/Telemetry.kt"
tel = tel_path.read_text(encoding="utf-8")
tel = tel.replace('    val model: String = "Veteran",\n', '    val model: String = "Veteran",\n    val keyTonePercent: Int? = null,\n')
tel_path.write_text(tel, encoding="utf-8")

# Decoder: subtype 8, byte 63 = key tone volume (0..100, 0x80 unsupported).
dec_path = ROOT / "app/src/main/java/com/euclab/app/ble/VeteranFrameDecoder.kt"
dec = dec_path.read_text(encoding="utf-8")
dec = dec.replace("    private var bmsSeen = false\n", "    private var bmsSeen = false\n    private var latestKeyTonePercent: Int? = null\n")
dec = dec.replace("        bmsSeen = false\n", "        bmsSeen = false\n        latestKeyTonePercent = null\n", 1)
dec = dec.replace(
    "        if (modelVersion >= 5) decodeSmartBms(frame)\n",
    "        if (frame.size > 63 && u8(frame[46]) == 8) {\n            val reportedTone = u8(frame[63])\n            if (reportedTone != 0x80 && reportedTone in 0..100) latestKeyTonePercent = reportedTone\n        }\n\n        if (modelVersion >= 5) decodeSmartBms(frame)\n",
)
dec = dec.replace("            model = modelName(modelVersion),\n", "            model = modelName(modelVersion),\n            keyTonePercent = latestKeyTonePercent,\n")
dec_path.write_text(dec, encoding="utf-8")

# BLE: modern Sherman L horn + real key-tone volume command.
ble_path = ROOT / "app/src/main/java/com/euclab/app/ble/BleWheelManager.kt"
ble = ble_path.read_text(encoding="utf-8")
ble = ble.replace("import android.os.Build\n", "import android.os.Build\nimport android.os.Handler\nimport android.os.Looper\n")
ble = ble.replace("import java.util.UUID\n", "import java.util.UUID\nimport java.util.zip.CRC32\n")
ble = ble.replace("    private var wheelCharacteristic: BluetoothGattCharacteristic? = null\n", "    private var wheelCharacteristic: BluetoothGattCharacteristic? = null\n    private val commandHandler = Handler(Looper.getMainLooper())\n")
ble = replace_between(
    ble,
    "    fun beep(): Boolean",
    "    fun setPedalMode(",
    r'''    fun beep(): Boolean {
        val old = buildVeteranCommandOld(0x0E, 9, 1, byte5 = 0x00)
        val newer = buildVeteranCommandNew(0x0E, 9, 1, byte5 = 0x00, byte6 = 0x00)
        return sendStream(old + newer)
    }

    fun setKeyToneVolume(percent: Int): Boolean {
        val value = percent.coerceIn(0, 100)
        return sendStream(buildVeteranCommandNew(0x1C, 23, value, byte5 = 0x01, byte6 = 0x02))
    }''',
)
marker = '    @SuppressLint("MissingPermission")\n    private fun sendCommand(bytes: ByteArray): Boolean {'
helpers = r'''    private fun buildVeteranCommandOld(cmdByte: Int, valuePosition: Int, value: Int, byte5: Int = 0x01): ByteArray {
        val payload = ByteArray(valuePosition + 1) { 0x80.toByte() }
        payload[0] = 0x4C
        payload[1] = 0x6B
        payload[2] = 0x41
        payload[3] = 0x70
        payload[4] = cmdByte.toByte()
        payload[5] = byte5.toByte()
        payload[valuePosition] = value.toByte()
        return appendVeteranCrc(payload)
    }

    private fun buildVeteranCommandNew(cmdByte: Int, valuePosition: Int, value: Int, byte5: Int = 0x01, byte6: Int = 0x00): ByteArray {
        val payload = ByteArray(valuePosition + 1) { 0x80.toByte() }
        payload[0] = 0x4C
        payload[1] = 0x64
        payload[2] = 0x41
        payload[3] = 0x70
        payload[4] = cmdByte.toByte()
        payload[5] = byte5.toByte()
        payload[6] = byte6.toByte()
        payload[valuePosition] = value.toByte()
        return appendVeteranCrc(payload)
    }

    private fun appendVeteranCrc(payload: ByteArray): ByteArray {
        val crc = CRC32().apply { update(payload) }.value
        return payload + byteArrayOf(
            ((crc shr 24) and 0xFF).toByte(),
            ((crc shr 16) and 0xFF).toByte(),
            ((crc shr 8) and 0xFF).toByte(),
            (crc and 0xFF).toByte(),
        )
    }

    private fun sendStream(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + 20, bytes.size)
            chunks += bytes.copyOfRange(offset, end)
            offset = end
        }
        if (!sendCommand(chunks.first())) return false
        chunks.drop(1).forEachIndexed { index, chunk ->
            commandHandler.postDelayed({ sendCommand(chunk) }, 55L * (index + 1))
        }
        return true
    }

'''
ble = ble.replace(marker, helpers + marker)
ble_path.write_text(ble, encoding="utf-8")

# App version.
gradle_path = ROOT / "app/build.gradle.kts"
gradle = gradle_path.read_text(encoding="utf-8")
gradle = gradle.replace("versionCode = 5", "versionCode = 6")
gradle = gradle.replace('versionName = "0.0.5"', 'versionName = "0.0.6"')
gradle_path.write_text(gradle, encoding="utf-8")

print("Safe EUC Lab v0.0.6 rebuild applied")
