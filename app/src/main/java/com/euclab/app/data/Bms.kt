package com.euclab.app.data

data class BmsPack(
    val index: Int,
    val cells: List<Float> = List(36) { 0f },
    val currentA: Float? = null,
    val temperaturesC: List<Float> = emptyList(),
    /**
     * Expected cell count when the model/protocol tells us. Null means that EUC Lab
     * must not invent a pack layout; it simply renders every valid cell received.
     */
    val expectedCells: Int? = null,
) {
    val validCells: List<Float> get() = cells.filter { it > 1f }
    val minCellV: Float? get() = validCells.minOrNull()
    val maxCellV: Float? get() = validCells.maxOrNull()
    val avgCellV: Float? get() = validCells.takeIf { it.isNotEmpty() }?.average()?.toFloat()
    val deltaV: Float? get() = if (minCellV != null && maxCellV != null) maxCellV!! - minCellV!! else null
    val totalV: Float? get() = validCells.takeIf { it.isNotEmpty() }?.sum()
    val complete: Boolean
        get() = expectedCells?.let { it > 0 && validCells.size >= it }
            ?: validCells.isNotEmpty()
}

data class BmsSnapshot(
    val timestampMs: Long,
    val pack1: BmsPack,
    val pack2: BmsPack,
    val pack3: BmsPack? = null,
    val pack4: BmsPack? = null,
) {
    val packs: List<BmsPack>
        get() = listOfNotNull(pack1, pack2, pack3, pack4)
}
