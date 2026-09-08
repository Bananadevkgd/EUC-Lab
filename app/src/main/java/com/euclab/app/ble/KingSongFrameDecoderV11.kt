package com.euclab.app.ble

import com.euclab.app.data.Telemetry

/**
 * Minimal clean-room KingSong telemetry decoder for EUC Lab v0.0.11.
 *
 * Protocol facts used here are public interoperability facts:
 * fixed 20-byte frames, AA 55 header, frame type at byte 16,
 * 14 5A 5A trailer for standard frames, and the documented field scales.
 * No control/settings commands are implemented here.
 */
class KingSongFrameDecoderV11 {
    private var buffer = ByteArray(0)
    private var model = "KingSong"
    private var advertisedName = ""
    private var voltageV = 0f
    private var speedKmh = 0f
    private var currentA = 0f
    private var tempC = 0f
    private var pwmPercent = 0f
    private var batteryPercent = 0
    private var charging = false
    private var hasLiveData = false

    fun reset() {
        buffer = ByteArray(0)
        model = "KingSong"
        advertisedName = ""
        voltageV = 0f
        speedKmh = 0f
        currentA = 0f
        tempC = 0f
        pwmPercent = 0f
        batteryPercent = 0
        charging = false
        hasLiveData = false
    }

    fun setAdvertisedName(name: String?) {
        advertisedName = name.orEmpty()
        if (model == "KingSong") parseAdvertisedModel(advertisedName)?.let { model = it }
    }

    fun feed(bytes: ByteArray): List<Telemetry> {
        if (bytes.isEmpty()) return emptyList()
        buffer += bytes
        if (buffer.size > 4096) buffer = buffer.takeLast(1024).toByteArray()

        val out = mutableListOf<Telemetry>()
        while (true) {
            val start = findHeader(buffer)
            if (start < 0) {
                buffer = if (buffer.lastOrNull() == 0xAA.toByte()) byteArrayOf(0xAA.toByte()) else ByteArray(0)
                break
            }
            if (start > 0) buffer = buffer.copyOfRange(start, buffer.size)
            if (buffer.size < 20) break

            val frame = buffer.copyOfRange(0, 20)
            if (!looksLikeFrame(frame)) {
                buffer = buffer.copyOfRange(1, buffer.size)
                continue
            }
            buffer = buffer.copyOfRange(20, buffer.size)
            processFrame(frame)?.let(out::add)
        }
        return out
    }

    fun looksLikeKingSong(bytes: ByteArray): Boolean {
        if (bytes.size < 2) return false
        for (i in 0 until bytes.size - 1) {
            if (bytes[i] == 0xAA.toByte() && bytes[i + 1] == 0x55.toByte()) return true
        }
        return false
    }

    private fun processFrame(frame: ByteArray): Telemetry? {
        when (u8(frame[16])) {
            0xBB -> {
                val raw = frame.copyOfRange(2, 16)
                    .takeWhile { it != 0.toByte() }
                    .toByteArray()
                    .decodeToString()
                    .trim()
                if (raw.isNotBlank()) {
                    advertisedName = raw
                    parseModelFromName(raw)?.let { model = it }
                }
            }
            0xA9 -> {
                val rawVoltage = u16le(frame, 2)
                val rawSpeed = s16le(frame, 4)
                val rawCurrent = s16le(frame, 10)
                val rawTemp = s16le(frame, 12)
                voltageV = rawVoltage / 100f
                speedKmh = rawSpeed / 100f
                currentA = rawCurrent / 100f
                tempC = rawTemp / 100f
                if (batteryPercent <= 0) batteryPercent = estimateBattery(rawVoltage, model)
                hasLiveData = rawVoltage > 0
            }
            0xF5 -> {
                pwmPercent = u8(frame[15]).coerceIn(0, 100).toFloat()
            }
            0xF6 -> {
                val socRaw = u8(frame[4])
                if (socRaw in 1..101) batteryPercent = socRaw - 1
            }
            0xB9 -> {
                charging = u8(frame[13]) != 0
            }
            0xC9 -> {
                charging = (u8(frame[15]) and 0x10) != 0
            }
        }

        return if (hasLiveData) snapshot() else null
    }

    private fun snapshot(): Telemetry = Telemetry(
        timestampMs = System.currentTimeMillis(),
        speedKmh = speedKmh,
        voltageV = voltageV,
        // KingSong 0xA9 exposes wheel current. EUC Lab's current generic schema still
        // calls this slot phaseCurrentA; it will be renamed when the common schema is widened.
        phaseCurrentA = currentA,
        mosfetTempC = tempC,
        pitchDeg = 0f,
        pwmPercent = pwmPercent,
        tripKm = 0f,
        totalKm = 0f,
        firmwareRaw = 0,
        charging = charging,
        batteryPercent = batteryPercent.coerceIn(0, 100),
        model = model.ifBlank { "KingSong" },
    )

    private fun parseAdvertisedModel(name: String): String? {
        val upper = name.trim().uppercase()
        if (!upper.startsWith("KS-")) return null
        return upper.substringBefore(' ').take(24)
    }

    private fun parseModelFromName(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return null
        val parts = trimmed.split('-')
        return if (parts.size > 2 && parts.last().all(Char::isDigit)) {
            parts.dropLast(1).joinToString("-")
        } else trimmed
    }

    private fun estimateBattery(rawVoltage: Int, model: String): Int {
        val upper = model.uppercase()
        val (empty, full, step) = when {
            upper in setOf("KS-18L", "KS-16X", "KS-16XF", "KS-18LH", "KS-18LY", "KS-S18", "KS-S16", "KS-S16P") -> Triple(6250, 8250, 20)
            upper in setOf("KS-S20", "KS-S22") -> Triple(9375, 12375, 30)
            upper == "KS-S19" -> Triple(7500, 9900, 24)
            upper == "KS-F18P" -> Triple(11250, 14850, 36)
            upper == "KS-F22" -> Triple(11563, 15263, 37)
            upper == "KS-F22P" -> Triple(13125, 17325, 42)
            upper.startsWith("KS-X") -> Triple(3125, 4125, 10)
            upper.startsWith("KS-S9") -> Triple(3750, 4950, 12)
            upper.startsWith("KS-N") && !upper.startsWith("KS-N12P") -> Triple(4063, 5363, 13)
            else -> return 0
        }
        return when {
            rawVoltage <= empty -> 0
            rawVoltage >= full -> 100
            else -> ((rawVoltage - empty).toFloat() / step).toInt().coerceIn(0, 100)
        }
    }

    private fun looksLikeFrame(frame: ByteArray): Boolean =
        frame.size == 20 &&
            frame[0] == 0xAA.toByte() &&
            frame[1] == 0x55.toByte() &&
            frame[18] == 0x5A.toByte() &&
            frame[19] == 0x5A.toByte()

    private fun findHeader(data: ByteArray): Int {
        for (i in 0 until data.size - 1) {
            if (data[i] == 0xAA.toByte() && data[i + 1] == 0x55.toByte()) return i
        }
        return -1
    }

    private fun u8(b: Byte): Int = b.toInt() and 0xFF
    private fun u16le(a: ByteArray, o: Int): Int = u8(a[o]) or (u8(a[o + 1]) shl 8)
    private fun s16le(a: ByteArray, o: Int): Int {
        val v = u16le(a, o)
        return if (v and 0x8000 != 0) v - 0x10000 else v
    }
}
