from pathlib import Path
import runpy

# v0.0.13 builds on the proven v0.0.12 patch chain.
runpy.run_path("scripts/run_v12.py", run_name="__main__")


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected exactly one match, got {count}: {old[:180]!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


def replace_all(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    if old not in text:
        raise RuntimeError(f"{path}: missing {old!r}")
    p.write_text(text.replace(old, new), encoding="utf-8")


# ---------------------------------------------------------------------------
# Session statistics: update on every decoded telemetry sample, independent of CSV.
# ---------------------------------------------------------------------------
repo = "app/src/main/java/com/euclab/app/data/WheelRepository.kt"
replace_once(
    repo,
    '''    fun publishTelemetry(value: Telemetry) {
        _telemetry.value = value
    }''',
    '''    fun publishTelemetry(value: Telemetry) {
        _telemetry.value = value
        SessionTrackerV13.onTelemetry(value)
    }''',
)

# ---------------------------------------------------------------------------
# Home: show the live session card and bump visible version.
# ---------------------------------------------------------------------------
ui = "app/src/main/java/com/euclab/app/AppUiV5.kt"
replace_once(
    ui,
    '''        item { V5MetricGrid(telemetry, lang) }
        item { V8LightQuick(telemetry, lightOn, highBeamOn, lang == V5Language.RU, ble) }''',
    '''        item { V5MetricGrid(telemetry, lang) }
        item { V13SessionCard(lang == V5Language.RU) }
        item { V8LightQuick(telemetry, lightOn, highBeamOn, lang == V5Language.RU, ble) }''',
)
replace_all(ui, "v0.0.12", "v0.0.13")

# ---------------------------------------------------------------------------
# Wheel tab: route Begode / Extreme Bull / InMotion to family-specific UI.
# ---------------------------------------------------------------------------
wheel_ui = "app/src/main/java/com/euclab/app/WheelUiV12.kt"
replace_once(
    wheel_ui,
    '''    when (profile.brand) {
        WheelBrandV12.KINGSONG -> V12KingSongWheelScreen(ble, telemetry, ru)
        WheelBrandV12.LEAPERKIM -> V7WheelScreen(ble, ru)
        else -> V12UnknownWheelScreen(telemetry, profile.displayBrand, ru)
    }''',
    '''    when (profile.brand) {
        WheelBrandV12.KINGSONG -> V12KingSongWheelScreen(ble, telemetry, ru)
        WheelBrandV12.LEAPERKIM -> V7WheelScreen(ble, ru)
        WheelBrandV12.BEGODE, WheelBrandV12.EXTREME_BULL, WheelBrandV12.INMOTION ->
            V13FamilyWheelScreen(telemetry, profile.brand, ru)
        else -> V12UnknownWheelScreen(telemetry, profile.displayBrand, ru)
    }''',
)

# ---------------------------------------------------------------------------
# Veteran/Lynx S: debounce the charging flag so a transient control-frame value
# cannot switch the UI into charging ETA while lights are being changed.
# ---------------------------------------------------------------------------
vet = "app/src/main/java/com/euclab/app/ble/VeteranFrameDecoder.kt"
replace_once(
    vet,
    '''    private var bmsSeen = false
    private var latestSettings = VeteranSettingsSnapshot()''',
    '''    private var bmsSeen = false
    private var latestSettings = VeteranSettingsSnapshot()
    private var consecutiveChargeFrames = 0''',
)
replace_once(
    vet,
    '''        bmsSeen = false
        latestSettings = VeteranSettingsSnapshot()''',
    '''        bmsSeen = false
        latestSettings = VeteranSettingsSnapshot()
        consecutiveChargeFrames = 0''',
)
replace_once(
    vet,
    '''        val chargeMode = u16be(frame, 22)
        val alertSpeed = u16be(frame, 24)''',
    '''        val chargeMode = u16be(frame, 22)
        // A light/control transition on some Lynx S streams can briefly make byte 22
        // non-zero. Require several stopped telemetry frames before declaring charging.
        val chargeCandidate = chargeMode > 0 && abs(speed) < 1f
        consecutiveChargeFrames = if (chargeCandidate) (consecutiveChargeFrames + 1).coerceAtMost(10) else 0
        val stableCharging = consecutiveChargeFrames >= 3
        val alertSpeed = u16be(frame, 24)''',
)
replace_once(vet, "            charging = chargeMode > 0,", "            charging = stableCharging,")

# ---------------------------------------------------------------------------
# BLE manager: add Begode and InMotion P6 transports/decoders.
# ---------------------------------------------------------------------------
ble = "app/src/main/java/com/euclab/app/ble/BleWheelManager.kt"

replace_once(
    ble,
    '''    private val decoder = VeteranFrameDecoder()
    private val kingSongDecoder = KingSongFrameDecoderV11()''',
    '''    private val decoder = VeteranFrameDecoder()
    private val kingSongDecoder = KingSongFrameDecoderV11()
    private val begodeDecoder = BegodeFrameDecoderV13()
    private val p6Decoder = LorinP6DecoderV13()''',
)

replace_once(
    ble,
    '''    private val commandHandler = Handler(Looper.getMainLooper())
    private val kingSongHeartbeatRunnable = object : Runnable {''',
    '''    private val commandHandler = Handler(Looper.getMainLooper())
    private val p6KeepAliveRunnable = object : Runnable {
        override fun run() {
            if (WheelRepository.linkState.value == LinkState.CONNECTED && detectedProtocolLabel == "InMotion P6") {
                writeTransportBytes(p6Decoder.keepAliveCommand())
                commandHandler.postDelayed(this, 250L)
            }
        }
    }
    private val kingSongHeartbeatRunnable = object : Runnable {''',
)

# Scanner: identify NUS P6 and common Begode marketing/controller names even when
# the advertisement does not literally contain "Begode".
replace_once(
    ble,
    '''            name.contains("Inmotion", ignoreCase = true) ||
            name.contains("NOSFET", ignoreCase = true)''',
    '''            name.contains("Inmotion", ignoreCase = true) ||
            name.startsWith("P6", ignoreCase = true) ||
            name.startsWith("V11", ignoreCase = true) ||
            name.startsWith("V12", ignoreCase = true) ||
            name.startsWith("V13", ignoreCase = true) ||
            name.startsWith("V14", ignoreCase = true) ||
            name.startsWith("E20", ignoreCase = true) ||
            name.startsWith("E25", ignoreCase = true) ||
            name.equals("RACE", ignoreCase = true) ||
            name.contains("Falcon", ignoreCase = true) ||
            name.contains("ET MAX", ignoreCase = true) ||
            name.contains("ETMAX", ignoreCase = true) ||
            name.contains("Master", ignoreCase = true) ||
            name.contains("EX30", ignoreCase = true) ||
            name.contains("Blitz", ignoreCase = true) ||
            name.startsWith("T4", ignoreCase = true) ||
            name.contains("Mten", ignoreCase = true) ||
            name.equals("A2", ignoreCase = true) ||
            name.contains("Nikola", ignoreCase = true) ||
            name.contains("Commander", ignoreCase = true) ||
            name.contains("Panther", ignoreCase = true) ||
            name.contains("X-Way", ignoreCase = true) ||
            name.contains("NOSFET", ignoreCase = true) ||
            uuids.contains(NUS_SERVICE_UUID) ||
            uuids.contains(BEGODE_ALT_SERVICE_UUID)''',
)

# Reset all family decoders on a new explicit connection and start session stats.
replace_once(
    ble,
    '''        reconnectHandler.removeCallbacksAndMessages(null)
        decoder.reset()
        kingSongDecoder.reset()
        currentWheelName = candidateMap[address]?.name ?: rememberedWheelName().orEmpty()
        kingSongDecoder.setAdvertisedName(currentWheelName)
        stopKingSongTransport()
        WheelRepository.clearTelemetry()''',
    '''        reconnectHandler.removeCallbacksAndMessages(null)
        decoder.reset()
        kingSongDecoder.reset()
        begodeDecoder.reset()
        p6Decoder.reset()
        currentWheelName = candidateMap[address]?.name ?: rememberedWheelName().orEmpty()
        kingSongDecoder.setAdvertisedName(currentWheelName)
        stopKingSongTransport()
        stopP6Transport()
        SessionTrackerV13.beginFor(address)
        WheelRepository.clearTelemetry()''',
)

# Stop P6 polling on manual disconnect too.
replace_once(
    ble,
    '''    fun disconnect() {
        reconnectHandler.removeCallbacksAndMessages(null)
        stopKingSongTransport()
        autoConnectScan = false''',
    '''    fun disconnect() {
        reconnectHandler.removeCallbacksAndMessages(null)
        stopKingSongTransport()
        stopP6Transport()
        autoConnectScan = false''',
)

# Service discovery: explicit Nordic UART handling for P6, plus Begode name hints.
replace_once(
    ble,
    '''            val knownFfe1 = callbackGatt.getService(SERVICE_UUID)?.getCharacteristic(CHAR_UUID)
            val kseService = callbackGatt.getService(KINGSONG_KSE_SERVICE_UUID)
            val kseNotify = kseService?.getCharacteristic(KINGSONG_KSE_READ_UUID)
            val kseWrite = kseService?.getCharacteristic(KINGSONG_KSE_WRITE_UUID)
            val kingSongNameHint = isKingSongName(currentWheelName)''',
    '''            val knownFfe1 = callbackGatt.getService(SERVICE_UUID)?.getCharacteristic(CHAR_UUID)
            val kseService = callbackGatt.getService(KINGSONG_KSE_SERVICE_UUID)
            val kseNotify = kseService?.getCharacteristic(KINGSONG_KSE_READ_UUID)
            val kseWrite = kseService?.getCharacteristic(KINGSONG_KSE_WRITE_UUID)
            val nusService = callbackGatt.getService(NUS_SERVICE_UUID)
            val nusNotify = nusService?.getCharacteristic(NUS_NOTIFY_UUID)
            val nusWrite = nusService?.getCharacteristic(NUS_WRITE_UUID)
            val kingSongNameHint = isKingSongName(currentWheelName)
            val p6NameHint = isP6Name(currentWheelName)
            val begodeNameHint = isBegodeName(currentWheelName)''',
)

replace_once(
    ble,
    '''            val notify = when {
                kseNotify != null -> kseNotify
                knownFfe1 != null -> knownFfe1
                else -> allCharacteristics.firstOrNull { (it.properties and notifyMask) != 0 }
            }''',
    '''            val notify = when {
                nusNotify != null -> nusNotify
                kseNotify != null -> kseNotify
                knownFfe1 != null -> knownFfe1
                else -> allCharacteristics.firstOrNull { (it.properties and notifyMask) != 0 }
            }''',
)

replace_once(
    ble,
    '''            val write = when {
                kseWrite != null -> kseWrite
                knownFfe1 != null -> knownFfe1
                else -> allCharacteristics.firstOrNull { (it.properties and writeMask) != 0 }
            }''',
    '''            val write = when {
                nusWrite != null -> nusWrite
                kseWrite != null -> kseWrite
                knownFfe1 != null -> knownFfe1
                else -> allCharacteristics.firstOrNull { (it.properties and writeMask) != 0 }
            }''',
)

replace_once(
    ble,
    '''            currentTransportLabel = when {
                kseNotify != null -> "KingSong KSE AD00"
                knownFfe1 != null && kingSongNameHint -> "KingSong FFE0/FFE1"
                knownFfe1 != null -> "FFE0/FFE1"
                else -> "Generic BLE"
            }
            detectedProtocolLabel = if (kseNotify != null || kingSongNameHint) "KingSong (probing)" else "Unknown protocol"''',
    '''            currentTransportLabel = when {
                nusNotify != null -> "Nordic UART"
                kseNotify != null -> "KingSong KSE AD00"
                knownFfe1 != null && kingSongNameHint -> "KingSong FFE0/FFE1"
                knownFfe1 != null && begodeNameHint -> "Begode FFE0/FFE1"
                knownFfe1 != null -> "FFE0/FFE1"
                else -> "Generic BLE"
            }
            detectedProtocolLabel = when {
                p6NameHint -> "InMotion P6"
                kseNotify != null || kingSongNameHint -> "KingSong (probing)"
                begodeNameHint -> "Begode (probing)"
                else -> "Unknown protocol"
            }''',
)

# Once notification setup succeeds, issue only safe family init/probe traffic.
replace_once(
    ble,
    '''                    if (detectedProtocolLabel.startsWith("KingSong")) startKingSongTransport()''',
    '''                    when {
                        detectedProtocolLabel.startsWith("KingSong") -> startKingSongTransport()
                        detectedProtocolLabel == "InMotion P6" -> startP6Transport()
                        detectedProtocolLabel.startsWith("Begode") -> startBegodeTransport()
                    }''',
)

# Protocol router. Signatures are distinct: P6 AA AA, KingSong AA 55,
# Begode 55 AA, Veteran DC 5A 5C.
replace_once(
    ble,
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
    }''',
    '''    private fun onBytes(bytes: ByteArray) {
        WheelRepository.setRawPacket(bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }.take(240))

        val p6Candidate = detectedProtocolLabel == "InMotion P6" || p6Decoder.looksLikeLorin(bytes)
        if (p6Candidate) {
            val p6Decoded = p6Decoder.feed(bytes)
            if (p6Decoded.isNotEmpty()) {
                p6Decoded.forEach(WheelRepository::publishTelemetry)
                if (detectedProtocolLabel != "InMotion P6") {
                    stopKingSongTransport()
                    detectedProtocolLabel = "InMotion P6"
                    startP6Transport()
                }
                WheelRepository.setLink(LinkState.CONNECTED, "$currentTransportLabel connected · InMotion P6 · telemetry active")
                return
            }
        }

        val begodeCandidate = detectedProtocolLabel.startsWith("Begode") || begodeDecoder.looksLikeBegode(bytes)
        if (begodeCandidate) {
            val begodeDecoded = begodeDecoder.feed(bytes)
            if (begodeDecoded.isNotEmpty()) {
                stopKingSongTransport()
                stopP6Transport()
                begodeDecoded.forEach(WheelRepository::publishTelemetry)
                detectedProtocolLabel = "Begode / Gotway"
                WheelRepository.setLink(LinkState.CONNECTED, "$currentTransportLabel connected · ${begodeDecoder.brandName()} · telemetry active")
                return
            }
        }

        val kingSongDecoded = kingSongDecoder.feed(bytes)
        if (kingSongDecoded.isNotEmpty()) {
            kingSongDecoded.forEach(WheelRepository::publishTelemetry)
            if (detectedProtocolLabel != "KingSong") {
                stopP6Transport()
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
                stopP6Transport()
                detectedProtocolLabel = "Veteran / LeaperKim"
                WheelRepository.setLink(LinkState.CONNECTED, "$currentTransportLabel connected · $detectedProtocolLabel detected")
            }
        }
    }''',
)

# Family hints and safe init/keepalive helpers.
replace_once(
    ble,
    '''    private fun isKingSongName(name: String): Boolean {
        val n = name.trim().uppercase()
        return n.contains("KINGSONG") || n.startsWith("KS-") || n.startsWith("KSE")
    }

    private fun startKingSongTransport() {''',
    '''    private fun isKingSongName(name: String): Boolean {
        val n = name.trim().uppercase()
        return n.contains("KINGSONG") || n.startsWith("KS-") || n.startsWith("KSE")
    }

    private fun isP6Name(name: String): Boolean {
        val n = name.trim().uppercase()
        return n.startsWith("P6") || n.contains("INMOTION P6")
    }

    private fun isBegodeName(name: String): Boolean {
        val n = name.trim().uppercase().replace("_", " ")
        return n.contains("BEGODE") || n.contains("GOTWAY") || n.contains("EXTREME BULL") ||
            n == "RACE" || n.contains("FALCON") || n.contains("ET MAX") || n.contains("ETMAX") ||
            n.contains("MASTER") || n.contains("EX30") || n.contains("BLITZ") || n.startsWith("T4") ||
            n.contains("MTEN") || n == "A2" || n.contains("NIKOLA") || n.contains("COMMANDER") ||
            n.contains("PANTHER") || n.contains("X-WAY") || n.contains("XWAY") || n.startsWith("GW")
    }

    private fun startP6Transport() {
        stopP6Transport()
        p6Decoder.initCommands().forEachIndexed { index, command ->
            commandHandler.postDelayed({
                if (WheelRepository.linkState.value == LinkState.CONNECTED && detectedProtocolLabel == "InMotion P6") {
                    writeTransportBytes(command)
                }
            }, index * 140L)
        }
        commandHandler.postDelayed(p6KeepAliveRunnable, 1000L)
    }

    private fun stopP6Transport() {
        commandHandler.removeCallbacks(p6KeepAliveRunnable)
    }

    private fun startBegodeTransport() {
        // Public Gotway/Begode init probes: firmware, stream/settings, model, stream/settings.
        listOf("V", "b", "N", "b").forEachIndexed { index, command ->
            commandHandler.postDelayed({
                if (WheelRepository.linkState.value == LinkState.CONNECTED && detectedProtocolLabel.startsWith("Begode")) {
                    writeTransportBytes(command.encodeToByteArray())
                }
            }, index * 180L)
        }
    }

    private fun startKingSongTransport() {''',
)

# UUIDs for InMotion/Lorin P6 and newer Begode advertisement hints.
replace_once(
    ble,
    '''        val KINGSONG_KSE_READ_UUID: UUID = UUID.fromString("0000ad02-0000-1000-8000-00805f9b34fb")''',
    '''        val KINGSONG_KSE_READ_UUID: UUID = UUID.fromString("0000ad02-0000-1000-8000-00805f9b34fb")
        val NUS_SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val NUS_WRITE_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        val NUS_NOTIFY_UUID: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
        val BEGODE_ALT_SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")''',
)

# ---------------------------------------------------------------------------
# APK metadata.
# ---------------------------------------------------------------------------
gradle = "app/build.gradle.kts"
replace_once(gradle, "        versionCode = 12", "        versionCode = 13")
replace_once(gradle, '        versionName = "0.0.12"', '        versionName = "0.0.13"')
replace_all(gradle, "// v0.0.12:", "// v0.0.13:")

print("EUC Lab v0.0.13 universal Begode/P6/session patch applied")
