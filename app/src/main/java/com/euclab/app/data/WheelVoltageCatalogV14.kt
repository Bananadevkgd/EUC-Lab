package com.euclab.app.data

/**
 * Full-charge voltage metadata used for model identification, display and protocol
 * calibration. These are maximum pack voltages, not marketing/nominal voltage labels.
 * Battery SOC must still use the manufacturer's protocol/curve when available.
 */
data class WheelVoltageProfileV14(
    val model: String,
    val brand: String,
    val fullVoltageV: Float,
    val seriesCells: Int,
    val aliases: List<String> = emptyList(),
    val smartBmsPacks: Int? = null,
    val emptyVoltageV: Float? = null,
)

object WheelVoltageCatalogV14 {
    val entries: List<WheelVoltageProfileV14> = listOf(
        // Begode / Gotway
        p("Begode A1", "Begode", 42f, 10, "A1"),
        p("Begode Mten Mini", "Begode", 42f, 10, "MTEN MINI"),
        p("Begode ACM 16", "Begode", 67.2f, 16, "ACM 16"),
        p("Begode Tesla 67V", "Begode", 67.2f, 16, "TESLA 67V"),
        p("Begode A2", "Begode", 84f, 20, "A2", empty = 62f),
        p("Begode A5", "Begode", 84f, 20, "A5"),
        p("Begode C8", "Begode", 84f, 20, "C8"),
        p("Begode Mten 4", "Begode", 84f, 20, "MTEN4", "MTEN 4", empty = 62f),
        p("Begode Mten 5", "Begode", 84f, 20, "MTEN5", "MTEN 5", empty = 62f),
        p("Begode Nikola", "Begode", 84f, 20, "NIKOLA"),
        p("Begode Tesla 84V", "Begode", 84f, 20, "TESLA 84V", "TESLA 2", "TESLA T3"),
        p("Begode Monster 84V", "Begode", 84f, 20, "MONSTER 84V"),
        p("Begode Msuper X 84V", "Begode", 84f, 20, "MSUPER X 84V"),
        p("Begode EX", "Begode", 100.8f, 24, "EX"),
        p("Begode EX.N", "Begode", 100.8f, 24, "EX.N"),
        p("Begode EX20S", "Begode", 100.8f, 24, "EX20S"),
        p("Begode Falcon", "Begode", 100.8f, 24, "FALCON", empty = 72f),
        p("Begode Hero", "Begode", 100.8f, 24, "HERO"),
        p("Begode Monster Pro", "Begode", 100.8f, 24, "MONSTER PRO"),
        p("Begode Monster 100V", "Begode", 100.8f, 24, "MONSTER 100V"),
        p("Begode Msuper X 100V", "Begode", 100.8f, 24, "MSUPER X 100V"),
        p("Begode Nikola Plus", "Begode", 100.8f, 24, "NIKOLA PLUS"),
        p("Begode RS", "Begode", 100.8f, 24, "RS C30", "RS C38"),
        p("Begode T4", "Begode", 100.8f, 24, "T4", "T4 PRO", empty = 72f),
        p("Begode Blitz", "Begode", 134.4f, 32, "BLITZ", packs = 2, empty = 99f),
        p("Begode EX30", "Begode", 134.4f, 32, "EX30", packs = 2, empty = 99.2f),
        p("Begode Extreme", "Begode", 134.4f, 32, "EXTREME", packs = 2, empty = 99f),
        p("Begode Master", "Begode", 134.4f, 32, "MASTER", empty = 104f),
        p("Begode Master Pro", "Begode", 134.4f, 32, "MASTER PRO", empty = 99.2f),
        p("Begode Master X", "Begode", 134.4f, 32, "MASTER X", empty = 99.2f),
        p("Begode X-Way 134V", "Begode", 134.4f, 32, "X-WAY 134", "XWAY-134", packs = 2, empty = 99.2f),
        p("Begode Blitz Pro", "Begode", 168f, 40, "BLITZ PRO", packs = 2, empty = 124f),
        p("Begode ET Max", "Begode", 168f, 40, "ET MAX", "ETMAX", packs = 2, empty = 124f),
        p("Begode Panther", "Begode", 168f, 40, "PANTHER", packs = 2, empty = 116f),
        p("Begode X-Way 168V", "Begode", 168f, 40, "X-WAY 168", "XWAY-168", packs = 2, empty = 124f),
        p("Begode RACE", "Begode", 210f, 50, "RACE", packs = 2, empty = 155f),

        // Extreme Bull (Gotway/Begode protocol)
        p("Extreme Bull Commander C30/C38", "Extreme Bull", 100.8f, 24, "COMMANDER C30", "COMMANDER C38"),
        p("Extreme Bull X-Men", "Extreme Bull", 100.8f, 24, "X-MEN C30", "X-MEN C38"),
        p("Extreme Bull Commander Mini", "Extreme Bull", 134.4f, 32, "COMMANDER MINI", packs = 2, empty = 100f),
        p("Extreme Bull Commander Pro", "Extreme Bull", 134.4f, 32, "COMMANDER PRO", packs = 2, empty = 97.6f),
        p("Extreme Bull Commander GT", "Extreme Bull", 134.4f, 32, "COMMANDER GT", empty = 97.6f),
        p("Extreme Bull Griffin", "Extreme Bull", 151.2f, 36, "GRIFFIN", packs = 2, empty = 111.6f),
        p("Extreme Bull Commander Max", "Extreme Bull", 168f, 40, "COMMANDER MAX", packs = 2, empty = 116f),
        p("Extreme Bull GT Pro", "Extreme Bull", 168f, 40, "GT PRO", empty = 124f),
        p("Extreme Bull Rocket", "Extreme Bull", 168f, 40, "ROCKET", empty = 120f),

        // LeaperKim / Veteran / NOSFET
        p("Veteran Sherman", "LeaperKim", 100.8f, 24, "SHERMAN", "ABRAMS", "SHERMAN S"),
        p("Veteran Patton", "LeaperKim", 126f, 30, "PATTON", "PATTON S"),
        p("Veteran Lynx", "LeaperKim", 151.2f, 36, "LYNX", "SHERMAN L", "LYNX S", packs = 2),
        p("Veteran Oryx", "LeaperKim", 176.4f, 42, "ORYX", packs = 2),
        p("NOSFET Aero", "NOSFET", 126f, 30, "NOSFET AERO", "AERO"),
        p("NOSFET Xeno", "NOSFET", 126f, 30, "NOSFET XENO", "XENO"),
        p("NOSFET Apex", "NOSFET", 151.2f, 36, "NOSFET APEX", "APEX"),
        p("NOSFET Aeon", "NOSFET", 151.2f, 36, "NOSFET AEON", "AEON"),

        // KingSong — max/full charge voltage, not nominal marketing voltage.
        p("KingSong 14D", "KingSong", 67.2f, 16, "KS-14D", "KS14D"),
        p("KingSong 16X", "KingSong", 84f, 20, "16X", "KS16X"),
        p("KingSong 18XL", "KingSong", 84f, 20, "18XL", "KS18XL"),
        p("KingSong S18", "KingSong", 84f, 20, "S18", "KS-S18"),
        p("KingSong S16 Pro", "KingSong", 84f, 20, "S16", "S16 PRO"),
        p("KingSong S19 Pro", "KingSong", 100.8f, 24, "S19", "S19 PRO"),
        p("KingSong S22 Pro", "KingSong", 126f, 30, "S22", "S22 PRO"),
        p("KingSong F18", "KingSong", 151.2f, 36, "F18", "F18P"),
        p("KingSong F22 Pro", "KingSong", 176.4f, 42, "F22", "F22 PRO"),

        // InMotion. Lorin decoder provides the series-cell count directly.
        p("InMotion V10F", "InMotion", 84f, 20, "V10F"),
        p("InMotion V11", "InMotion", 84f, 20, "V11", "V11Y"),
        p("InMotion V12", "InMotion", 100.8f, 24, "V12 HS", "V12 HT", "V12 PRO"),
        p("InMotion V13", "InMotion", 126f, 30, "V13", "V13 PRO", packs = 2),
        p("InMotion V14", "InMotion", 134.4f, 32, "V14", packs = 4),
        p("InMotion E20", "InMotion", 84f, 20, "E20"),
        p("InMotion V12S", "InMotion", 84f, 20, "V12S"),
        p("InMotion E25", "InMotion", 84f, 20, "E25", packs = 2),
        p("InMotion P6", "InMotion", 235.2f, 56, "P6"),
    )

