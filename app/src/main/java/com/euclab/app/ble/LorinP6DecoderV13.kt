package com.euclab.app.ble

import com.euclab.app.data.Telemetry
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Clean-room InMotion P6 / Lorin decoder for EUC Lab v0.0.13.
 *
 * Transport is Nordic UART (6E400001 service, 0002 write, 0003 notify).
 * Lorin frames use AA AA header, escaped AA/A5 bytes, XOR checksum, and
 * extended P6 requests under flag 0x16.
 */
class LorinP6DecoderV13 {
    private var rawBuffer = ByteArray(0)
    private var lastTelemetry = Telemetry(
        timestampMs = 0L,
        speedKmh = 0f,
        voltageV = 0f,
        phaseCurrentA = 0f,
        mosfetTempC = 0f,
        pitchDeg = 0f,
        pwmPercent = 0f,
        tripKm = 0f,
        totalKm = 0f,
        firmwareRaw = 0,
        charging = false,
        batteryPercent = 0,
        model = "InMotion P6",
    )
    private var hasTelemetry = false

    fun reset() {
        rawBuffer = ByteArray(0)
        hasTelemetry = false
        lastTelemetry = lastTelemetry.copy(
            timestampMs = 0L,
            speedKmh = 0f,
            voltageV = 0f,
            phaseCurrentA = 0f,
            mosfetTempC = 0f,
            pitchDeg = 0f,
            pwmPercent = 0f,
            tripKm = 0f,
            totalKm = 0f,
            charging = false,
            batteryPercent = 0,
        )
    }

    fun looksLikeLorin(bytes: ByteArray): Boolean {
        for (i in 0 until (bytes.size - 1).coerceAtLeast(0)) {
            if (bytes[i] == 0xAA.toByte() && bytes[i + 1] == 0xAA.toByte()) return true
        }
        return false
    }

    fun feed(bytes: ByteArray): List<Telemetry> {
        if (bytes.isEmpty()) return emptyList()
        rawBuffer += bytes
        if (rawBuffer.size > 16384) rawBuffer = rawBuffer.takeLast(4096).toByteArray()
        val out = mutableListOf<Telemetry>()

        while (true) {
            val start = findHeader(rawBuffer)
            if (start < 0) {
                rawBuffer = if (rawBuffer.lastOrNull() == 0xAA.toByte()) byteArrayOf(0xAA.toByte()) else ByteArray(0)
                break
            }
            if (start > 0) rawBuffer = rawBuffer.copyOfRange(start, rawBuffer.size)
            val pulled = pullEscapedFrame(rawBuffer) ?: break
            val frame = pulled.first
            rawBuffer = rawBuffer.copyOfRange(pulled.second, rawBuffer.size)
            processFrame(frame)?.let(out::add)
        }
        return out
    }

    /** Read-only init sequence used by the official Lorin protocol family. */
    fun initCommands(): List<ByteArray> = listOf(
        buildMessage(FLAG_INITIAL, CMD_MAIN_INFO, byteArrayOf(0x01)),
        buildMessage(FLAG_INITIAL, CMD_MAIN_INFO, byteArrayOf(0x02)),
        buildMessage(FLAG_INITIAL, CMD_MAIN_INFO, byteArrayOf(0x06)),
        buildMessage(FLAG_EXTENDED, CMD_MAIN_INFO, byteArrayOf(0x21, 0x06)),
        buildMessage(FLAG_EXTENDED, CMD_MAIN_INFO, byteArrayOf(0x21, 0x20, 0x20)),
        buildMessage(FLAG_EXTENDED, CMD_MAIN_INFO, byteArrayOf(0x21, 0x11)),
    )

    fun keepAliveCommand(): ByteArray =
        buildMessage(FLAG_EXTENDED, CMD_MAIN_INFO, byteArrayOf(0x21, 0x07))

    private fun processFrame(frame: ByteArray): Telemetry? {
        if (!verify(frame)) return null
        val flags = u8(frame[2])
        val cmd = u8(frame[4]) and 0x7F
        val data = frame.copyOfRange(5, frame.size - 1)

        if (flags == FLAG_EXTENDED && cmd == CMD_MAIN_INFO && data.size >= 2) {
            return when (u8(data[1])) {
                0x87 -> parseExtendedRealtime(data)
                0x91 -> {
                    parseExtendedTotalStats(data)
                    if (hasTelemetry) snapshot() else null
                }
                else -> null
            }
        }

        // Some firmware may also answer the regular real-time request.
        if (flags == FLAG_DEFAULT && cmd == CMD_REAL_TIME && data.size >= 78) {
            return parseFullRealtime(data)
        }
        return null
    }

    private fun parseExtendedRealtime(data: ByteArray): Telemetry? {
        // Extended response prefix: 02 87 01 00, then 96-byte telemetry payload.
        if (data.size < 38) return null
        val p = data.copyOfRange(4, data.size)
        if (p.size < 34) return null

        val voltage = u16le(p, 0) / 100f
        val current = i16le(p, 2) / 100f
        val speed = i16le(p, 8) / 100f
        val outputRate = i16le(p, 14)
        val pitch = i16le(p, 20) / 100f
        val mileageMeters = u16le(p, 28) * 10L
        val consumed = u16le(p, 32)
        val soc = (100f - consumed / 100f).roundToInt().coerceIn(0, 100)

        if (voltage !in 100f..260f || abs(speed) > 180f) return null
        hasTelemetry = true
        lastTelemetry = lastTelemetry.copy(
            timestampMs = System.currentTimeMillis(),
            speedKmh = speed,
            voltageV = voltage,
            phaseCurrentA = current,
            pitchDeg = pitch,
            pwmPercent = (abs(outputRate) / 100f).coerceIn(0f, 120f),
            tripKm = mileageMeters / 1000f,
            charging = current < -0.3f && abs(speed) < 1f,
            batteryPercent = soc,
            model = "InMotion P6",
        )
        return snapshot()
    }

