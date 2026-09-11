from pathlib import Path
import runpy

# Build on the exact v0.0.13 CI transform, including its qualified SessionTracker fix.
runpy.run_path("scripts/run_v13_ci.py", run_name="__main__")


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected exactly one match, got {count}: {old[:200]!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


def replace_all(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    if old not in text:
        raise RuntimeError(f"{path}: missing {old!r}")
    p.write_text(text.replace(old, new), encoding="utf-8")


# ---------------------------------------------------------------------------
# UI: model artwork on Home, protocol-aware battery screen, real Begode controls.
# ---------------------------------------------------------------------------
ui = "app/src/main/java/com/euclab/app/AppUiV5.kt"
replace_once(
    ui,
    '''                    V5Screen.BATTERY -> V5Battery(lang)''',
    '''                    V5Screen.BATTERY -> V14BatteryScreen(lang == V5Language.RU)''',
)
replace_once(
    ui,
    '''        item { V5Header(telemetry, link, lang) }
        item { V5ContextHero(telemetry, bms, lang) }''',
    '''        item { V5Header(telemetry, link, lang) }
        if (telemetry != null) item { V14WheelArtwork(telemetry!!.model, Modifier.fillMaxWidth().height(132.dp)) }
        item { V5ContextHero(telemetry, bms, lang) }''',
)
replace_all(ui, "v0.0.13", "v0.0.14")

wheel_ui = "app/src/main/java/com/euclab/app/WheelUiV12.kt"
replace_once(
    wheel_ui,
    '''        WheelBrandV12.BEGODE, WheelBrandV12.EXTREME_BULL, WheelBrandV12.INMOTION ->
            V13FamilyWheelScreen(telemetry, profile.brand, ru)''',
    '''        WheelBrandV12.BEGODE, WheelBrandV12.EXTREME_BULL ->
            V14BegodeWheelScreen(ble, telemetry, ru)
        WheelBrandV12.INMOTION -> V13FamilyWheelScreen(telemetry, profile.brand, ru)''',
)

# Native map: route rendering no longer depends on WebView/JavaScript at all.
ride = "app/src/main/java/com/euclab/app/RideDetailV7.kt"
replace_once(
    ride,
    '''                        key(mapMode) { V8RouteMap(ride, mapMode == RouteColorMode.PWM, Modifier.fillMaxWidth().height(270.dp)) }''',
    '''                        key(mapMode) { V14NativeRouteMap(ride, mapMode == RouteColorMode.PWM, Modifier.fillMaxWidth().height(270.dp)) }''',
)

# Small source-compat cleanups in the v14-only UI files.
replace_all("app/src/main/java/com/euclab/app/BegodeWheelScreenV14.kt", "private val G14Card = Color(0xFF11151C)", "private val G14CardColor = Color(0xFF11151C)")
replace_all("app/src/main/java/com/euclab/app/BegodeWheelScreenV14.kt", "containerColor = G14Card)", "containerColor = G14CardColor)")
replace_all("app/src/main/java/com/euclab/app/BegodeWheelScreenV14.kt", "mutableIntStateOf(", "mutableStateOf(")

battery_ui = Path("app/src/main/java/com/euclab/app/BatteryScreenV14.kt")
battery_text = battery_ui.read_text(encoding="utf-8")
if "import androidx.compose.foundation.lazy.items" not in battery_text:
    battery_text = battery_text.replace(
        "import androidx.compose.foundation.lazy.LazyColumn\n",
        "import androidx.compose.foundation.lazy.LazyColumn\nimport androidx.compose.foundation.lazy.items\n",
    )
battery_ui.write_text(battery_text, encoding="utf-8")

# ---------------------------------------------------------------------------
# BLE manager: v14 Begode decoder, readback/BMS publishing and verified controls.
# ---------------------------------------------------------------------------
ble = "app/src/main/java/com/euclab/app/ble/BleWheelManager.kt"
replace_once(ble, "private val begodeDecoder = BegodeFrameDecoderV13()", "private val begodeDecoder = BegodeFrameDecoderV14()")

replace_once(
    ble,
    '''    private val _highBeamOn = MutableStateFlow(false)
    val highBeamOn: StateFlow<Boolean> = _highBeamOn.asStateFlow()''',
    '''    private val _highBeamOn = MutableStateFlow(false)
    val highBeamOn: StateFlow<Boolean> = _highBeamOn.asStateFlow()

    private val _begodeSettings = MutableStateFlow(com.euclab.app.data.BegodeSettingsV14())
    val begodeSettings: StateFlow<com.euclab.app.data.BegodeSettingsV14> = _begodeSettings.asStateFlow()''',
)

replace_once(
    ble,
    '''        begodeDecoder.reset()
        p6Decoder.reset()
        currentWheelName = candidateMap[address]?.name ?: rememberedWheelName().orEmpty()
        kingSongDecoder.setAdvertisedName(currentWheelName)''',
    '''        begodeDecoder.reset()
        p6Decoder.reset()
        _begodeSettings.value = com.euclab.app.data.BegodeSettingsV14()
        currentWheelName = candidateMap[address]?.name ?: rememberedWheelName().orEmpty()
        kingSongDecoder.setAdvertisedName(currentWheelName)
        begodeDecoder.setAdvertisedName(currentWheelName)''',
)

# Home quick controls must route to Begode instead of falling through to Veteran.
replace_once(
    ble,
    '''    fun setLight(on: Boolean): Boolean {
        if (detectedProtocolLabel == "KingSong") {''',
    '''    fun setLight(on: Boolean): Boolean {
        if (detectedProtocolLabel == "Begode / Gotway") {
            return begodeSetLightMode(if (on) 1 else 0)
        }
        if (detectedProtocolLabel == "KingSong") {''',
)
replace_once(
    ble,
    '''    fun beep(): Boolean {
        if (detectedProtocolLabel == "KingSong") {''',
    '''    fun beep(): Boolean {
        if (detectedProtocolLabel == "Begode / Gotway") return begodeBeep()
        if (detectedProtocolLabel == "KingSong") {''',
)

# Publish Begode readback and Smart BMS on every valid Begode frame batch.
replace_once(
    ble,
    '''            if (begodeDecoded.isNotEmpty()) {
                stopKingSongTransport()
                stopP6Transport()
                begodeDecoded.forEach(WheelRepository::publishTelemetry)
                detectedProtocolLabel = "Begode / Gotway"
                WheelRepository.setLink(LinkState.CONNECTED, "$currentTransportLabel connected · ${begodeDecoder.brandName()} · telemetry active")
                return
            }''',
    '''            if (begodeDecoded.isNotEmpty()) {
                stopKingSongTransport()
                stopP6Transport()
                begodeDecoded.forEach(WheelRepository::publishTelemetry)
                begodeDecoder.latestBmsSnapshot()?.let(WheelRepository::publishBms)
                _begodeSettings.value = begodeDecoder.settingsSnapshot()
                _begodeSettings.value.lightMode?.let { _lightOn.value = it != 0 }
                detectedProtocolLabel = "Begode / Gotway"
                WheelRepository.setLink(
                    LinkState.CONNECTED,
                    "$currentTransportLabel connected · ${begodeDecoder.modelName()} · ${begodeDecoder.profile()?.fullVoltageV?.let { "${it}V" } ?: "voltage class resolving"}"
                )
                return
            }''',
)

# Verified Begode ASCII command surface. No Veteran packets can reach this path.
replace_once(
    ble,
    '''    private fun startBegodeTransport() {
        // Public Gotway/Begode init probes: firmware, stream/settings, model, stream/settings.
        listOf("V", "b", "N", "b").forEachIndexed { index, command ->
            commandHandler.postDelayed({
                if (WheelRepository.linkState.value == LinkState.CONNECTED && detectedProtocolLabel.startsWith("Begode")) {
                    writeTransportBytes(command.encodeToByteArray())
                }
            }, index * 180L)
        }
    }''',
    '''    private fun begodeReady(): Boolean =
        WheelRepository.linkState.value == LinkState.CONNECTED && detectedProtocolLabel == "Begode / Gotway"

    private fun begodeSequence(vararg steps: Pair<Long, String>): Boolean {
        if (!begodeReady() || steps.isEmpty()) return false
        val first = steps.first()
        if (first.first != 0L) return false
        val ok = writeTransportBytes(first.second.encodeToByteArray())
        if (!ok) return false
        steps.drop(1).forEach { (delayMs, command) ->
            commandHandler.postDelayed({
                if (begodeReady()) writeTransportBytes(command.encodeToByteArray())
            }, delayMs)
        }
        return true
    }

    fun begodeBeep(): Boolean = begodeSequence(0L to "b")

    fun begodeSetLightMode(mode: Int): Boolean {
        val command = when (mode) { 0 -> "E"; 1 -> "Q"; 2 -> "T"; else -> return false }
        val ok = begodeSequence(0L to command)
        if (ok) _lightOn.value = mode != 0
        return ok
    }

    fun begodeSetPedalsMode(mode: Int): Boolean {
        val command = when (mode) { 0 -> "h"; 1 -> "f"; 2 -> "s"; 3 -> "i"; else -> return false }
        return begodeSequence(0L to command)
    }

    fun begodeSetRollAngleMode(mode: Int): Boolean {
        val command = when (mode) { 0 -> ">"; 1 -> "="; 2 -> "<"; else -> return false }
        return begodeSequence(0L to command)
    }

    fun begodeSetLedMode(mode: Int): Boolean {
        if (mode !in 0..9) return false
        return begodeSequence(0L to "W", 100L to "M", 200L to mode.toString(), 300L to "b")
    }

    fun begodeSetBeeperVolume(level: Int): Boolean {
        if (level !in 0..9) return false
        return begodeSequence(0L to "W", 100L to "B", 200L to level.toString())
    }

    fun begodeSetWeakMagnetism(level: Int): Boolean {
        if (level !in 0..9) return false
        return begodeSequence(0L to "W", 100L to "C", 200L to level.toString())
    }

    fun begodeSetCutoutAngle(angle: Int): Boolean {
        if (angle !in 45..90 || angle % 5 != 0) return false
        val step = (angle - 45) / 5
        return begodeSequence(0L to "W", 200L to "X", 400L to step.toString())
    }

    fun begodeSetMiles(miles: Boolean): Boolean = begodeSequence(0L to if (miles) "m" else "g")

    fun begodeCalibrate(): Boolean = begodeSequence(0L to "c", 300L to "y")

    private fun startBegodeTransport() {
        // V/N are identity probes; b asks the controller for its normal stream/settings.
        // GotWay_xxxxxx is a factory BLE name, so retry identity traffic for a few
        // seconds instead of assuming the advertisement is the model name.
        repeat(10) { round ->
            listOf("V", "b", "N", "b").forEachIndexed { index, command ->
                commandHandler.postDelayed({
                    val active = WheelRepository.linkState.value == LinkState.CONNECTED && detectedProtocolLabel.startsWith("Begode")
                    if (!active) return@postDelayed
                    val stillResolving = !begodeDecoder.hasResolvedModel() || !begodeDecoder.hasFirmware()
                    if (round == 0 || stillResolving) writeTransportBytes(command.encodeToByteArray())
                }, round * 1100L + index * 180L)
            }
        }
    }''',
)

# ---------------------------------------------------------------------------
# APK metadata.
# ---------------------------------------------------------------------------
gradle = "app/build.gradle.kts"
replace_once(gradle, "        versionCode = 13", "        versionCode = 14")
replace_once(gradle, '        versionName = "0.0.13"', '        versionName = "0.0.14"')
replace_all(gradle, "// v0.0.13:", "// v0.0.14:")

print("EUC Lab v0.0.14 Begode/BMS/native-map patch applied")
