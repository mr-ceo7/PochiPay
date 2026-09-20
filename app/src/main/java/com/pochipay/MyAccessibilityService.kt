package com.pochipay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.preference.PreferenceManager
import com.pochipay.data.AppDatabase
import com.pochipay.data.AutomationCommand
import com.pochipay.data.Repository
import com.pochipay.utils.NotificationHelper
import com.pochipay.utils.ToastHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

data class Selector(
        val text: String? = null,
        val textContains: String? = null,
        val contentDescription: String? = null,
        val resourceId: String? = null,
        val hint: String? = null,
        val index: Int = 0 // 0-based index for which matching node to return
)

class MyAccessibilityService : AccessibilityService() {

    companion object {
        var isConnected: Boolean = false
            private set
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private lateinit var repository: Repository
    private lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        repository = Repository(AppDatabase.getDatabase(this))
        notificationHelper = NotificationHelper(this)
        notificationHelper.createNotificationChannel()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        println("MyAccessibilityService connected")
        Timber.d("Accessibility service connected")
        scope.launch {
            AutomationEngine.commandFlow.collectLatest { command ->
                println("Command received in MyAccessibilityService: $command")
                when (command) {
                    is AutomationCommand.Tap -> {
                        performTap(command.selector)
                        println("Tappp command received: $command")
                    }
                    is AutomationCommand.createConversation -> {
                        convo(command.name)
                        println("convo command received: $command")
                    }
                    is AutomationCommand.TapCoordinate -> {
                        performTap(command.x, command.y)
                    }
                    is AutomationCommand.Swipe ->
                            performSwipe(
                                    command.startSelector,
                                    command.endSelector,
                                    command.duration
                            )
                    is AutomationCommand.Paste -> performPaste(command.selector, command.text)
                    is AutomationCommand.WaitFor ->
                            performWaitFor(command.selector, command.timeout)
                    is AutomationCommand.ExtractName -> {
                        val result =
                                if (command.all) {
                                    extractName(all = true)
                                } else {
                                    extractName(command.index)
                                }
                        command.result.complete(result)
                    }
                    is AutomationCommand.SendMessage -> {
                        Timber.d(
                                "SendMessage command received: conversation=${command.conversationId}, message=${command.message}"
                        )
                        // TODO: Implement accessibility logic to open conversation and send message
                        ToastHelper.showInfo(
                                this@MyAccessibilityService,
                                "Reply received: ${command.message}"
                        )
                    }
                    is AutomationCommand.PressBack -> {
                        val success = performGlobalAction(GLOBAL_ACTION_BACK)
                        Timber.d("Performed GLOBAL_ACTION_BACK: success=$success")
                    }
                    AutomationCommand.Stop -> {
                        Timber.d("Stopping automation")
                        job.cancel()
                        stopSelf()
                    }
                }
            }
        }
    }

    private suspend fun convo(name: String) {
        Timber.d("convo() called with name: $name")
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        // Heuristic: if the incoming 'name' already looks like a fully-formed transaction note
        // (contains a transaction code starting with TK and a Ksh amount), treat it as the final
        // note.
        val isFinalNote =
                name.startsWith("TK", ignoreCase = true) ||
                        (name.contains("Ksh") && name.contains("Confirmed"))

        val note =
                if (isFinalNote) {
                    Timber.d("Convo received final note directly")
                    name
                } else {
                    val noteTemplate = sharedPreferences.getString("note_template", "Note: {name}")
                    noteTemplate?.replace("{name}", name) ?: "Note: $name"
                }

        Timber.d("Prepared note: $note")

        // Ensure conversation is created before showing the notification
        try {
            val conversationId = repository.createConversation("MPESA", note, false)
            Timber.d("Conversation created with id: $conversationId, about to show notification")
            notificationHelper.showNotification("MPESA", note)
            Timber.d("Notification shown successfully")
        } catch (t: Throwable) {
            Timber.e(t, "Failed to create conversation before notifying")
        }
    }

    private suspend fun performWaitFor(selector: Selector, timeout: Long) {
        Timber.d("Waiting for node with selector: $selector (timeout: $timeout ms)")
        val node =
                withTimeoutOrNull(timeout) {
                    var foundNode: AccessibilityNodeInfo? = null
                    while (foundNode == null) {
                        foundNode = findNodeBySelector(selector)
                        if (foundNode == null) {
                            delay(500) // Poll every 500ms
                        }
                    }
                    foundNode
                }

        if (node != null) {
            Timber.d("Node found for selector: $selector")
        } else {
            Timber.e("Timed out waiting for node with selector: $selector")
        }
    }

