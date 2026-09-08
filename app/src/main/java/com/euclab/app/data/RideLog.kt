package com.euclab.app.data

import java.io.File
import java.time.Instant
import kotlin.math.max

data class RideSample(
    val timestampMs: Long,
    val speedKmh: Float,
    val voltageV: Float,
    val phaseCurrentA: Float,
    val mosfetTempC: Float,
    val pitchDeg: Float,
    val pwmPercent: Float,
    val tripKm: Float,
    val totalKm: Float,
    val batteryPercent: Int? = null,
    val autoOffSec: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val gpsAccuracyM: Float? = null,
    val altitudeM: Double? = null,
    val gpsSpeedMps: Float? = null,
    val bearingDeg: Float? = null,
)

data class RideLog(
    val file: File,
    val samples: List<RideSample>,
) {
    val startedAtMs: Long? get() = samples.firstOrNull()?.timestampMs
    val endedAtMs: Long? get() = samples.lastOrNull()?.timestampMs
    val durationMs: Long get() = max(0L, (endedAtMs ?: 0L) - (startedAtMs ?: 0L))
    val maxSpeedKmh: Float get() = samples.maxOfOrNull { kotlin.math.abs(it.speedKmh) } ?: 0f
    val maxPwm: Float get() = samples.maxOfOrNull { it.pwmPercent } ?: 0f
    val minVoltageV: Float get() = samples.minOfOrNull { it.voltageV } ?: 0f
    val maxTempC: Float get() = samples.maxOfOrNull { it.mosfetTempC } ?: 0f
    val distanceKm: Float get() = if (samples.size < 2) 0f else (samples.last().tripKm - samples.first().tripKm).coerceAtLeast(0f)
    val gpsSamples: List<RideSample> get() = samples.filter { it.latitude != null && it.longitude != null }
}

object RideLogReader {
    fun listRides(filesDir: File): List<File> = File(filesDir, "rides")
        .takeIf { it.exists() }
        ?.listFiles { file -> file.isFile && file.extension.equals("csv", true) }
        ?.sortedByDescending { it.lastModified() }
        .orEmpty()

    fun read(file: File): RideLog {
        val lines = file.readLines()
        if (lines.size < 2) return RideLog(file, emptyList())
        val headers = lines.first().split(',').map { it.trim() }
        val index = headers.withIndex().associate { it.value to it.index }

        fun col(row: List<String>, name: String): String? = index[name]?.let { row.getOrNull(it) }
        fun f(row: List<String>, name: String, fallback: Float = 0f): Float = col(row, name)?.toFloatOrNull() ?: fallback
        fun fn(row: List<String>, name: String): Float? = col(row, name)?.toFloatOrNull()
        fun d(row: List<String>, name: String): Double? = col(row, name)?.toDoubleOrNull()
        fun l(row: List<String>, name: String): Long? = col(row, name)?.toLongOrNull()
        fun i(row: List<String>, name: String): Int? = col(row, name)?.toIntOrNull()

        val samples = lines.drop(1).mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val row = line.split(',')
            val timestamp = l(row, "timestamp_ms")
                ?: col(row, "timestamp_iso")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                ?: return@mapNotNull null
            RideSample(
                timestampMs = timestamp,
                speedKmh = f(row, "speed_kmh"),
                voltageV = f(row, "voltage_v"),
                phaseCurrentA = f(row, "phase_current_a"),
                mosfetTempC = f(row, "mosfet_temp_c"),
                pitchDeg = f(row, "pitch_deg"),
                pwmPercent = f(row, "pwm_percent"),
                tripKm = f(row, "trip_km"),
                totalKm = f(row, "total_km"),
                batteryPercent = i(row, "battery_percent"),
                autoOffSec = i(row, "auto_off_sec"),
                latitude = d(row, "latitude"),
                longitude = d(row, "longitude"),
                gpsAccuracyM = fn(row, "gps_accuracy_m"),
                altitudeM = d(row, "altitude_m"),
                gpsSpeedMps = fn(row, "gps_speed_mps"),
                bearingDeg = fn(row, "bearing_deg"),
            )
        }
        return RideLog(file, samples)
    }
}
