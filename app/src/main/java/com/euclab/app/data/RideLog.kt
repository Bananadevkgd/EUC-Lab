package com.euclab.app.data

import java.io.File
import java.time.Instant
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

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
    val gpsSamples: List<RideSample> get() = samples.filter { it.latitude != null && it.longitude != null }

    /**
     * Prefer the wheel trip counter when it actually moved. Some protocols/models
     * don't expose a useful per-trip value, so fall back to the GPS trace instead of
     * displaying 0.0 km next to hundreds of valid GPS samples.
     */
    val distanceKm: Float
        get() {
            if (samples.size >= 2) {
                val wheelDelta = samples.last().tripKm - samples.first().tripKm
                if (wheelDelta > 0.005f) return wheelDelta
            }
            val gps = gpsSamples
            if (gps.size < 2) return 0f
            var meters = 0.0
            for (i in 1 until gps.size) {
                val a = gps[i - 1]
                val b = gps[i]
                val accuracy = maxOf(a.gpsAccuracyM ?: 0f, b.gpsAccuracyM ?: 0f)
                if (accuracy > 80f) continue
                val segment = haversineMeters(a.latitude!!, a.longitude!!, b.latitude!!, b.longitude!!)
                // Reject impossible phone-GPS jumps while keeping normal EUC speeds.
                val dtSec = ((b.timestampMs - a.timestampMs).coerceAtLeast(1L)) / 1000.0
                if (segment / dtSec <= 55.0) meters += segment
            }
            return (meters / 1000.0).toFloat()
        }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }
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
