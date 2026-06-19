package com.stremio.mobile.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerEngineTest {
    @Test
    fun `profile values map to player engines`() {
        assertEquals(PlayerEngine.EXO, PlayerEngine.fromProfileValue(null))
        assertEquals(PlayerEngine.EXO, PlayerEngine.fromProfileValue("exo"))
        assertEquals(PlayerEngine.MPV, PlayerEngine.fromProfileValue("mpv"))
        assertEquals(PlayerEngine.EXO, PlayerEngine.fromProfileValue("vlc"))
    }
}
