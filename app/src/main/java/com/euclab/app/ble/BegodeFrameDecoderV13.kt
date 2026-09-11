package com.euclab.app.ble

import com.euclab.app.data.Telemetry
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Clean-room Begode / Gotway telemetry decoder for EUC Lab v0.0.13.
 *
 * Public interoperability facts used here:
 * - 24-byte binary frames: 55 AA ... type@18 context@19 5A 5A 5A 5A
 * - ASCII identity replies: NAME..., GW/JL (Begode), JN (Extreme Bull), CF/BF custom FW
 * - frame 0x00: voltage/speed/distance/phase current/controller temp
 * - frame 0x01: true pack voltage + BMS summary
 * - frame 0x04: odometer/settings
 * - frame 0x07: battery current, motor temp, hardware PWM
 *
 * User-facing settings commands are intentionally not implemented yet; v0.0.13
 * first makes discovery, connection and telemetry reliable.
 */
class BegodeFrameDecoderV13 {
    private var buffer = ByteArray(0)
    private var model = "Begode"
    private var firmware = ""
    private var brand = "Begode"

    private var speedKmh = 0f
    private var voltageV = 0f
    private var phaseCurrentA = 0f
    private var batteryCurrentA = 0f
    private var controllerTempC = 0f
    private var motorTempC = 0f
    private var pwmPercent = 0f
    private var tripKm = 0f
    private var totalKm = 0f
    private var batteryPercent = 0
    private var hasLive = false
    private var hasTrueVoltage = false
    private var hasHardwarePwm = false

    fun reset() {
        buffer = ByteArray(0)
        model = "Begode"
        firmware = ""
        brand = "Begode"
        speedKmh = 0f
        voltageV = 0f
        phaseCurrentA = 0f
        batteryCurrentA = 0f
        controllerTempC = 0f
        motorTempC = 0f
        pwmPercent = 0f
        tripKm = 0f
        totalKm = 0f
        batteryPercent = 0
        hasLive = false
        hasTrueVoltage = false
        hasHardwarePwm = false
    }

    fun modelName(): String = model
    fun brandName(): String = brand
    fun firmwareName(): String = firmware

    fun looksLikeBegode(bytes: ByteArray): Boolean {
        if (bytes.size >= 2) {
            for (i in 0 until bytes.size - 1) {
                if (bytes[i] == 0x55.toByte() && bytes[i + 1] == 0xAA.toByte()) return true
            }
        }
        val s = runCatching { bytes.decodeToString().trim().uppercase() }.getOrDefault("")
        return s.startsWith("NAME") || s.startsWith("GW") || s.startsWith("JL") ||
            s.startsWith("JN") || s.startsWith("CF") || s.startsWith("BF")
    }

    fun feed(bytes: ByteArray): List<Telemetry> {
        if (bytes.isEmpty()) return emptyList()
        parseAsciiIdentity(bytes)

        buffer += bytes
        if (buffer.size > 8192) buffer = buffer.takeLast(2048).toByteArray()

        val out = mutableListOf<Telemetry>()
        while (true) {
            val start = findHeader(buffer)
            if (start < 0) {
                buffer = if (buffer.lastOrNull() == 0x55.toByte()) byteArrayOf(0x55) else ByteArray(0)
                break
            }
            if (start > 0) buffer = buffer.copyOfRange(start, buffer.size)
            if (buffer.size < 24) break

            val frame = buffer.copyOfRange(0, 24)
            if (!validFrame(frame)) {
                buffer = buffer.copyOfRange(1, buffer.size)
                continue
            }
            buffer = buffer.copyOfRange(24, buffer.size)
            processFrame(frame)?.let(out::add)
        }
        return out
    }

