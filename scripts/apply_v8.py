from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected exactly one match, got {count}: {old[:80]!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


def replace_all(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    if old not in text:
        raise RuntimeError(f"{path}: missing {old!r}")
    p.write_text(text.replace(old, new), encoding="utf-8")


# --- Home UI: Lynx S gets split low/high beam control. ---
ui = "app/src/main/java/com/euclab/app/AppUiV5.kt"
replace_once(
    ui,
    "    val lightOn by ble.lightOn.collectAsState()\n    var showAll by remember { mutableStateOf(false) }",
    "    val lightOn by ble.lightOn.collectAsState()\n    val highBeamOn by ble.highBeamOn.collectAsState()\n    var showAll by remember { mutableStateOf(false) }",
)
replace_once(
    ui,
    "        item { V5LightQuick(telemetry != null, lightOn, lang) { ble.setLight(!lightOn) } }",
    "        item { V8LightQuick(telemetry, lightOn, highBeamOn, lang == V5Language.RU, ble) }",
)
replace_all(ui, "v0.0.7", "v0.0.8")

# --- Expanded ride map: remove dependency on external Leaflet JS. ---
ride = "app/src/main/java/com/euclab/app/RideDetailV7.kt"
replace_once(
    ride,
    "                        key(mapMode) { R7Map(ride, mapMode, Modifier.fillMaxWidth().height(270.dp)) }",
    "                        key(mapMode) { V8RouteMap(ride, mapMode == RouteColorMode.PWM, Modifier.fillMaxWidth().height(270.dp)) }",
)

# --- Veteran transport: low beam = ASCII; Lynx S high beam = captured LkAp+LdAp. ---
ble = "app/src/main/java/com/euclab/app/ble/BleWheelManager.kt"
replace_once(
    ble,
    "    private val _lightOn = MutableStateFlow(false)\n    val lightOn: StateFlow<Boolean> = _lightOn.asStateFlow()",
    "    private val _lightOn = MutableStateFlow(false)\n    val lightOn: StateFlow<Boolean> = _lightOn.asStateFlow()\n\n    private val _highBeamOn = MutableStateFlow(false)\n    val highBeamOn: StateFlow<Boolean> = _highBeamOn.asStateFlow()",
)
replace_once(
    ble,
    '''    fun setLight(on: Boolean): Boolean {\n        val ver = currentModelVersion()\n        val ok = if (ver >= 3) {\n            sendStream(buildVeteranCommandOld(0x0D, 8, if (on) 1 else 0) + buildVeteranCommandNew(0x0D, 8, if (on) 1 else 0))\n        } else sendCommand(if (on) "SetLightON".encodeToByteArray() else "SetLightOFF".encodeToByteArray())\n        if (ok) _lightOn.value = on\n        return ok\n    }''',
    '''    fun setLight(on: Boolean): Boolean {\n        // LeaperKim low beam is the family ASCII command. On Lynx S the high beam\n        // is a separate vendor-frame command handled by setHighBeam().\n        val ok = sendCommand(if (on) "SetLightON".encodeToByteArray() else "SetLightOFF".encodeToByteArray())\n        if (ok) {\n            _lightOn.value = on\n            if (!on) _highBeamOn.value = false\n        }\n        return ok\n    }\n\n    fun setHighBeam(on: Boolean): Boolean {\n        // Captured from the official LeaperKim app on Lynx S (mVer 9):\n        // LkAp payload 01 80 80 <state>, followed by LdAp payload 01 00 80 <state>.\n        if (currentModelVersion() != 9) return false\n        val state = if (on) 1 else 0\n        val old = buildVeteranCommandOld(0x0D, 8, state, byte5 = 0x01)\n        val newer = buildVeteranCommandNew(0x0D, 8, state, byte5 = 0x01, byte6 = 0x00)\n        val ok = sendStream(old + newer)\n        if (ok) _highBeamOn.value = on\n        return ok\n    }''',
)
replace_once(
    ble,
    "        currentAddress = address\n        WheelRepository.setLink(LinkState.CONNECTING,",
    "        currentAddress = address\n        _lightOn.value = false\n        _highBeamOn.value = false\n        WheelRepository.setLink(LinkState.CONNECTING,",
)
replace_once(
    ble,
    "        WheelRepository.clearBms()\n        WheelRepository.setLink(LinkState.IDLE, \"Disconnected\")",
    "        WheelRepository.clearBms()\n        _lightOn.value = false\n        _highBeamOn.value = false\n        WheelRepository.setLink(LinkState.IDLE, \"Disconnected\")",
)

# --- APK version. ---
gradle = "app/build.gradle.kts"
replace_once(gradle, "        versionCode = 7", "        versionCode = 8")
replace_once(gradle, '        versionName = "0.0.7"', '        versionName = "0.0.8"')
replace_all(gradle, "// v0.0.7:", "// v0.0.8:")

print("EUC Lab v0.0.8 source patch applied")