    /**
     * Extract a single name by index (0 = first, 1 = second, etc.) Defaults to extracting the first
     * name if no index is provided. Returns null if no name is found at the given index.
     */
    private suspend fun extractName(index: Int = 0): String? {
        Timber.d("Extracting name at index: $index")
        val rootNode = rootInActiveWindow
        if (rootNode != null) {
            val names = mutableListOf<String>()
            findAllNameNodes(rootNode, names)

            if (index >= 0 && index < names.size) {
                val name = names[index]
                Timber.d("Name found at index $index: $name")
                ToastHelper.showSuccess(this, "Running...")
                val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
                val noteTemplate = sharedPreferences.getString("note_template", "Note: {name}")
                val note = noteTemplate?.replace("{name}", name) ?: "Note: $name"

                return name
            } else {
                Timber.d("Name not found at index $index (found ${names.size} total names)")
                ToastHelper.showError(this, "Name not found at index $index")
                return null
            }
        } else {
            Timber.e("Root node is null, cannot extract name")
            ToastHelper.showError(this, "Retrying...")
            return null
        }
    }

    /**
     * Extract all names found in the current screen. Returns a List<String> of all names found, or
     * an empty list if none found. Can be called with extractName(all = true) or extractNames()
     */
    private fun extractName(all: Boolean): List<String> {
        if (!all) {
            return emptyList()
        }

        Timber.d("Extracting all screen text")
        val rootNode = rootInActiveWindow
        if (rootNode != null) {
            val texts = mutableListOf<String>()
            findAllScreenNodesText(rootNode, texts)

            if (texts.isNotEmpty()) {
                Timber.d("Found ${texts.size} text nodes")
                return texts
            }
            return emptyList()
        } else {
            Timber.e("Root node is null, cannot extract screen text")
            return emptyList()
        }
    }

    private fun extractNames(): List<String> {
        return extractName(all = true)
    }

    internal fun findAllScreenNodesText(
            node: android.view.accessibility.AccessibilityNodeInfo?,
            texts: MutableList<String>
    ) {
        if (node == null) return
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { texts.add(it) }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { texts.add(it) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            findAllScreenNodesText(child, texts)
        }
    }

