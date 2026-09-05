package com.mnivesh.callyn.managers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class HandsFreeConnectionState {
    DISABLED,
    CONNECTING,
    CONNECTED,
    UNAVAILABLE
}

object HandsFreeManager {
    private val _connectionState = MutableStateFlow(HandsFreeConnectionState.DISABLED)

    val connectionState: StateFlow<HandsFreeConnectionState> = _connectionState.asStateFlow()

    fun setConnectionState(state: HandsFreeConnectionState) {
        _connectionState.value = state
    }
}
