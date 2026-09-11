package com.euclab.app.ble

import com.euclab.app.data.BegodeSettingsV14
import com.euclab.app.data.BmsPack
import com.euclab.app.data.BmsSnapshot
import com.euclab.app.data.Telemetry
import com.euclab.app.data.WheelVoltageCatalogV14
import com.euclab.app.data.WheelVoltageProfileV14
import kotlin.math.abs

/**
 * Begode / Gotway decoder for v0.0.14.
 *
 * Important separation:
 *  - Begode SOC is calculated from the controller's normalized frame-0 voltage.
 *  - Displayed pack voltage uses true-voltage frame 0x01 when available, otherwise
 *    a model voltage-class scaler.
 * This means a generic Bluetooth name such as GotWay_014506 no longer produces 0%.
 */
class BegodeFrameDecoderV14 {
    private var buffer = ByteArray(0)
    private var advertisedName = ""
    private var model = "Begode"
    private var firmware = ""
    private var brand = "Begode"
    private var profile: WheelVoltageProfileV14? = null

    private var speedKmh = 0f
    private var voltageV = 0f
    private var rawNormalizedVoltage = 0
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

    private var settings = BegodeSettingsV14()

    private data class MutableBms(
        val cells: FloatArray = FloatArray(56),
        var currentA: Float? = null,
        var temps: List<Float> = emptyList(),
        var seen: Boolean = false,
    )

    private val bms = Array(4) { MutableBms() }
    private var bmsSeen = false

    fun reset() {
        buffer = ByteArray(0)
        advertisedName = ""
        model = "Begode"
        firmware = ""
        brand = "Begode"
        profile = null
        speedKmh = 0f
        voltageV = 0f
        rawNormalizedVoltage = 0
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
        settings = BegodeSettingsV14()
        bms.forEach {
            it.cells.fill(0f)
            it.currentA = null
            it.temps = emptyList()
            it.seen = false
        }
        bmsSeen = false
    }

    fun setAdvertisedName(name: String) {
        advertisedName = name
        val match = WheelVoltageCatalogV14.match(name)
        if (match != null && match.brand in setOf("Begode", "Extreme Bull")) applyProfile(match)
    }

    fun modelName(): String = model
    fun brandName(): String = brand
    fun firmwareName(): String = firmware
    fun profile(): WheelVoltageProfileV14? = profile
    fun settingsSnapshot(): BegodeSettingsV14 = settings
    fun hasResolvedModel(): Boolean = profile != null || model != "Begode"
    fun hasFirmware(): Boolean = firmware.isNotBlank()

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

    fun latestBmsSnapshot(): BmsSnapshot? {
        if (!bmsSeen) return null
        val expected = profile?.seriesCells
        fun pack(i: Int): BmsPack = BmsPack(
            index = i + 1,
            cells = bms[i].cells.toList(),
            currentA = bms[i].currentA,
            temperaturesC = bms[i].temps,
            expectedCells = expected,
        )
        val expectedPacks = profile?.smartBmsPacks ?: 2
        return BmsSnapshot(
            timestampMs = System.currentTimeMillis(),
            pack1 = pack(0),
            pack2 = pack(1),
            pack3 = pack(2).takeIf { bms[2].seen || expectedPacks >= 3 },
            pack4 = pack(3).takeIf { bms[3].seen || expectedPacks >= 4 },
        )
    }

    private fun parseAsciiIdentity(bytes: ByteArray) {
        if (bytes.isEmpty() || bytes[0] == 0x55.toByte()) return
        val s = runCatching { bytes.decodeToString().trim().trim('\u0000') }.getOrNull() ?: return
        val u = s.uppercase()
        when {
            u.startsWith("NAME") -> {
                val value = s.drop(4).trim().trimStart(':', '=', ' ')
                if (value.isNotBlank()) {
                    val matched = WheelVoltageCatalogV14.match(value)
                    if (matched != null && matched.brand in setOf("Begode", "Extreme Bull")) {
                        applyProfile(matched)
                    } else {
                        model = normalizeModel(value)
                    }
                }
            }
            u.startsWith("GW") || u.startsWith("JL") -> {
                brand = "Begode"
                firmware = s.take(40)
            }
            u.startsWith("JN") -> {
                brand = "Extreme Bull"
                firmware = s.take(40)
            }
            u.startsWith("CF") || u.startsWith("BF") -> {
                brand = "Begode"
                firmware = s.take(40)
            }
        }
    }

