package com.pochipay

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object StatusLogEngine {
    private val _logFlow = MutableSharedFlow<String>(replay = 50)
    val logFlow = _logFlow.asSharedFlow()
    private val buffer = StringBuilder()

    fun getLogs(): String = synchronized(buffer) { buffer.toString() }

    suspend fun updateLog(log: String) {
        synchronized(buffer) {
            buffer.append(log)
            if (buffer.length > 20000) {
                buffer.delete(0, 5000)
            }
        }
        _logFlow.emit(log)
    }

    fun clear() {
        synchronized(buffer) {
            buffer.clear()
        }
    }
}
