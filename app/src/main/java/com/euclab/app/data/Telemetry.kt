package com.euclab.app.data

data class Telemetry(
    val timestampMs: Long,
    val speedKmh: Float,
    val voltageV: Float,
    val phaseCurrentA: Float,
    val mosfetTempC: Float,
    val pitchDeg: Float,
    val pwmPercent: Float,
    val tripKm: Float,
    val totalKm: Float,
    val firmwareRaw: Int,
    val charging: Boolean,
    val batteryPercent: Int = 0,
    val model: String = "Veteran",
)

data class BleCandidate(
    val name: String,
    val address: String,
    val rssi: Int,
    val likelyEuc: Boolean,
)

enum class LinkState {
    IDLE, SCANNING, CONNECTING, DISCOVERING, CONNECTED, ERROR
}
