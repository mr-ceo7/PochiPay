package com.pochipay.data

import com.pochipay.Selector
import kotlinx.coroutines.CompletableDeferred

sealed class AutomationCommand {
    data class Tap(val selector: Selector) : AutomationCommand()
    data class createConversation(val name: String) : AutomationCommand()
    data class TapCoordinate(val x: Int, val y: Int) : AutomationCommand()
    data class Swipe(val startSelector: Selector, val endSelector: Selector, val duration: Long) :
            AutomationCommand()
    data class Paste(val selector: Selector, val text: String) : AutomationCommand()
    data class WaitFor(val selector: Selector, val timeout: Long) : AutomationCommand()
    data class ExtractName(
            val result: CompletableDeferred<Any?>, // Can be String? or List<String>
            val index: Int = -1, // If >= 0, extract single name at index; if < 0, extract all names
            val all: Boolean = false // If true, extract all names (alternative to index < 0)
    ) : AutomationCommand()
    data class SendMessage(
            val conversationId: String,
            val message: String,
            val isUser: Boolean = true
    ) : AutomationCommand()
    object PressBack : AutomationCommand()
    object Stop : AutomationCommand()
}
