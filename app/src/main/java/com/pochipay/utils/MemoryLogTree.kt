package com.pochipay.utils

import com.pochipay.data.LogRepository
import timber.log.Timber

class MemoryLogTree : Timber.DebugTree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Log to standard output via DebugTree
        super.log(priority, tag, message, t)
        
        // Also capture to our repository
        val fullMessage = if (t != null) {
            "$message\n${t.stackTraceToString()}"
        } else {
            message
        }
        
        LogRepository.addLog(priority, tag, fullMessage)
    }
}
