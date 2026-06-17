package com.stremio.mobile.server

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class StubStreamingServerController : StreamingServerController {
    private val mutableState = MutableStateFlow<StreamingServerState>(StreamingServerState.Stopped)

    override val state: StateFlow<StreamingServerState> = mutableState

    override suspend fun start() {
        if (mutableState.value is StreamingServerState.Ready) {
            return
        }

        mutableState.value = StreamingServerState.Starting
        delay(450)
        mutableState.value = StreamingServerState.Ready("http://127.0.0.1:11470")
    }

    override suspend fun stop() {
        mutableState.value = StreamingServerState.Stopped
    }
}