    fun match(name: String?): WheelVoltageProfileV14? {
        val n = normalize(name.orEmpty())
        if (n.isEmpty()) return null
        return entries
            .mapNotNull { entry ->
                val tokens = (entry.aliases + entry.model).map(::normalize)
                val best = tokens.filter { it.isNotEmpty() && n.contains(it) }.maxOfOrNull { it.length } ?: 0
                entry.takeIf { best > 0 }?.let { it to best }
            }
            .maxByOrNull { it.second }
            ?.first
    }

    /**
     * Safe Begode fallback from an already-decoded true pack voltage.
     * Only infer a model when the voltage class is unambiguous. At present a pack
     * above every 176.4 V-class wheel can only be the 210 V RACE in our catalog.
     * Lower voltages intentionally return null because a discharged higher-voltage
     * wheel can overlap a lower-voltage class.
     */
    fun inferBegodeModelFromTrueVoltage(voltageV: Float): WheelVoltageProfileV14? =
        entries.firstOrNull { it.model == "Begode RACE" }
            ?.takeIf { voltageV > 177.5f && voltageV <= 215f }

    fun byFullVoltage(fullVoltageV: Float, tolerance: Float = 0.8f): List<WheelVoltageProfileV14> =
        entries.filter { kotlin.math.abs(it.fullVoltageV - fullVoltageV) <= tolerance }

    private fun normalize(value: String): String = value.uppercase().filter { it.isLetterOrDigit() }

    private fun p(
        model: String,
        brand: String,
        full: Float,
        series: Int,
        vararg aliases: String,
        packs: Int? = null,
        empty: Float? = null,
    ) = WheelVoltageProfileV14(model, brand, full, series, aliases.toList(), packs, empty)
}
