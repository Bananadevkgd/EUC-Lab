package com.euclab.app.data

data class VeteranSettingsSnapshot(
    val pedalHardness: Int? = null,
    val stopSpeedRaw: Int? = null,
    val pwmLimitRaw: Int? = null,
    val screenBacklightPercent: Int? = null,
    val transportMode: Boolean? = null,
    val wheelDisplayMiles: Boolean? = null,
    val voltageCorrection: Int? = null,
    val lowVoltageMode: Boolean? = null,
    val highSpeedMode: Boolean? = null,
    val keyTonePercent: Int? = null,
    val maxChargeVoltageRaw: Int? = null,
    val dynamicAssist: Int? = null,
    val accelerationLimit: Int? = null,
    val brakePressureAlarm: Int? = null,
    val lateralCutoffAngle: Int? = null,
)

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
    val autoOffSec: Int = 0,
    val alertSpeedKmh: Int = 0,
    val tiltbackSpeedKmh: Int = 0,
    val pedalsModeRaw: Int = 0,
    val keyTonePercent: Int? = null,
    val veteranSettings: VeteranSettingsSnapshot = VeteranSettingsSnapshot(),
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
