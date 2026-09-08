package com.euclab.app.ble

import com.euclab.app.data.BmsPack
import com.euclab.app.data.BmsSnapshot
import com.euclab.app.data.Telemetry
import com.euclab.app.data.VeteranSettingsSnapshot
import java.util.zip.CRC32
import kotlin.math.abs
import kotlin.math.roundToInt

/** Veteran / LeaperKim stream decoder. */
class VeteranFrameDecoder {
    private val queue = ArrayList<Byte>(192)

    private val bms1Cells = FloatArray(36)
    private val bms2Cells = FloatArray(36)
    private var bms1Current: Float? = null
    private var bms2Current: Float? = null
    private var bms1Temps: List<Float> = emptyList()
    private var bms2Temps: List<Float> = emptyList()
    private var bmsSeen = false
    private var latestSettings = VeteranSettingsSnapshot()

    fun reset() {
        queue.clear()
        bms1Cells.fill(0f)
        bms2Cells.fill(0f)
        bms1Current = null
        bms2Current = null
        bms1Temps = emptyList()
        bms2Temps = emptyList()
        bmsSeen = false
        latestSettings = VeteranSettingsSnapshot()
    }

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

    fun latestBmsSnapshot(): BmsSnapshot? {
        if (!bmsSeen) return null
        return BmsSnapshot(
            timestampMs = System.currentTimeMillis(),
            pack1 = BmsPack(1, bms1Cells.toList(), bms1Current, bms1Temps),
            pack2 = BmsPack(2, bms2Cells.toList(), bms2Current, bms2Temps),
        )
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

        val voltageRaw = u16be(frame, 4)
        val voltage = voltageRaw / 100f
        val speed = i16be(frame, 6) / 10f
        val tripMeters = wordSwappedU32(frame, 8)
        val totalMeters = wordSwappedU32(frame, 12)
        val phaseCurrent = i16be(frame, 16) / 10f
        val temperature = i16be(frame, 18) / 100f
        val autoOffSec = u16be(frame, 20)
        val chargeMode = u16be(frame, 22)
        val alertSpeed = u16be(frame, 24)
        val tiltbackSpeed = u16be(frame, 26)
        val firmware = u16be(frame, 28)
        val modelVersion = firmware / 1000
        val pedalsMode = u16be(frame, 30)
        val pitch = i16be(frame, 32) / 100f
        val pwm = u16be(frame, 34) / 100f

        if (voltage !in 20f..220f || abs(speed) > 160f || pwm !in 0f..120f) return null

        decodeExtendedSettings(frame)
        if (modelVersion >= 5) decodeSmartBms(frame)

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
            charging = chargeMode > 0,
            batteryPercent = batteryPercent(modelVersion, voltageRaw),
            model = modelName(modelVersion),
            autoOffSec = autoOffSec,
            alertSpeedKmh = alertSpeed,
            tiltbackSpeedKmh = tiltbackSpeed,
            pedalsModeRaw = pedalsMode,
            keyTonePercent = latestSettings.keyTonePercent,
            veteranSettings = latestSettings,
        )
    }

    private fun decodeExtendedSettings(frame: ByteArray) {
        if (frame.size <= 47) return
        when (u8(frame[46])) {
            2 -> {
                val angle = u8(frame[47]).takeUnless { it == 0 || it == 0x80 }
                if (angle != null) latestSettings = latestSettings.copy(lateralCutoffAngle = angle)
            }
            8 -> {
                fun unsigned(offset: Int): Int? {
                    if (offset >= frame.size) return null
                    return u8(frame[offset]).takeUnless { it == 0x80 }
                }
                fun signed(offset: Int): Int? {
                    if (offset >= frame.size) return null
                    val raw = u8(frame[offset])
                    if (raw == 0x80) return null
                    return frame[offset].toInt()
                }
                fun bool(offset: Int): Boolean? = unsigned(offset)?.let { it != 0 }

                latestSettings = latestSettings.copy(
                    pedalHardness = unsigned(50) ?: latestSettings.pedalHardness,
                    stopSpeedRaw = unsigned(52) ?: latestSettings.stopSpeedRaw,
                    pwmLimitRaw = unsigned(53) ?: latestSettings.pwmLimitRaw,
                    screenBacklightPercent = unsigned(55) ?: latestSettings.screenBacklightPercent,
                    transportMode = bool(57) ?: latestSettings.transportMode,
                    wheelDisplayMiles = unsigned(58)?.let { it != 0 } ?: latestSettings.wheelDisplayMiles,
                    voltageCorrection = signed(59) ?: latestSettings.voltageCorrection,
                    lowVoltageMode = bool(60) ?: latestSettings.lowVoltageMode,
                    highSpeedMode = bool(61) ?: latestSettings.highSpeedMode,
                    keyTonePercent = unsigned(63)?.takeIf { it in 0..100 } ?: latestSettings.keyTonePercent,
                    maxChargeVoltageRaw = unsigned(64) ?: latestSettings.maxChargeVoltageRaw,
                    dynamicAssist = unsigned(66) ?: latestSettings.dynamicAssist,
                    accelerationLimit = unsigned(68) ?: latestSettings.accelerationLimit,
                    brakePressureAlarm = unsigned(69) ?: latestSettings.brakePressureAlarm,
                )
            }
        }
    }

    private fun decodeSmartBms(frame: ByteArray) {
        if (frame.size <= 46) return
        val page = u8(frame[46])
        if (page !in 0..7) return
        bmsSeen = true

        if ((page == 0 || page == 4) && frame.size > 72) {
            bms1Current = i16be(frame, 69) / 100f
            bms2Current = i16be(frame, 71) / 100f
        }

        val cells = if (page < 4) bms1Cells else bms2Cells
        when (page) {
            1, 5 -> for (i in 0 until 15) {
                val offset = 53 + i * 2
                if (offset + 1 < frame.size) cells[i] = u16be(frame, offset) / 1000f
            }
            2, 6 -> for (i in 0 until 15) {
                val offset = 53 + i * 2
                if (offset + 1 < frame.size) cells[i + 15] = u16be(frame, offset) / 1000f
            }
            3, 7 -> {
                for (i in 0 until 6) {
                    val offset = 59 + i * 2
                    if (offset + 1 < frame.size) cells[i + 30] = u16be(frame, offset) / 1000f
                }
                if (frame.size > 58) {
                    val temps = (0 until 6).mapNotNull { i ->
                        val offset = 47 + i * 2
                        if (offset + 1 < frame.size) i16be(frame, offset) / 100f else null
                    }
                    if (page == 3) bms1Temps = temps else bms2Temps = temps
                }
            }
        }
    }

    private fun batteryPercent(modelVersion: Int, voltageRaw: Int): Int {
        val value = when (modelVersion) {
            4, 7, 43 -> when {
                voltageRaw > 12525 -> 100
                voltageRaw > 10200 -> ((voltageRaw - 9975) / 25.5).roundToInt()
                voltageRaw > 9600 -> ((voltageRaw - 9600) / 67.5).roundToInt()
                else -> 0
            }
            5, 6, 9, 42, 44 -> when {
                voltageRaw > 15030 -> 100
                voltageRaw > 12240 -> ((voltageRaw - 11970) / 30.6).roundToInt()
                voltageRaw > 11520 -> ((voltageRaw - 11520) / 81.0).roundToInt()
                else -> 0
            }
            8 -> when {
                voltageRaw > 17535 -> 100
                voltageRaw > 14280 -> ((voltageRaw - 14123) / 34.125).roundToInt()
                voltageRaw > 13886 -> ((voltageRaw - 13886) / 85.3125).roundToInt()
                else -> 0
            }
            else -> when {
                voltageRaw > 10020 -> 100
                voltageRaw > 8160 -> ((voltageRaw - 8070) / 19.5).roundToInt()
                voltageRaw > 7935 -> ((voltageRaw - 7935) / 48.75).roundToInt()
                else -> 0
            }
        }
        return value.coerceIn(0, 100)
    }

    private fun modelName(version: Int): String = when (version) {
        1 -> "Sherman"
        2 -> "Abrams"
        3 -> "Sherman S"
        4 -> "Patton"
        5 -> "Lynx"
        6 -> "Sherman L"
        7 -> "Patton S"
        8 -> "Oryx"
        9 -> "Lynx S"
        42 -> "Nosfet Apex"
        43 -> "Nosfet Aero"
        44 -> "Nosfet Aeon"
        else -> "Veteran"
    }

    private fun u8(value: Byte): Int = value.toInt() and 0xFF
    private fun u16be(bytes: ByteArray, offset: Int): Int = (u8(bytes[offset]) shl 8) or u8(bytes[offset + 1])
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
