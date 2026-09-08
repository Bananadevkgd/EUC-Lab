from pathlib import Path
import runpy

# v0.0.11 builds on the v0.0.10 universal BLE foundation.
runpy.run_path("scripts/apply_v10.py", run_name="__main__")


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected exactly one match, got {count}: {old[:160]!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


def replace_all(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    if old not in text:
        raise RuntimeError(f"{path}: missing {old!r}")
    p.write_text(text.replace(old, new), encoding="utf-8")


ble = "app/src/main/java/com/euclab/app/ble/BleWheelManager.kt"

replace_once(
    ble,
    '''    private val decoder = VeteranFrameDecoder()''',
    '''    private val decoder = VeteranFrameDecoder()
    private val kingSongDecoder = KingSongFrameDecoderV11()''',
)

replace_once(
    ble,
    '''    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var currentTransportLabel = "Unknown BLE"
    private var detectedProtocolLabel = "Unknown protocol"
    private val commandHandler = Handler(Looper.getMainLooper())''',
    '''    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var currentTransportLabel = "Unknown BLE"
    private var detectedProtocolLabel = "Unknown protocol"
    private var currentWheelName = ""
    private val commandHandler = Handler(Looper.getMainLooper())
    private val kingSongHeartbeatRunnable = object : Runnable {
        override fun run() {
            if (WheelRepository.linkState.value == LinkState.CONNECTED &&
                detectedProtocolLabel.startsWith("KingSong") &&
                currentTransportLabel == "KingSong FFE0/FFE1") {
                writeTransportBytes(KINGSONG_HEARTBEAT)
                commandHandler.postDelayed(this, 1000L)
            }
        }
    }''',
)

replace_once(
    ble,
    '''        decoder.reset()
        WheelRepository.clearTelemetry()''',
    '''        decoder.reset()
        kingSongDecoder.reset()
        currentWheelName = candidateMap[address]?.name ?: rememberedWheelName().orEmpty()
        kingSongDecoder.setAdvertisedName(currentWheelName)
        stopKingSongTransport()
        WheelRepository.clearTelemetry()''',
)

replace_once(
    ble,
    '''            val services = callbackGatt.services.orEmpty()
            val allCharacteristics = services.flatMap { it.characteristics.orEmpty() }
            val knownFfe1 = callbackGatt.getService(SERVICE_UUID)?.getCharacteristic(CHAR_UUID)

            val gattSummary = services.joinToString(" | ") { service ->''',
    '''            val services = callbackGatt.services.orEmpty()
            val allCharacteristics = services.flatMap { it.characteristics.orEmpty() }
            val knownFfe1 = callbackGatt.getService(SERVICE_UUID)?.getCharacteristic(CHAR_UUID)
            val kseService = callbackGatt.getService(KINGSONG_KSE_SERVICE_UUID)
            val kseNotify = kseService?.getCharacteristic(KINGSONG_KSE_READ_UUID)
            val kseWrite = kseService?.getCharacteristic(KINGSONG_KSE_WRITE_UUID)
            val kingSongNameHint = isKingSongName(currentWheelName)

            val gattSummary = services.joinToString(" | ") { service ->''',
)

replace_once(
    ble,
    '''            val notify = knownFfe1 ?: allCharacteristics.firstOrNull { (it.properties and notifyMask) != 0 }
            if (notify == null) {
                WheelRepository.setLink(LinkState.ERROR, "Connected, but no notify/indicate characteristic found · GATT captured")
                return
            }

            val write = if (knownFfe1 != null) knownFfe1
            else allCharacteristics.firstOrNull { (it.properties and writeMask) != 0 }

            notifyCharacteristic = notify
            wheelCharacteristic = write
            currentTransportLabel = if (knownFfe1 != null) "FFE0/FFE1" else "Generic BLE"
            detectedProtocolLabel = "Unknown protocol"''',
    '''            val notify = when {
                kseNotify != null -> kseNotify
                knownFfe1 != null -> knownFfe1
                else -> allCharacteristics.firstOrNull { (it.properties and notifyMask) != 0 }
            }
            if (notify == null) {
                WheelRepository.setLink(LinkState.ERROR, "Connected, but no notify/indicate characteristic found · GATT captured")
                return
            }

            val write = when {
                kseWrite != null -> kseWrite
                knownFfe1 != null -> knownFfe1
                else -> allCharacteristics.firstOrNull { (it.properties and writeMask) != 0 }
            }

            notifyCharacteristic = notify
            wheelCharacteristic = write
            currentTransportLabel = when {
                kseNotify != null -> "KingSong KSE AD00"
                knownFfe1 != null && kingSongNameHint -> "KingSong FFE0/FFE1"
                knownFfe1 != null -> "FFE0/FFE1"
                else -> "Generic BLE"
            }
            detectedProtocolLabel = if (kseNotify != null || kingSongNameHint) "KingSong (probing)" else "Unknown protocol"''',
)

replace_once(
    ble,
    '''                    WheelRepository.setLink(
                        LinkState.CONNECTED,
                        "$currentTransportLabel connected$writeNote · $detectedProtocolLabel · RAW diagnostics active"
                    )
                } else WheelRepository.setLink(LinkState.ERROR, "Notification setup failed: $status")''',
    '''                    WheelRepository.setLink(
                        LinkState.CONNECTED,
                        "$currentTransportLabel connected$writeNote · $detectedProtocolLabel · RAW diagnostics active"
                    )
                    if (detectedProtocolLabel.startsWith("KingSong")) startKingSongTransport()
                } else WheelRepository.setLink(LinkState.ERROR, "Notification setup failed: $status")''',
)

replace_once(
    ble,
    '''    private fun onBytes(bytes: ByteArray) {
        WheelRepository.setRawPacket(bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }.take(240))

        // v0.0.10 keeps Veteran decoding as the first known decoder, but unknown BLE
        // streams stay connected and visible instead of being rejected. A protocol
        // router will add Begode/KingSong/Inmotion decoders on top of this foundation.
        val decoded = decoder.feed(bytes)
        decoded.forEach(WheelRepository::publishTelemetry)
        if (decoded.isNotEmpty()) {
            decoder.latestBmsSnapshot()?.let(WheelRepository::publishBms)
            if (detectedProtocolLabel != "Veteran / LeaperKim") {
                detectedProtocolLabel = "Veteran / LeaperKim"
                WheelRepository.setLink(LinkState.CONNECTED, "$currentTransportLabel connected · $detectedProtocolLabel detected")
            }
        }
    }''',
    '''    private fun onBytes(bytes: ByteArray) {
        WheelRepository.setRawPacket(bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }.take(240))

        // KingSong and Veteran are independently validated by their frame signatures.
        // Unknown wheels remain diagnostic-only until one decoder produces valid data.
        val kingSongDecoded = kingSongDecoder.feed(bytes)
        if (kingSongDecoded.isNotEmpty()) {
            kingSongDecoded.forEach(WheelRepository::publishTelemetry)
            if (detectedProtocolLabel != "KingSong") {
                detectedProtocolLabel = "KingSong"
                WheelRepository.setLink(LinkState.CONNECTED, "$currentTransportLabel connected · KingSong detected · telemetry active")
            }
            return
        }

        val decoded = decoder.feed(bytes)
        decoded.forEach(WheelRepository::publishTelemetry)
        if (decoded.isNotEmpty()) {
            decoder.latestBmsSnapshot()?.let(WheelRepository::publishBms)
            if (detectedProtocolLabel != "Veteran / LeaperKim") {
                stopKingSongTransport()
                detectedProtocolLabel = "Veteran / LeaperKim"
                WheelRepository.setLink(LinkState.CONNECTED, "$currentTransportLabel connected · $detectedProtocolLabel detected")
            }
        }
    }

    private fun isKingSongName(name: String): Boolean {
        val n = name.trim().uppercase()
        return n.contains("KINGSONG") || n.startsWith("KS-") || n.startsWith("KSE")
    }

    private fun startKingSongTransport() {
        kingSongDecoder.setAdvertisedName(currentWheelName)
        // Read-only init request. KingSong protocol frame type 0x9B requests model/name.
        writeTransportBytes(kingSongRequest(0x9B))

        if (currentTransportLabel == "KingSong FFE0/FFE1") {
            commandHandler.removeCallbacks(kingSongHeartbeatRunnable)
            commandHandler.post(kingSongHeartbeatRunnable)
            commandHandler.postDelayed({
                if (WheelRepository.linkState.value == LinkState.CONNECTED && detectedProtocolLabel.startsWith("KingSong")) {
                    writeTransportBytes(KINGSONG_WARMUP)
                    writeTransportBytes(kingSongRequest(0x9B))
                }
            }, 2500L)
        }
    }

    private fun stopKingSongTransport() {
        commandHandler.removeCallbacks(kingSongHeartbeatRunnable)
    }

    private fun kingSongRequest(type: Int): ByteArray = byteArrayOf(
        0xAA.toByte(), 0x55, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        type.toByte(), 0x14, 0x5A, 0x5A
    )''',
)

replace_once(
    ble,
    '''    private fun sendCommand(bytes: ByteArray): Boolean {
        if (!canConnect() || WheelRepository.linkState.value != LinkState.CONNECTED) return false
        // Never send Veteran/LeaperKim control packets to an unknown wheel merely
        // because it exposed a writable BLE characteristic. Commands unlock only
        // after a valid Veteran frame has positively identified the protocol.
        if (detectedProtocolLabel != "Veteran / LeaperKim") return false
        val currentGatt = gatt ?: return false
        val characteristic = wheelCharacteristic ?: return false
        return if (Build.VERSION.SDK_INT >= 33) {
            currentGatt.writeCharacteristic(characteristic, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run { characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE; characteristic.value = bytes; currentGatt.writeCharacteristic(characteristic) }
        }
    }''',
    '''    private fun sendCommand(bytes: ByteArray): Boolean {
        // User-facing control methods in this manager are still Veteran-only.
        if (detectedProtocolLabel != "Veteran / LeaperKim") return false
        return writeTransportBytes(bytes)
    }

    @SuppressLint("MissingPermission")
    private fun writeTransportBytes(bytes: ByteArray): Boolean {
        if (!canConnect() || WheelRepository.linkState.value != LinkState.CONNECTED) return false
        val currentGatt = gatt ?: return false
        val characteristic = wheelCharacteristic ?: return false
        return if (Build.VERSION.SDK_INT >= 33) {
            currentGatt.writeCharacteristic(characteristic, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run { characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE; characteristic.value = bytes; currentGatt.writeCharacteristic(characteristic) }
        }
    }''',
)

# Stop KingSong maintenance when an explicit disconnect happens.
replace_once(
    ble,
    '''    fun disconnect() {
        reconnectHandler.removeCallbacksAndMessages(null)
        autoConnectScan = false''',
    '''    fun disconnect() {
        reconnectHandler.removeCallbacksAndMessages(null)
        stopKingSongTransport()
        autoConnectScan = false''',
)

# UI: don't claim Sherman L or OFFLINE when BLE is connected but telemetry is not decoded yet.
ui = "app/src/main/java/com/euclab/app/AppUiV5.kt"
replace_once(ui, '''    val online = t != null''', '''    val telemetryOnline = t != null
    val bleOnline = link == LinkState.CONNECTED''')
replace_once(ui, '''            Text("${t?.model ?: "Sherman L"} · v0.0.10", color = V5Muted, fontSize = 12.sp)''', '''            Text("${t?.model ?: if (bleOnline) lang.t("Неизвестное колесо", "Unknown wheel") else "EUC"} · v0.0.11", color = V5Muted, fontSize = 12.sp)''')
replace_once(ui, '''            V5WheelModelBadge(t?.model, online)''', '''            V5WheelModelBadge(t?.model, telemetryOnline)''')
replace_once(ui, '''                    .background(if (online) Color(0xFF173A26) else Color(0xFF222831))''', '''                    .background(if (bleOnline) Color(0xFF173A26) else Color(0xFF222831))''')
replace_once(ui, '''                Box(Modifier.size(7.dp).clip(RoundedCornerShape(99.dp)).background(if (online) V5Good else V5Muted))''', '''                Box(Modifier.size(7.dp).clip(RoundedCornerShape(99.dp)).background(if (bleOnline) V5Good else V5Muted))''')
replace_once(ui, '''                    if (online) lang.t("ОНЛАЙН", "ONLINE") else when (link) {
                        LinkState.SCANNING -> lang.t("ПОИСК", "SCAN")''', '''                    if (telemetryOnline) lang.t("ОНЛАЙН", "ONLINE") else when (link) {
                        LinkState.CONNECTED -> lang.t("BLE СВЯЗЬ", "BLE LINK")
                        LinkState.SCANNING -> lang.t("ПОИСК", "SCAN")''')

# Version metadata.
gradle = "app/build.gradle.kts"
replace_once(gradle, "        versionCode = 10", "        versionCode = 11")
replace_once(gradle, '        versionName = "0.0.10"', '        versionName = "0.0.11"')
replace_all(gradle, "// v0.0.10:", "// v0.0.11:")

# BLE constants for KingSong KSE and classic transport maintenance frames.
replace_once(
    ble,
    '''        val SERVICE_UUID: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        val CHAR_UUID: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")''',
    '''        val SERVICE_UUID: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        val CHAR_UUID: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val KINGSONG_KSE_SERVICE_UUID: UUID = UUID.fromString("0000ad00-0000-1000-8000-00805f9b34fb")
        val KINGSONG_KSE_WRITE_UUID: UUID = UUID.fromString("0000ad01-0000-1000-8000-00805f9b34fb")
        val KINGSONG_KSE_READ_UUID: UUID = UUID.fromString("0000ad02-0000-1000-8000-00805f9b34fb")
        val KINGSONG_HEARTBEAT = byteArrayOf(
            0xAA.toByte(), 0x55, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x14, 0x5A, 0x5A
        )
        val KINGSONG_WARMUP = byteArrayOf(
            0xAA.toByte(), 0x55, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x5E, 0x14, 0x5A, 0x5A
        )''',
)

print("EUC Lab v0.0.11 KingSong telemetry patch applied")
