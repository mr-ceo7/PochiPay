package com.pochipay

import com.pochipay.data.AutomationCommand
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object AutomationEngine {
    private val _commandFlow = MutableSharedFlow<AutomationCommand>()
    val commandFlow = _commandFlow.asSharedFlow()

    suspend fun sendCommand(command: AutomationCommand) {
        _commandFlow.emit(command)
    }
}
