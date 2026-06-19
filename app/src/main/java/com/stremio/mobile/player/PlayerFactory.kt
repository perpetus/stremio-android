package com.stremio.mobile.player

import android.content.Context

object PlayerFactory {
    fun create(context: Context, engine: PlayerEngine): Player {
        return when (engine) {
            PlayerEngine.EXO -> ExoStreamPlayer(context)
            PlayerEngine.MPV -> MpvStreamPlayer(context)
        }
    }
}

