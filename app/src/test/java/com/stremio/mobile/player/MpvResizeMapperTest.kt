package com.stremio.mobile.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvResizeMapperTest {
    @Test
    fun `maps resize modes to mpv properties`() {
        val fit = MpvResizeMapper.properties(PlayerResizeMode.FIT)
        assertTrue(fit.keepAspect)
        assertEquals(0.0, fit.panscan, 0.0)

        val stretch = MpvResizeMapper.properties(PlayerResizeMode.STRETCH)
        assertFalse(stretch.keepAspect)
        assertEquals(0.0, stretch.panscan, 0.0)

        val zoom = MpvResizeMapper.properties(PlayerResizeMode.ZOOM)
        assertTrue(zoom.keepAspect)
        assertEquals(1.0, zoom.panscan, 0.0)
    }
}
