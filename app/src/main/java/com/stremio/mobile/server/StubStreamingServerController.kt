package com.stremio.mobile.server

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class StubStreamingServerController : StreamingServerController {
    private val mutableState = MutableStateFlow<StreamingServerState>(StreamingServerState.Stopped)

    override val state: StateFlow<StreamingServerState> = mutableState

    override suspend fun start() {
        val current = mutableState.value
        if (current is StreamingServerState.Ready || current is StreamingServerState.Failed) {
            return
        }

        mutableState.value = StreamingServerState.Starting
        delay(450)
        mutableState.value = StreamingServerState.Failed("stream_server JNI library is unavailable")
    }

    override suspend fun stop() {
        mutableState.value = StreamingServerState.Stopped
    }
}