    private fun parseAsciiIdentity(bytes: ByteArray) {
        if (bytes.isEmpty() || bytes[0] == 0x55.toByte()) return
        val s = runCatching { bytes.decodeToString().trim().trim('\u0000') }.getOrNull() ?: return
        val u = s.uppercase()
        when {
            u.startsWith("NAME") -> {
                val value = s.drop(4).trim().trimStart(':', '=', ' ')
                if (value.isNotBlank()) model = normalizeModel(value)
            }
            u.startsWith("GW") || u.startsWith("JL") -> {
                brand = "Begode"
                firmware = s.take(40)
            }
            u.startsWith("JN") -> {
                brand = "Extreme Bull"
                firmware = s.take(40)
            }
            u.startsWith("CF") -> {
                brand = "Begode"
                firmware = s.take(40)
            }
            u.startsWith("BF") -> {
                brand = "Begode"
                firmware = s.take(40)
            }
        }
    }

    private fun normalizeModel(value: String): String {
        val cleaned = value.trim().replace(Regex("\\s+"), " ")
        val upper = cleaned.uppercase()
        return when {
            upper == "RACE" -> "Begode RACE"
            upper.startsWith("ET MAX") || upper.startsWith("ETMAX") -> "Begode ET Max"
            upper.startsWith("FALCON") -> "Begode Falcon"
            upper.startsWith("BLITZ PRO") -> "Begode Blitz Pro"
            upper.startsWith("BLITZ") -> "Begode Blitz"
            upper.startsWith("EX30") -> "Begode EX30"
            upper.startsWith("MASTER PRO") -> "Begode Master Pro"
            upper.startsWith("MASTER") -> "Begode Master"
            upper.startsWith("T4 PRO") -> "Begode T4 Pro"
            upper.startsWith("T4") -> "Begode T4"
            upper.startsWith("EXTREME") -> "Begode Extreme"
            upper.startsWith("MTEN5") || upper.startsWith("MTEN 5") -> "Begode Mten 5"
            upper.startsWith("MTEN4") || upper.startsWith("MTEN 4") -> "Begode Mten 4"
            upper.startsWith("A2") -> "Begode A2"
            upper.startsWith("NIKOLA") -> "Begode $cleaned"
            upper.startsWith("COMMANDER") -> "Extreme Bull $cleaned"
            cleaned.startsWith("Begode", true) || cleaned.startsWith("Extreme Bull", true) -> cleaned
            else -> "${if (brand == "Extreme Bull") "Extreme Bull" else "Begode"} $cleaned"
        }
    }

    private fun processFrame(f: ByteArray): Telemetry? {
        when (u8(f[18])) {
            0x00 -> processLive(f)
            0x01 -> processExtended(f)
            0x04 -> processSettingsDistance(f)
            0x07 -> processCurrentTempPwm(f)
        }
        return if (hasLive) snapshot() else null
    }

    private fun processLive(f: ByteArray) {
        val rawVoltage = u16be(f, 2)
        val rawSpeed = i16be(f, 4)
        val rawDistance = u16be(f, 8)
        val rawPhase = i16be(f, 10)
        val rawTemp = i16be(f, 12)

        // Begode speed wire units convert to km/h as raw * 0.036.
        speedKmh = abs(rawSpeed) * 0.036f
        phaseCurrentA = abs(rawPhase) / 100f
        tripKm = rawDistance / 1000f
        controllerTempC = rawTemp / 340f + 36.53f

        if (!hasTrueVoltage) {
            val scale = modelProfile(model)?.fullVoltage?.div(67.2f) ?: 1f
            voltageV = rawVoltage / 100f * scale
        }
        batteryPercent = estimateSoc(voltageV, model)
        hasLive = rawVoltage > 0
    }

    private fun processExtended(f: ByteArray) {
        // True pack voltage: BE short at bytes 6-7, in 0.1 V units.
        val trueV = u16be(f, 6) / 10f
        if (trueV in 20f..260f) {
            voltageV = trueV
            hasTrueVoltage = true
            batteryPercent = estimateSoc(voltageV, model)
        }
    }