    private fun parseFullRealtime(data: ByteArray): Telemetry? {
        val voltage = u16le(data, 0) / 100f
        val current = i16le(data, 2) / 100f
        val speed = i16le(data, 8) / 100f
        val outputRate = i16le(data, 14)
        val pitch = i16le(data, 20) / 100f
        val mileageMeters = u16le(data, 28) * 10L
        val consumed = u16le(data, 32)
        val soc = (100f - consumed / 100f).roundToInt().coerceIn(0, 100)
        val mos = decodeTemp(data[58])

        if (voltage !in 100f..260f || abs(speed) > 180f) return null
        hasTelemetry = true
        lastTelemetry = lastTelemetry.copy(
            timestampMs = System.currentTimeMillis(),
            speedKmh = speed,
            voltageV = voltage,
            phaseCurrentA = current,
            mosfetTempC = mos,
            pitchDeg = pitch,
            pwmPercent = (abs(outputRate) / 100f).coerceIn(0f, 120f),
            tripKm = mileageMeters / 1000f,
            charging = current < -0.3f && abs(speed) < 1f,
            batteryPercent = soc,
            model = "InMotion P6",
        )
        return snapshot()
    }

    private fun parseExtendedTotalStats(data: ByteArray) {
        if (data.size < 6) return
        val meters = u32le(data, 2) * 10L
        if (meters in 0..100_000_000L) lastTelemetry = lastTelemetry.copy(totalKm = meters / 1000f)
    }

    private fun snapshot(): Telemetry = lastTelemetry.copy(timestampMs = System.currentTimeMillis())

    /** Returns unescaped frame and raw bytes consumed. */
    private fun pullEscapedFrame(raw: ByteArray): Pair<ByteArray, Int>? {
        if (raw.size < 6 || raw[0] != 0xAA.toByte() || raw[1] != 0xAA.toByte()) return null
        val out = ArrayList<Byte>(128)
        out += 0xAA.toByte(); out += 0xAA.toByte()
        var i = 2
        var expected = -1
        while (i < raw.size) {
            var b = raw[i]
            if (b == 0xA5.toByte()) {
                if (i + 1 >= raw.size) return null
                i++
                b = raw[i]
            }
            out += b
            i++
            if (out.size >= 4 && expected < 0) {
                expected = u8(out[3]) + 5
                if (expected !in 6..300) return null
            }
            if (expected > 0 && out.size == expected) return ByteArray(out.size) { out[it] } to i
            if (expected > 0 && out.size > expected) return null
        }
        return null
    }

    private fun verify(frame: ByteArray): Boolean {
        if (frame.size < 6 || frame[0] != 0xAA.toByte() || frame[1] != 0xAA.toByte()) return false
        val expected = u8(frame[3]) + 5
        if (frame.size != expected) return false
        var xor = 0
        for (i in 2 until frame.lastIndex) xor = xor xor u8(frame[i])
        return (xor and 0xFF) == u8(frame.last())
    }

    private fun findHeader(a: ByteArray): Int {
        for (i in 0 until a.size - 1) if (a[i] == 0xAA.toByte() && a[i + 1] == 0xAA.toByte()) return i
        return -1
    }

    private fun decodeTemp(b: Byte): Float = b.toInt() + 80f
    private fun u8(b: Byte): Int = b.toInt() and 0xFF
    private fun u16le(a: ByteArray, o: Int): Int = u8(a[o]) or (u8(a[o + 1]) shl 8)
    private fun i16le(a: ByteArray, o: Int): Int {
        val v = u16le(a, o)
        return if (v and 0x8000 != 0) v - 0x10000 else v
    }
    private fun u32le(a: ByteArray, o: Int): Long =
        u8(a[o]).toLong() or (u8(a[o + 1]).toLong() shl 8) or
            (u8(a[o + 2]).toLong() shl 16) or (u8(a[o + 3]).toLong() shl 24)

    companion object {
        const val NUS_SERVICE = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
        const val NUS_WRITE = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
        const val NUS_NOTIFY = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"

        private const val FLAG_INITIAL = 0x11
        private const val FLAG_DEFAULT = 0x14
        private const val FLAG_EXTENDED = 0x16
        private const val CMD_MAIN_INFO = 0x02
        private const val CMD_REAL_TIME = 0x04

        fun buildMessage(flags: Int, command: Int, data: ByteArray): ByteArray {
            val plain = ArrayList<Byte>(data.size + 5)
            plain += flags.toByte()
            plain += (data.size + 1).toByte()
            plain += command.toByte()
            plain.addAll(data.toList())
            var check = 0
            plain.forEach { check = check xor (it.toInt() and 0xFF) }

            val out = ArrayList<Byte>(plain.size + 8)
            out += 0xAA.toByte(); out += 0xAA.toByte()
            fun escaped(b: Byte) {
                val x = b.toInt() and 0xFF
                if (x == 0xAA || x == 0xA5) out += 0xA5.toByte()
                out += b
            }
            plain.forEach(::escaped)
            escaped(check.toByte())
            return ByteArray(out.size) { out[it] }
        }
    }
}
