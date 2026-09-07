package com.euclab.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object WheelRepository {
    private val _telemetry = MutableStateFlow<Telemetry?>(null)
    val telemetry: StateFlow<Telemetry?> = _telemetry.asStateFlow()

    private val _bms = MutableStateFlow<BmsSnapshot?>(null)
    val bms: StateFlow<BmsSnapshot?> = _bms.asStateFlow()

    private val _linkState = MutableStateFlow(LinkState.IDLE)
    val linkState: StateFlow<LinkState> = _linkState.asStateFlow()

    private val _linkMessage = MutableStateFlow("Not connected")
    val linkMessage: StateFlow<String> = _linkMessage.asStateFlow()

    private val _rawPacket = MutableStateFlow("—")
    val rawPacket: StateFlow<String> = _rawPacket.asStateFlow()

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private val _lastLogPath = MutableStateFlow<String?>(null)
    val lastLogPath: StateFlow<String?> = _lastLogPath.asStateFlow()

    fun publishTelemetry(value: Telemetry) {
        _telemetry.value = value
    }

    fun clearTelemetry() {
        _telemetry.value = null
    }

    fun publishBms(value: BmsSnapshot) {
        _bms.value = value
    }

    fun clearBms() {
        _bms.value = null
    }

    fun setLink(state: LinkState, message: String) {
        _linkState.value = state
        _linkMessage.value = message
    }

    fun setRawPacket(hex: String) {
        _rawPacket.value = hex
    }

    fun setRecording(value: Boolean) {
        _recording.value = value
    }

    fun setLastLogPath(path: String?) {
        _lastLogPath.value = path
    }
}