    private fun processSettingsDistance(f: ByteArray) {
        val meters = u32be(f, 2)
        if (meters in 0..20_000_000L) totalKm = meters / 1000f
    }

    private fun processCurrentTempPwm(f: ByteArray) {
        batteryCurrentA = (-i16be(f, 2)) / 100f
        val mt = i16be(f, 6)
        if (mt in -50..200) motorTempC = mt.toFloat()
        val hw = i16be(f, 8)
        if (hw in 1..100) {
            hasHardwarePwm = true
            pwmPercent = abs(hw).toFloat()
        }
    }

    private fun snapshot(): Telemetry = Telemetry(
        timestampMs = System.currentTimeMillis(),
        speedKmh = speedKmh,
        voltageV = voltageV,
        phaseCurrentA = phaseCurrentA,
        mosfetTempC = if (motorTempC != 0f) maxOf(controllerTempC, motorTempC) else controllerTempC,
        pitchDeg = 0f,
        pwmPercent = pwmPercent,
        tripKm = tripKm,
        totalKm = totalKm,
        firmwareRaw = 0,
        charging = batteryCurrentA < -0.3f && speedKmh < 1f,
        batteryPercent = batteryPercent.coerceIn(0, 100),
        model = model,
    )

    private data class Profile(val fullVoltage: Float, val emptyVoltage: Float)

    private fun modelProfile(name: String): Profile? {
        val n = name.uppercase()
        return when {
            "RACE" in n -> Profile(210f, 155f)
            "ET MAX" in n || "ETMAX" in n -> Profile(168f, 124f)
            "BLITZ PRO" in n -> Profile(168f, 124f)
            "BLITZ" in n -> Profile(134.4f, 99f)
            "EX30" in n || "MASTER" in n || "EXTREME" in n -> Profile(134.4f, 99f)
            "FALCON" in n || "T4" in n -> Profile(100.8f, 72f)
            "MTEN 5" in n || "MTEN5" in n || "MTEN 4" in n || "MTEN4" in n || "A2" in n -> Profile(84f, 62f)
            "GT PRO" in n || "COMMANDER MAX" in n -> Profile(168f, 116f)
            "COMMANDER" in n -> Profile(134.4f, 97.6f)
            else -> null
        }
    }

    private fun estimateSoc(voltage: Float, name: String): Int {
        if (voltage <= 0f) return 0
        val p = modelProfile(name) ?: return 0
        if (voltage <= p.emptyVoltage) return 0
        if (voltage >= p.fullVoltage) return 100
        val x = (voltage - p.emptyVoltage) / (p.fullVoltage - p.emptyVoltage)
        // Slightly conservative curved estimate; direct BMS SOC can supersede this later.
        return (100f * x * (0.82f + 0.18f * x)).roundToInt().coerceIn(0, 100)
    }

    private fun validFrame(f: ByteArray): Boolean = f.size == 24 &&
        f[0] == 0x55.toByte() && f[1] == 0xAA.toByte() &&
        f[20] == 0x5A.toByte() && f[21] == 0x5A.toByte() &&
        f[22] == 0x5A.toByte() && f[23] == 0x5A.toByte()

    private fun findHeader(data: ByteArray): Int {
        for (i in 0 until data.size - 1) if (data[i] == 0x55.toByte() && data[i + 1] == 0xAA.toByte()) return i
        return -1
    }

    private fun u8(b: Byte): Int = b.toInt() and 0xFF
    private fun u16be(a: ByteArray, o: Int): Int = (u8(a[o]) shl 8) or u8(a[o + 1])
    private fun i16be(a: ByteArray, o: Int): Int {
        val v = u16be(a, o)
        return if (v and 0x8000 != 0) v - 0x10000 else v
    }
    private fun u32be(a: ByteArray, o: Int): Long =
        (u8(a[o]).toLong() shl 24) or (u8(a[o + 1]).toLong() shl 16) or
            (u8(a[o + 2]).toLong() shl 8) or u8(a[o + 3]).toLong()
}
