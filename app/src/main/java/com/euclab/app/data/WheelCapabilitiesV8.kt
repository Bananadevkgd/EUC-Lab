package com.euclab.app.data

enum class WheelProtocolFamily {
    VETERAN_BLE,
    LEAPERKIM_CAN,
    BEGODE,
    KINGSONG,
    INMOTION_V1,
    INMOTION_V2,
    UNKNOWN,
}

data class WheelCapabilities(
    val protocol: WheelProtocolFamily,
    val supportsLowBeam: Boolean = false,
    val supportsHighBeam: Boolean = false,
    val supportsSmartBms: Boolean = false,
    val hardwarePwm: Boolean = false,
    val batteryWh: Int? = null,
    val seriesCells: Int? = null,
)

object WheelCapabilitiesV8 {
    fun forModel(model: String?): WheelCapabilities {
        val name = model.orEmpty()
        return when {
            name.contains("Lynx S", ignoreCase = true) -> WheelCapabilities(
                protocol = WheelProtocolFamily.VETERAN_BLE,
                supportsLowBeam = true,
                supportsHighBeam = true,
                supportsSmartBms = true,
                hardwarePwm = true,
                seriesCells = 36,
            )
            name.contains("Sherman L", ignoreCase = true) -> WheelCapabilities(
                protocol = WheelProtocolFamily.VETERAN_BLE,
                supportsLowBeam = true,
                supportsSmartBms = true,
                hardwarePwm = true,
                batteryWh = 4000,
                seriesCells = 36,
            )
            name.contains("Lynx", ignoreCase = true) -> WheelCapabilities(
                protocol = WheelProtocolFamily.VETERAN_BLE,
                supportsLowBeam = true,
                supportsSmartBms = true,
                hardwarePwm = true,
                seriesCells = 36,
            )
            name.contains("Patton S", ignoreCase = true) || name.contains("Patton", ignoreCase = true) -> WheelCapabilities(
                protocol = WheelProtocolFamily.VETERAN_BLE,
                supportsLowBeam = true,
                supportsSmartBms = true,
                hardwarePwm = true,
                seriesCells = 30,
            )
            name.contains("Oryx", ignoreCase = true) -> WheelCapabilities(
                protocol = WheelProtocolFamily.VETERAN_BLE,
                supportsLowBeam = true,
                supportsSmartBms = true,
                hardwarePwm = true,
                seriesCells = 42,
            )
            name.contains("Sherman", ignoreCase = true) || name.contains("Abrams", ignoreCase = true) -> WheelCapabilities(
                protocol = WheelProtocolFamily.VETERAN_BLE,
                supportsLowBeam = true,
                hardwarePwm = true,
            )
            else -> WheelCapabilities(protocol = WheelProtocolFamily.UNKNOWN)
        }
    }
}
