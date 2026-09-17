package com.mrbashir.android

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ConnectionState { IDLE, WATCHING, PORTAL_DETECTED, LOGGING_IN, LOGGED_IN, ERROR }

data class LogLine(val timestamp: String, val message: String)

/**
 * Simple in-process pub/sub so MainActivity's Compose UI can show live status
 * without binding to the Service. The service is the only writer.
 */
object AppStatus {
    private val _state = MutableStateFlow(ConnectionState.IDLE)
    val state = _state.asStateFlow()

    private val _log = MutableStateFlow<List<LogLine>>(emptyList())
    val log = _log.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun update(state: ConnectionState) {
        _state.value = state
    }

    fun appendLog(message: String) {
        val line = LogLine(timeFormat.format(Date()), message)
        _log.value = (_log.value + line).takeLast(50) // keep it bounded
    }
}
