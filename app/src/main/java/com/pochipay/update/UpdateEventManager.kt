package com.pochipay.update

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class UpdateDownloadEvent {
    data class DownloadCompleted(val downloadId: Long, val status: Int) : UpdateDownloadEvent()
    // Could add DownloadFailed, DownloadProgress here too if needed
}

object UpdateEventManager {
    private val _events = MutableSharedFlow<UpdateDownloadEvent>()
    val events: SharedFlow<UpdateDownloadEvent> = _events.asSharedFlow()

    suspend fun emitEvent(event: UpdateDownloadEvent) {
        _events.emit(event)
    }
}
