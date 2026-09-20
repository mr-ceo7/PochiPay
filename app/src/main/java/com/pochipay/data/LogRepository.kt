package com.pochipay.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val timestamp: Long,
    val level: Int,
    val tag: String?,
    val message: String
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

object LogRepository {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val logBuffer = ArrayDeque<LogEntry>()
    private const val MAX_LOGS = 1000

    @Synchronized
    fun addLog(priority: Int, tag: String?, message: String) {
        val entry = LogEntry(System.currentTimeMillis(), priority, tag, message)
        
        logBuffer.add(entry)
        if (logBuffer.size > MAX_LOGS) {
            logBuffer.removeFirst()
        }
        
        // Emitting a new list copy to trigger flow collectors
        _logs.value = logBuffer.toList()
    }

    @Synchronized
    fun clearLogs() {
        logBuffer.clear()
        _logs.value = emptyList()
    }
}
