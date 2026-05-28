package dev.nicospz.pogomap.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class S2ViewportCovererTest {
    private val coverer = S2ViewportCoverer()

    @Test
    fun `tokyo viewport generates decimal cell ids`() {
        val result = coverer.cover(
            bounds = ViewportBounds(
                south = 35.66,
                west = 139.72,
                north = 35.71,
                east = 139.78,
            ),
            zoom = 13f,
        )

        assertFalse(result.blockedByMaxCells)
        assertTrue(result.cellIds.isNotEmpty())
        assertTrue(result.cellIds.all { id -> id.all(Char::isDigit) })
    }

    @Test
    fun `zoom mapping selects expected S2 level`() {
        assertEquals(4, coverer.levelForZoom(5.99f))
        assertEquals(5, coverer.levelForZoom(6f))
        assertEquals(6, coverer.levelForZoom(7f))
        assertEquals(10, coverer.levelForZoom(11.99f))
        assertEquals(11, coverer.levelForZoom(12f))
        assertEquals(13, coverer.levelForZoom(13.99f))
        assertEquals(14, coverer.levelForZoom(14.25f))
        assertEquals(14, coverer.levelForZoom(15.75f))
        assertEquals(15, coverer.levelForZoom(16f))
        assertEquals(16, coverer.levelForZoom(17.5f))
    }

    @Test
    fun `country level viewport uses coarse level 4 cells`() {
        val result = coverer.cover(
            bounds = ViewportBounds(
                south = 49.8,
                west = -8.7,
                north = 60.9,
                east = 2.0,
            ),
            zoom = 5f,
        )

        assertEquals(4, result.level)
        assertFalse(result.blockedByMaxCells)
        assertTrue(result.cellIds.isNotEmpty())
    }

    @Test
    fun `city level viewport uses mid level cells`() {
        val result = coverer.cover(
            bounds = ViewportBounds(
                south = 51.28,
                west = -0.52,
                north = 51.70,
                east = 0.28,
            ),
            zoom = 10f,
        )

        assertEquals(9, result.level)
        assertFalse(result.blockedByMaxCells)
        assertTrue(result.cellIds.isNotEmpty())
    }

    @Test
    fun `street level 16 viewport stays within default guard`() {
        val result = coverer.cover(
            bounds = ViewportBounds(
                south = 35.648,
                west = 139.682,
                north = 35.656,
                east = 139.694,
            ),
            zoom = 17.5f,
        )

        assertEquals(16, result.level)
        assertFalse(result.blockedByMaxCells)
        assertTrue(result.cellIds.isNotEmpty())
    }

    @Test
    fun `max cell guard blocks large level 13 viewport`() {
        val result = coverer.cover(
            bounds = ViewportBounds(
                south = 34.0,
                west = 138.0,
                north = 36.5,
                east = 141.0,
            ),
            zoom = 13f,
        )

        assertTrue(result.blockedByMaxCells)
        assertTrue(result.cellIds.isEmpty())
    }
}