    private fun processFrame(f: ByteArray): Telemetry? {
        when (u8(f[18])) {
            0x00 -> processLive(f)
            0x01 -> processExtended(f)
            0x02, 0x03, 0x05, 0x06 -> processBmsCells(f, u8(f[18]))
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
        val beeper = u8(f[17])

        rawNormalizedVoltage = rawVoltage
        speedKmh = abs(rawSpeed) * 0.036f
        phaseCurrentA = abs(rawPhase) / 100f
        tripKm = rawDistance / 1000f
        controllerTempC = rawTemp / 340f + 36.53f

        // Begode's own SOC calculation is based on the normalized raw controller
        // voltage, before the voltage-class multiplier is applied.
        batteryPercent = standardBegodeSoc(rawVoltage)

        if (!hasTrueVoltage) {
            val scale = profile?.fullVoltageV?.div(67.2f)
            voltageV = if (scale != null) rawVoltage / 100f * scale else rawVoltage / 100f
        }
        settings = settings.copy(beeperVolume = beeper.takeIf { it in 0..9 } ?: settings.beeperVolume)
        hasLive = rawVoltage > 0
    }

    private fun processExtended(f: ByteArray) {
        // True pack voltage is independent of the guessed/model voltage multiplier.
        val trueV = u16be(f, 6) / 10f
        if (trueV in 20f..260f) {
            voltageV = trueV
            hasTrueVoltage = true

            // GotWay_xxxxxx does not identify the model. A measured voltage above
            // all 176.4 V-class wheels is an unambiguous 210 V class in our catalog.
            if (profile == null) {
                WheelVoltageCatalogV14.inferBegodeModelFromTrueVoltage(trueV)?.let(::applyProfile)
            }
        }

        val packIndex = u8(f[19])
        if (packIndex in 0..3) {
            val pack = bms[packIndex]
            pack.seen = true
            pack.currentA = i16be(f, 8) / 10f
            val t1 = i16be(f, 10).toFloat()
            val t2 = i16be(f, 12).toFloat()
            pack.temps = listOf(t1, t2).filter { it in -60f..180f }
            bmsSeen = true
        }
    }

    private fun processBmsCells(f: ByteArray, frameType: Int) {
        val packIndex = when (frameType) {
            0x02 -> 0
            0x03 -> 1
            0x05 -> 2
            0x06 -> 3
            else -> return
        }
        val page = u8(f[19])
        val pack = bms[packIndex]
        for (i in 0 until 8) {
            val cellIndex = page * 8 + i
            if (cellIndex >= pack.cells.size) break
            val offset = (i + 1) * 2
            val volts = u16be(f, offset) / 1000f
            if (volts in 1.5f..5.0f) pack.cells[cellIndex] = volts
        }
        pack.seen = true
        bmsSeen = true
    }

    private fun processSettingsDistance(f: ByteArray) {
        val meters = u32be(f, 2)
        if (meters in 0..100_000_000L) totalKm = meters / 1000f

        val bits = u16be(f, 6)
        val rawPedals = (bits shr 13) and 0x03
        val pedals = if (rawPedals in 0..2) 2 - rawPedals else rawPedals
        val speedAlarms = (bits shr 10) and 0x03
        val roll = (bits shr 7) and 0x03
        val miles = (bits and 0x01) != 0
        val powerOff = u16be(f, 8)
        val rawTiltback = u16be(f, 10)
        val tiltback = rawTiltback.takeIf { it in 1..99 }
        val led = u8(f[13]).takeIf { it in 0..9 }
        val light = (u8(f[15]) and 0x03).takeIf { it in 0..2 }

        settings = settings.copy(
            pedalsMode = pedals,
            speedAlarmsMode = speedAlarms,
            rollAngleMode = roll,
            inMiles = miles,
            tiltBackSpeedKmh = tiltback,
            ledMode = led,
            lightMode = light,
            powerOffTimeSec = powerOff,
        )
    }

    private fun processCurrentTempPwm(f: ByteArray) {
        batteryCurrentA = (-i16be(f, 2)) / 100f
        val settingsByte = u8(f[5])
        val cutoutStep = settingsByte and 0x0F
        val weak = (settingsByte shr 4) and 0x0F
        val cutout = cutoutStep.takeIf { it in 0..9 }?.let { it * 5 + 45 }
        val weakValue = weak.takeIf { it in 0..9 }
        val mt = i16be(f, 6)
        if (mt in -60..200) motorTempC = mt.toFloat()
        val hw = i16be(f, 8)
        if (abs(hw) in 1..100) {
            hasHardwarePwm = true
            pwmPercent = abs(hw).toFloat()
        }
        settings = settings.copy(cutoutAngleDeg = cutout, weakMagnetism = weakValue)
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

    private fun applyProfile(value: WheelVoltageProfileV14) {
        profile = value
        model = value.model
        brand = value.brand
        if (!hasTrueVoltage && rawNormalizedVoltage > 0) {
            voltageV = rawNormalizedVoltage / 100f * (value.fullVoltageV / 67.2f)
        }
    }

    private fun normalizeModel(value: String): String {
        val cleaned = value.trim().replace(Regex("\\s+"), " ")
        return when {
            cleaned.startsWith("Begode", true) || cleaned.startsWith("Extreme Bull", true) -> cleaned
            else -> "${if (brand == "Extreme Bull") "Extreme Bull" else "Begode"} $cleaned"
        }
    }

    private fun standardBegodeSoc(rawVoltage: Int): Int = when {
        rawVoltage <= 5290 -> 0
        rawVoltage >= 6580 -> 100
        else -> ((rawVoltage - 5290) / 13).coerceIn(0, 100)
    }

    private fun validFrame(f: ByteArray): Boolean = f.size == 24 &&
        f[0] == 0x55.toByte() && f[1] == 0xAA.toByte() &&
        f[20] == 0x5A.toByte() && f[21] == 0x5A.toByte() &&
        f[22] == 0x5A.toByte() && f[23] == 0x5A.toByte()

    private fun findHeader(data: ByteArray): Int {
        for (i in 0 until data.size - 1) {
            if (data[i] == 0x55.toByte() && data[i + 1] == 0xAA.toByte()) return i
        }
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
