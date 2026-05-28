package dev.nicospz.pogomap.domain

import com.google.common.geometry.S2LatLng
import com.google.common.geometry.S2LatLngRect
import com.google.common.geometry.S2RegionCoverer
import java.util.ArrayList

data class S2CoveringResult(
    val level: Int,
    val cellIds: List<String>,
    val blockedByMaxCells: Boolean,
)

class S2ViewportCoverer(
    private val maxCells: Int = 128,
) {
    fun levelForZoom(zoom: Float): Int = when {
        zoom < 6f -> 4
        zoom < 7f -> 5
        zoom < 8f -> 6
        zoom < 9f -> 7
        zoom < 10f -> 8
        zoom < 11f -> 9
        zoom < 12f -> 10
        zoom < 12.75f -> 11
        zoom < 13.5f -> 12
        zoom < 14.25f -> 13
        zoom < 16f -> 14
        zoom < 17.5f -> 15
        else -> 16
    }

    fun cover(bounds: ViewportBounds, zoom: Float): S2CoveringResult {
        val level = levelForZoom(zoom)
        val rect = S2LatLngRect.fromPointPair(
            S2LatLng.fromDegrees(bounds.south, bounds.west),
            S2LatLng.fromDegrees(bounds.north, bounds.east),
        )
        val coverer = S2RegionCoverer.builder()
            .setMinLevel(level)
            .setMaxLevel(level)
            .setMaxCells(maxCells + 1)
            .build()
        val cells = ArrayList<com.google.common.geometry.S2CellId>()
        coverer.getCovering(rect, cells)
        val ids = cells
            .map { java.lang.Long.toUnsignedString(it.id()) }
            .distinct()
            .sorted()
        return S2CoveringResult(
            level = level,
            cellIds = if (ids.size > maxCells) emptyList() else ids,
            blockedByMaxCells = ids.size > maxCells,
        )
    }
}
