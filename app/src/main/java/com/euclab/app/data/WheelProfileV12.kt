package com.euclab.app.data

enum class WheelBrandV12 {
    LEAPERKIM,
    KINGSONG,
    BEGODE,
    EXTREME_BULL,
    INMOTION,
    NOSFET,
    NINEBOT,
    UNKNOWN,
}

enum class FeatureSupportV12 { YES, NO, UNKNOWN }

data class WheelProfileV12(
    val brand: WheelBrandV12,
    val protocol: WheelProtocolFamily,
    val displayBrand: String,
    val smartBms: FeatureSupportV12 = FeatureSupportV12.UNKNOWN,
    val light: FeatureSupportV12 = FeatureSupportV12.UNKNOWN,
    val beep: FeatureSupportV12 = FeatureSupportV12.UNKNOWN,
    val seriesCells: Int? = null,
    val voltageClass: String? = null,
    val legacy: Boolean = false,
)

object WheelProfilesV12 {
    fun forModel(model: String?): WheelProfileV12 {
        val raw = model.orEmpty().trim()
        val n = raw.uppercase()

        if (n.startsWith("KS-") || n.contains("KINGSONG")) {
            val legacy14 = n.startsWith("KS-14SMD") || n.startsWith("KS-14D") ||
                n.startsWith("KS-14M") || n.startsWith("KS-14S")
            return WheelProfileV12(
                brand = WheelBrandV12.KINGSONG,
                protocol = WheelProtocolFamily.KINGSONG,
                displayBrand = "KingSong",
                smartBms = if (legacy14) FeatureSupportV12.NO else FeatureSupportV12.UNKNOWN,
                light = FeatureSupportV12.YES,
                beep = FeatureSupportV12.YES,
                seriesCells = if (legacy14) 16 else null,
                voltageClass = if (legacy14) "67.2 V" else null,
                legacy = legacy14,
            )
        }

        if (n.contains("SHERMAN") || n.contains("LYNX") || n.contains("PATTON") ||
            n.contains("ORYX") || n.contains("ABRAMS") || n.contains("APEX") ||
            n.contains("AERO") || n.contains("AEON") || n.contains("XENO")) {
            val caps = WheelCapabilitiesV8.forModel(raw)
            return WheelProfileV12(
                brand = WheelBrandV12.LEAPERKIM,
                protocol = caps.protocol,
                displayBrand = "LeaperKim / Veteran",
                smartBms = if (caps.supportsSmartBms) FeatureSupportV12.YES else FeatureSupportV12.UNKNOWN,
                light = if (caps.supportsLowBeam) FeatureSupportV12.YES else FeatureSupportV12.UNKNOWN,
                beep = FeatureSupportV12.YES,
                seriesCells = caps.seriesCells,
            )
        }

        if (n.contains("BEGODE") || n.contains("GOTWAY")) return WheelProfileV12(
            WheelBrandV12.BEGODE, WheelProtocolFamily.BEGODE, "Begode / Gotway"
        )
        if (n.contains("EXTREME BULL") || n.startsWith("EB ")) return WheelProfileV12(
            WheelBrandV12.EXTREME_BULL, WheelProtocolFamily.BEGODE, "Extreme Bull"
        )
        if (n.contains("INMOTION") || n.matches(Regex("V(5|8|10|11|12|13|14).*"))) return WheelProfileV12(
            WheelBrandV12.INMOTION, WheelProtocolFamily.INMOTION_V2, "Inmotion"
        )
        if (n.contains("NOSFET")) return WheelProfileV12(
            WheelBrandV12.NOSFET, WheelProtocolFamily.VETERAN_BLE, "NOSFET"
        )
        if (n.contains("NINEBOT")) return WheelProfileV12(
            WheelBrandV12.NINEBOT, WheelProtocolFamily.UNKNOWN, "Ninebot"
        )

        return WheelProfileV12(WheelBrandV12.UNKNOWN, WheelProtocolFamily.UNKNOWN, "Unknown")
    }
}
