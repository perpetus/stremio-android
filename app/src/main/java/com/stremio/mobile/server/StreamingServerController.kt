package com.stremio.mobile.server

import kotlinx.coroutines.flow.StateFlow

sealed interface StreamingServerState {
    data object Stopped : StreamingServerState
    data object Starting : StreamingServerState
    data class Ready(val baseUrl: String) : StreamingServerState
    data class Failed(val message: String) : StreamingServerState
}

interface StreamingServerController {
    val state: StateFlow<StreamingServerState>

    suspend fun start()

    suspend fun stop()
}
