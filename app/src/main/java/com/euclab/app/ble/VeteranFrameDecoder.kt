package com.euclab.app.ble

import com.euclab.app.data.Telemetry
import java.util.zip.CRC32

/**
 * Fresh Veteran/LeaperKim decoder written from the public wire-format description.
 * It deliberately contains no battery-percent curve yet: for v0.0.1 we expose the
 * wheel's measured voltage and raw telemetry only.
 */
class VeteranFrameDecoder {
    private val queue = ArrayList<Byte>(192)

    fun reset() = queue.clear()

    fun feed(chunk: ByteArray): List<Telemetry> {
        if (chunk.isEmpty()) return emptyList()
        val result = mutableListOf<Telemetry>()

        chunk.forEach { byte ->
            queue += byte
            alignToMagic()

            while (true) {
                val frame = pullFrame() ?: break
                decodeTelemetry(frame)?.let(result::add)
            }
        }
        return result
    }

    private fun alignToMagic() {
        while (queue.isNotEmpty()) {
            val good0 = queue.size < 1 || queue[0] == MAGIC[0]
            val good1 = queue.size < 2 || queue[1] == MAGIC[1]
            val good2 = queue.size < 3 || queue[2] == MAGIC[2]
            if (good0 && good1 && good2) return
            queue.removeAt(0)
        }
    }

    private fun pullFrame(): ByteArray? {
        if (queue.size < 4) return null
        val len = u8(queue[3])
        val total = len + 4
        if (total !in 20..180) {
            queue.removeAt(0)
            alignToMagic()
            return null
        }
        if (queue.size < total) return null

        val frame = ByteArray(total) { queue[it] }
        val isLong = len > 38
        if (isLong && !crcIsValid(frame, len)) {
            queue.removeAt(0)
            alignToMagic()
            return null
        }

        repeat(total) { queue.removeAt(0) }
        alignToMagic()
        return frame
    }

    private fun crcIsValid(frame: ByteArray, len: Int): Boolean {
        if (frame.size < len + 4) return false
        val actual = CRC32().apply { update(frame, 0, len) }.value
        val expected = u32be(frame, len)
        return actual == expected
    }

    private fun decodeTelemetry(frame: ByteArray): Telemetry? {
        if (frame.size < 36) return null
        if (frame[0] != MAGIC[0] || frame[1] != MAGIC[1] || frame[2] != MAGIC[2]) return null

        val voltage = u16be(frame, 4) / 100f
        val speed = i16be(frame, 6) / 10f
        val tripMeters = wordSwappedU32(frame, 8)
        val totalMeters = wordSwappedU32(frame, 12)
        val phaseCurrent = i16be(frame, 16) / 10f
        val temperature = i16be(frame, 18) / 100f
        val firmware = u16be(frame, 28)
        val pitch = i16be(frame, 32) / 100f
        val pwm = u16be(frame, 34) / 100f
        val charging = frame.size > 23 && u8(frame[23]) > 0

        // Reject obvious false alignments on legacy no-CRC frames.
        if (voltage !in 20f..220f || kotlin.math.abs(speed) > 160f || pwm !in 0f..120f) return null

        return Telemetry(
            timestampMs = System.currentTimeMillis(),
            speedKmh = speed,
            voltageV = voltage,
            phaseCurrentA = phaseCurrent,
            mosfetTempC = temperature,
            pitchDeg = pitch,
            pwmPercent = pwm,
            tripKm = tripMeters / 1000f,
            totalKm = totalMeters / 1000f,
            firmwareRaw = firmware,
            charging = charging,
        )
    }

    private fun u8(value: Byte): Int = value.toInt() and 0xFF

    private fun u16be(bytes: ByteArray, offset: Int): Int =
        (u8(bytes[offset]) shl 8) or u8(bytes[offset + 1])

    private fun i16be(bytes: ByteArray, offset: Int): Int {
        val raw = u16be(bytes, offset)
        return if (raw and 0x8000 != 0) raw - 0x10000 else raw
    }

    private fun u32be(bytes: ByteArray, offset: Int): Long =
        (u8(bytes[offset]).toLong() shl 24) or
            (u8(bytes[offset + 1]).toLong() shl 16) or
            (u8(bytes[offset + 2]).toLong() shl 8) or
            u8(bytes[offset + 3]).toLong()

    private fun wordSwappedU32(bytes: ByteArray, offset: Int): Long =
        (u8(bytes[offset + 2]).toLong() shl 24) or
            (u8(bytes[offset + 3]).toLong() shl 16) or
            (u8(bytes[offset]).toLong() shl 8) or
            u8(bytes[offset + 1]).toLong()

    companion object {
        private val MAGIC = byteArrayOf(0xDC.toByte(), 0x5A, 0x5C)
    }
}