    /**
     * Recursively find all nodes containing name patterns and add them to the list. A name is a
     * string with 2+ words where each word starts with uppercase.
     */
    internal fun findAllNameNodes(
            node: android.view.accessibility.AccessibilityNodeInfo?,
            names: MutableList<String>
    ) {
        if (node == null) return

        // Check text for name pattern (e.g. 2+ words, each capitalized)
        node.text?.toString()?.trim()?.let { text ->
            val words = text.split(" ").filter { it.isNotBlank() }
            if (words.size >= 2 && words.all { it[0].isUpperCase() }) {
                names.add(text)
                return@let
            }
        }

        // Check content description for name pattern
        node.contentDescription?.toString()?.trim()?.let { description ->
            val words = description.split(" ").filter { it.isNotBlank() }
            if (words.size >= 2 && words.all { it[0].isUpperCase() }) {
                names.add(description)
                return@let
            }
        }

        // Recursively search children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            findAllNameNodes(child, names)
        }
    }

    /** Kept for backward compatibility. Finds the first name node (index 0). */
    internal fun findNameNode(node: android.view.accessibility.AccessibilityNodeInfo?): String? {
        if (node == null) return null
        val names = mutableListOf<String>()
        findAllNameNodes(node, names)
        return names.firstOrNull()
    }

    private fun findNodeBySelector(
            selector: Selector
    ): android.view.accessibility.AccessibilityNodeInfo? {
        Timber.d("Finding node with selector: $selector")
        val root = rootInActiveWindow ?: return null
        val foundNodes = mutableListOf<AccessibilityNodeInfo>()
        findNodes(root, selector, foundNodes)
        if (selector.index < foundNodes.size) {
            return foundNodes[selector.index]
        } else {
            Timber.e("Node not found for selector: $selector")
            Timber.e("found nodes areeee: ${root.text}")
            return null
        }
    }

    private fun findNode(
            node: android.view.accessibility.AccessibilityNodeInfo,
            selector: Selector
    ): android.view.accessibility.AccessibilityNodeInfo? {
        Timber.d(
                "Inspecting node: text='${node.text}', description='${node.contentDescription}', resourceId='${node.viewIdResourceName}', hint='${node.hintText}'"
        )
        if (selector.text != null && node.text?.toString().equals(selector.text, ignoreCase = true)
        ) {
            return node
        }
        if (selector.textContains != null && node.text?.toString()?.contains(selector.textContains, ignoreCase = true) == true) {
            return node
        }
        if (selector.contentDescription != null &&
                        node.contentDescription
                                ?.toString()
                                .equals(selector.contentDescription, ignoreCase = true)
        ) {
            return node
        }
        if (selector.resourceId != null && node.viewIdResourceName == selector.resourceId) {
            return node
        }
        if (selector.hint != null &&
                        node.hintText?.toString().equals(selector.hint, ignoreCase = true)
        ) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val result = findNode(child, selector)
                if (result != null) {
                    return result
                }
            }
        }
        return null
    }

    // Recursive function to find all nodes matching the selector
    private fun findNodes(
            node: AccessibilityNodeInfo,
            selector: Selector,
            foundNodes: MutableList<AccessibilityNodeInfo>
    ) {
        Timber.d(
                "Inspecting node: text='${node.text}', description='${node.contentDescription}', resourceId='${node.viewIdResourceName}', hint='${node.hintText}'"
        )

        val textMatches =
                selector.text != null &&
                        node.text?.toString().equals(selector.text, ignoreCase = true)
        val textContainsMatches =
                selector.textContains != null &&
                        node.text?.toString()?.contains(selector.textContains, ignoreCase = true) == true
        val descriptionMatches =
                selector.contentDescription != null &&
                        node.contentDescription
                                ?.toString()
                                .equals(selector.contentDescription, ignoreCase = true)
        val resourceIdMatches =
                selector.resourceId != null && node.viewIdResourceName == selector.resourceId
        val hintMatches =
                selector.hint != null &&
                        node.hintText?.toString().equals(selector.hint, ignoreCase = true)

        if (textMatches || textContainsMatches || descriptionMatches || resourceIdMatches || hintMatches) {
            foundNodes.add(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                findNodes(child, selector, foundNodes)
            }
        }
    }

    /**
     * Recursively search for the first editable/focusable input node (e.g., EditText). We consider
     * a node editable if AccessibilityNodeInfo.isEditable == true or its class name contains
     * "EditText".
     */
    private fun findFirstEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        try {
            val className = node.className?.toString() ?: ""
            if (node.isEditable || className.contains("EditText", ignoreCase = true)) {
                return node
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    val result = findFirstEditable(child)
                    if (result != null) return result
                }
            }
        } catch (t: Throwable) {
            // Defensive: accessibility node tree can throw if recycled; ignore and continue
            Timber.w(t, "Error while searching for editable node")
        }

        return null
    }

    /**
     * Attempts to focus an input field so the soft keyboard appears. If a selector is provided, we
     * search for that node; otherwise we pick the first editable node in the active window. Returns
     * true if a focus/click action was successfully performed.
     */
    fun focusInputField(selector: Selector? = null): Boolean {
        val root =
                rootInActiveWindow
                        ?: run {
                            Timber.e("Cannot focus input: rootInActiveWindow is null")
                            return false
                        }

        val target: AccessibilityNodeInfo? =
                if (selector != null) {
                    findNodeBySelector(selector)
                } else {
                    findFirstEditable(root)
                }

        if (target == null) {
            Timber.d("No candidate input node found to focus (selector=$selector)")
            return false
        }

        // Try to request focus on the node itself
        try {
            if (target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) {
                Timber.d("ACTION_FOCUS performed on node: $target")
                return true
            }

            // Sometimes click is required to open keyboard
            if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Timber.d(
                        "ACTION_CLICK performed on node: $target (should open keyboard if focusable)"
                )
                return true
            }

            // Walk up to parents to find a focusable/clickable container
            var parent = target.parent
            while (parent != null) {
                if (parent.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) {
                    Timber.d("ACTION_FOCUS performed on parent: $parent")
                    return true
                }
                if (parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    Timber.d("ACTION_CLICK performed on parent: $parent")
                    return true
                }
                parent = parent.parent
            }
        } catch (t: Throwable) {
            Timber.w(t, "Error while attempting to focus input node")
        }

        Timber.d("Failed to focus input node for selector=$selector")
        return false
    }

    private fun findNodeAt(node: AccessibilityNodeInfo, x: Int, y: Int): AccessibilityNodeInfo? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (!bounds.contains(x, y)) {
            return null
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val childNode = findNodeAt(child, x, y)
                if (childNode != null) {
                    return childNode
                }
            }
        }
        return node
    }

    private fun performTap(selector: Selector) {
        println("tappp perfom tap fun called")
        val node = findNodeBySelector(selector)
        if (node != null) {
            var clickableNode: AccessibilityNodeInfo? = node
            while (clickableNode != null && !clickableNode.isClickable) {
                clickableNode = clickableNode.parent
            }

            if (clickableNode != null &&
                            clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ) {
                Timber.d("Performed ACTION_CLICK on node: $clickableNode")
                return
            }

            // Fallback to gesture
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            val x = rect.centerX()
            val y = rect.centerY()
            performTap(x, y)
        } else {
            Timber.e("Could not find node for selector: $selector")
        }
    }

    private fun performTap(x: Int, y: Int) {
        val rootNode = rootInActiveWindow
        if (rootNode != null) {
            val nodeToClick = findNodeAt(rootNode, x, y)
            if (nodeToClick != null) {
                var clickableNode: AccessibilityNodeInfo? = nodeToClick
                while (clickableNode != null && !clickableNode.isClickable) {
                    clickableNode = clickableNode.parent
                }

                if (clickableNode != null &&
                                clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                ) {
                    Timber.d("Performed ACTION_CLICK on node at ($x, $y): $clickableNode")
                    return
                }
            }
        }

        // Fallback to gesture
        Timber.d("Falling back to gesture tap at ($x, $y)")
        val path = Path()
        path.moveTo(x.toFloat(), y.toFloat())
        val gesture =
                GestureDescription.Builder()
                        .addStroke(GestureDescription.StrokeDescription(path, 0, 1L))
                        .build()
        dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        Timber.d("Tap gesture completed successfully")
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        Timber.e("Tap gesture cancelled")
                    }
                },
                null
        )
    }

    private fun performPaste(selector: Selector, text: String) {
        val node = findNodeBySelector(selector)
        if (node != null) {
            val arguments = Bundle()
            arguments.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
            )
            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                Timber.d("Pasted text '$text' into node with selector: $selector")
            } else {
                Timber.e("Failed to paste text into node with selector: $selector")
            }
        } else {
            Timber.e("Could not find node for selector: $selector")
        }
    }

    private fun performSwipe(startSelector: Selector, endSelector: Selector, duration: Long) {
        val startNode = findNodeBySelector(startSelector)
        val endNode = findNodeBySelector(endSelector)

        if (startNode != null && endNode != null) {
            val startRect = android.graphics.Rect()
            startNode.getBoundsInScreen(startRect)
            val startX = startRect.centerX()
            val startY = startRect.centerY()

            val endRect = android.graphics.Rect()
            endNode.getBoundsInScreen(endRect)
            val endX = endRect.centerX()
            val endY = endRect.centerY()

            Timber.d("Performing swipe from ($startX, $startY) to ($endX, $endY) in $duration ms")
            val path = Path()
            path.moveTo(startX.toFloat(), startY.toFloat())
            path.lineTo(endX.toFloat(), endY.toFloat())
            val gesture =
                    GestureDescription.Builder()
                            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                            .build()
            dispatchGesture(
                    gesture,
                    object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            super.onCompleted(gestureDescription)
                            Timber.d("Swipe gesture completed successfully")
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            super.onCancelled(gestureDescription)
                            Timber.e("Swipe gesture cancelled")
                        }
                    },
                    null
            )
        } else {
            Timber.e(
                    "Could not find nodes for swipe selectors: start=$startSelector, end=$endSelector"
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        Timber.d("Accessibility event: $event")
    }

    override fun onInterrupt() {
        Timber.d("Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        isConnected = false
        job.cancel()
    }
}
