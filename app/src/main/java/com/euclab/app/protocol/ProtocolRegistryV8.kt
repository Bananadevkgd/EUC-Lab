package com.euclab.app.protocol

import com.euclab.app.data.WheelProtocolFamily

/**
 * v0.0.8 protocol-family registry.
 *
 * The current BLE transport is still Veteran-first, but the rest of the app can now
 * reason in protocol families instead of hard-coding a single manufacturer. Each
 * family will get its own transport/decoder adapter while publishing the same common
 * telemetry model to the UI.
 */
object ProtocolRegistryV8 {
    fun inferFromAdvertisedName(name: String?): WheelProtocolFamily {
        val n = name.orEmpty().lowercase()
        return when {
            n.contains("veteran") || n.contains("sherman") || n.contains("lynx") || n.contains("patton") || n.startsWith("lk") -> WheelProtocolFamily.VETERAN_BLE
            n.contains("begode") || n.contains("gotway") || n.contains("extreme bull") || n.startsWith("gw") -> WheelProtocolFamily.BEGODE
            n.contains("kingsong") || n.startsWith("ks-") || n.startsWith("ks") -> WheelProtocolFamily.KINGSONG
            n.contains("inmotion") || n.startsWith("v10") || n.startsWith("v11") || n.startsWith("v12") || n.startsWith("v13") || n.startsWith("v14") -> WheelProtocolFamily.INMOTION_V2
            else -> WheelProtocolFamily.UNKNOWN
        }
    }

    fun decoderLabel(family: WheelProtocolFamily): String = when (family) {
        WheelProtocolFamily.VETERAN_BLE -> "Veteran / LeaperKim BLE"
        WheelProtocolFamily.LEAPERKIM_CAN -> "LeaperKim CAN-over-BLE"
        WheelProtocolFamily.BEGODE -> "Begode / Gotway"
        WheelProtocolFamily.KINGSONG -> "KingSong"
        WheelProtocolFamily.INMOTION_V1 -> "Inmotion V1"
        WheelProtocolFamily.INMOTION_V2 -> "Inmotion V2 / Lorin"
        WheelProtocolFamily.UNKNOWN -> "Unknown"
    }
}
