from pathlib import Path
import runpy

# Build v0.0.12 on top of the proven v0.0.11 patch chain.
runpy.run_path("scripts/run_v11.py", run_name="__main__")


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


# --- Route the Wheel tab by detected family instead of always showing Veteran settings. ---
ui = "app/src/main/java/com/euclab/app/AppUiV5.kt"
replace_once(
    ui,
    "                    V5Screen.WHEEL -> V7WheelScreen(ble, lang == V5Language.RU)",
    "                    V5Screen.WHEEL -> V12WheelScreen(ble, lang == V5Language.RU)",
)

# --- If a known legacy wheel cannot expose Smart BMS data, show a capability-aware page. ---
replace_once(
    ui,
    '''private fun V5Battery(lang: V5Language) {
    val bms by WheelRepository.bms.collectAsState()
    val telemetry by WheelRepository.telemetry.collectAsState()
    val advice = batteryAdvice(bms, lang)''',
    '''private fun V5Battery(lang: V5Language) {
    val bms by WheelRepository.bms.collectAsState()
    val telemetry by WheelRepository.telemetry.collectAsState()
    val profile = WheelProfilesV12.forModel(telemetry?.model)
    if (bms == null && profile.smartBms == FeatureSupportV12.NO) {
        V12NoSmartBmsBattery(telemetry, lang == V5Language.RU)
        return
    }
    val advice = batteryAdvice(bms, lang)''',
)

# --- Visible app version. ---
replace_all(ui, "v0.0.11", "v0.0.12")

# --- KingSong quick controls use KingSong frames; Veteran controls remain isolated. ---
ble = "app/src/main/java/com/euclab/app/ble/BleWheelManager.kt"
replace_once(
    ble,
    '''    fun setLight(on: Boolean): Boolean {
        // LeaperKim low beam is the family ASCII command. On Lynx S the high beam
        // is a separate vendor-frame command handled by setHighBeam().
        val ok = sendCommand(if (on) "SetLightON".encodeToByteArray() else "SetLightOFF".encodeToByteArray())
        if (ok) {
            _lightOn.value = on
            if (!on) _highBeamOn.value = false
        }
        return ok
    }''',
    '''    fun setLight(on: Boolean): Boolean {
        if (detectedProtocolLabel == "KingSong") {
            // KingSong light/mute command 0x73. Preserve voice enabled in byte 3;
            // byte 2 uses 0x12=off, 0x13=on (0x14 is auto, not used by this toggle).
            val frame = kingSongRequest(0x73)
            frame[2] = if (on) 0x13 else 0x12
            frame[3] = 0x00
            val ok = writeTransportBytes(frame)
            if (ok) _lightOn.value = on
            return ok
        }

        // LeaperKim low beam is the family ASCII command. On Lynx S the high beam
        // is a separate vendor-frame command handled by setHighBeam().
        val ok = sendCommand(if (on) "SetLightON".encodeToByteArray() else "SetLightOFF".encodeToByteArray())
        if (ok) {
            _lightOn.value = on
            if (!on) _highBeamOn.value = false
        }
        return ok
    }''',
)

replace_once(
    ble,
    '''    fun beep(): Boolean {
        val old = buildVeteranCommandOld(0x0E, 9, 1, byte5 = 0x00)
        val newer = buildVeteranCommandNew(0x0E, 9, 1, byte5 = 0x00, byte6 = 0x00)
        return sendStream(old + newer)
    }''',
    '''    fun beep(): Boolean {
        if (detectedProtocolLabel == "KingSong") {
            return writeTransportBytes(kingSongRequest(0x88))
        }
        val old = buildVeteranCommandOld(0x0E, 9, 1, byte5 = 0x00)
        val newer = buildVeteranCommandNew(0x0E, 9, 1, byte5 = 0x00, byte6 = 0x00)
        return sendStream(old + newer)
    }''',
)

# --- APK metadata. ---
gradle = "app/build.gradle.kts"
replace_once(gradle, "        versionCode = 11", "        versionCode = 12")
replace_once(gradle, '        versionName = "0.0.11"', '        versionName = "0.0.12"')
replace_all(gradle, "// v0.0.11:", "// v0.0.12:")

print("EUC Lab v0.0.12 capability-aware UI patch applied")
