package com.euclab.app.data

data class BmsPack(
    val index: Int,
    val cells: List<Float> = List(36) { 0f },
    val currentA: Float? = null,
    val temperaturesC: List<Float> = emptyList(),
) {
    val validCells: List<Float> get() = cells.filter { it > 1f }
    val minCellV: Float? get() = validCells.minOrNull()
    val maxCellV: Float? get() = validCells.maxOrNull()
    val avgCellV: Float? get() = validCells.takeIf { it.isNotEmpty() }?.average()?.toFloat()
    val deltaV: Float? get() = if (minCellV != null && maxCellV != null) maxCellV!! - minCellV!! else null
    val totalV: Float? get() = validCells.takeIf { it.isNotEmpty() }?.sum()
    val complete: Boolean get() = validCells.size >= 36
}

data class BmsSnapshot(
    val timestampMs: Long,
    val pack1: BmsPack,
    val pack2: BmsPack,
)
