package com.euclab.app.data

/** Readback values carried by Begode/Gotway status frames. Null = not received yet. */
data class BegodeSettingsV14(
    val pedalsMode: Int? = null,
    val speedAlarmsMode: Int? = null,
    val rollAngleMode: Int? = null,
    val inMiles: Boolean? = null,
    val tiltBackSpeedKmh: Int? = null,
    val ledMode: Int? = null,
    val lightMode: Int? = null,
    val powerOffTimeSec: Int? = null,
    val cutoutAngleDeg: Int? = null,
    val weakMagnetism: Int? = null,
    val beeperVolume: Int? = null,
)
