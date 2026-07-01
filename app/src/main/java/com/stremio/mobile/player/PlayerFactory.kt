package com.stremio.mobile.player

import android.content.Context

object PlayerFactory {
    fun create(
        context: Context,
        engine: PlayerEngine,
        settings: com.stremio.core.types.profile.Profile.Settings? = null
    ): Player {
        return when (engine) {
            PlayerEngine.EXO -> ExoStreamPlayer(context, settings)
            PlayerEngine.MPV -> MpvStreamPlayer(context, settings)
        }
    }
}

