package com.euclab.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.max

/**
 * Lightweight ride/session statistics that work without starting CSV logging.
 * A session is tied to the connected wheel address and survives brief reconnects
 * to that same wheel. Switching to a different wheel starts a fresh session.
 */
data class SessionStatsV13(
    val address: String = "",
    val model: String = "",
    val startedAtMs: Long = 0L,
    val lastTimestampMs: Long = 0L,
    val samples: Int = 0,
    val distanceKm: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val maxPwmPercent: Float = 0f,
    val minVoltageV: Float = 0f,
    val maxMosfetTempC: Float = 0f,
    val lastTripKm: Float? = null,
    val startTripKm: Float? = null,
    val integratedDistanceKm: Float = 0f,
)

object SessionTrackerV13 {
    private val _state = MutableStateFlow(SessionStatsV13())
    val state: StateFlow<SessionStatsV13> = _state.asStateFlow()

    fun beginFor(address: String) {
        val now = System.currentTimeMillis()
        val current = _state.value
        if (current.address.equals(address, ignoreCase = true) && current.startedAtMs > 0L) return
        _state.value = SessionStatsV13(address = address, startedAtMs = now, lastTimestampMs = now)
    }

    fun resetKeepingWheel() {
        val address = _state.value.address
        val now = System.currentTimeMillis()
        _state.value = SessionStatsV13(address = address, startedAtMs = now, lastTimestampMs = now)
    }

    fun onTelemetry(t: Telemetry) {
        var s = _state.value
        if (s.startedAtMs == 0L) {
            val now = t.timestampMs.takeIf { it > 0L } ?: System.currentTimeMillis()
            s = s.copy(startedAtMs = now, lastTimestampMs = now)
        }

        val now = t.timestampMs.takeIf { it > 0L } ?: System.currentTimeMillis()
        val dtMs = (now - s.lastTimestampMs).coerceIn(0L, 5000L)
        val integrated = if (s.samples > 0 && dtMs > 0L) {
            s.integratedDistanceKm + abs(t.speedKmh) * (dtMs / 3_600_000f)
        } else s.integratedDistanceKm

        val startTrip = s.startTripKm ?: t.tripKm.takeIf { it >= 0f }
        val wheelDelta = if (startTrip != null && t.tripKm >= startTrip && t.tripKm - startTrip < 500f) {
            t.tripKm - startTrip
        } else 0f
        val distance = max(integrated, wheelDelta)

        _state.value = s.copy(
            model = t.model,
            lastTimestampMs = now,
            samples = s.samples + 1,
            distanceKm = distance,
            maxSpeedKmh = max(s.maxSpeedKmh, abs(t.speedKmh)),
            maxPwmPercent = max(s.maxPwmPercent, t.pwmPercent),
            minVoltageV = when {
                t.voltageV <= 0f -> s.minVoltageV
                s.minVoltageV <= 0f -> t.voltageV
                else -> minOf(s.minVoltageV, t.voltageV)
            },
            maxMosfetTempC = max(s.maxMosfetTempC, t.mosfetTempC),
            lastTripKm = t.tripKm,
            startTripKm = startTrip,
            integratedDistanceKm = integrated,
        )
    }
}
