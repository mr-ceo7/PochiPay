package com.pochipay.automation

import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import com.pochipay.AutomationEngine
import com.pochipay.Selector
import com.pochipay.StatusLogEngine
import com.pochipay.data.AutomationCommand
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

data class BusinessStkResult(
    val success: Boolean,
    val customerName: String? = null,
    val errorMessage: String? = null,
    val requestId: String? = null
)

object BusinessStkAutomation {

    private val automationMutex = Mutex()

    suspend fun execute(
        context: Context,
        phone: String,
        amount: Double,
        requestId: String? = null
    ): BusinessStkResult {
        // Concurrency Guard: Ensure only one STK flow runs at a time
        val acquired = withTimeoutOrNull(25_000L) {
            automationMutex.lock()
            true
        } ?: false

        if (!acquired) {
            val busyErr = "Automation engine is currently busy executing another transaction."
            StatusLogEngine.updateLog("Error: $busyErr\n")
            return BusinessStkResult(success = false, errorMessage = busyErr, requestId = requestId)
        }

        try {
            // Global Execution Timeout: Guarantee execution completes within 75s
            val result = withTimeoutOrNull(75_000L) {
                runAutomationPipeline(context, phone, amount, requestId)
            }

            return result ?: run {
                val timeoutErr = "STK Push automation timed out after 75s."
                StatusLogEngine.updateLog("Error: $timeoutErr\n")
                // Cleanup: attempt back presses to return to clean state
                try {
                    AutomationEngine.sendCommand(AutomationCommand.PressBack)
                    delay(300)
                    AutomationEngine.sendCommand(AutomationCommand.PressBack)
                } catch (ignored: Exception) {}
                BusinessStkResult(success = false, errorMessage = timeoutErr, requestId = requestId)
            }
        } finally {
            try {
                bringPochiPayToForeground(context)
                context.sendBroadcast(Intent("com.pochipay.ACTION_TASK_COMPLETED"))
            } catch (ignored: Exception) {}
            if (automationMutex.isLocked) {
                automationMutex.unlock()
            }
        }
    }

    private suspend fun runAutomationPipeline(
        context: Context,
        phone: String,
        amount: Double,
        requestId: String?
    ): BusinessStkResult {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val targetPackage = prefs.getString("target_package", "com.safaricom.mpesa.orgapp")
            ?.takeIf { it.isNotBlank() } ?: "com.safaricom.mpesa.orgapp"
        val mpesaPin = prefs.getString("mpesa_pin", "")?.trim() ?: ""

        StatusLogEngine.updateLog("Starting Business STK Push for $phone, Amount: KES $amount (Target: $targetPackage)\n")

        // 0. Safeguard: Suppress Developer Options alert
        suppressDeveloperOptions(context)

        // 1. Launch / Foreground Target App
        val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPackage)
        if (launchIntent == null) {
            val err = "Target app ($targetPackage) is not installed on this device."
            StatusLogEngine.updateLog("Error: $err\n")
            return BusinessStkResult(success = false, errorMessage = err, requestId = requestId)
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        context.startActivity(launchIntent)
        StatusLogEngine.updateLog("Launching $targetPackage...\n")

        delay(2200)

        // 2. Pre-flight Recovery: Normalize screen to Dashboard or PIN prompt
        normalizeToDashboardOrPin(context, targetPackage, mpesaPin)

        // 3. Handle PIN Screen if present
        if (isPinScreenVisible()) {
            val pinOk = handlePinEntry(mpesaPin)
            if (!pinOk) {
                val err = "Invalid or unconfigured M-PESA PIN in PochiPay settings."
                StatusLogEngine.updateLog("Error: $err\n")
                return BusinessStkResult(success = false, errorMessage = err, requestId = requestId)
            }
        }

        // 4. Locate and Tap "NEW SALE"
        StatusLogEngine.updateLog("Locating dashboard 'NEW SALE'...\n")
        var foundNewSale = waitForScreenContent(listOf("NEW SALE", "New Sale"), timeoutMs = 8000)

        // If not found, check if an interrupting dialog or lingering prompt list is active
        if (!foundNewSale) {
            dismissAnyInterruptingDialog()
            // Check if we are already in the "M-PESA PROMPT" view or a previous sub-screen
            val inSubScreen = checkScreenContains(listOf("M-PESA PROMPT", "M-PESA Prompt", "ENTER CUSTOMER PHONE NUMBER"), timeoutMs = 1500)
            if (inSubScreen) {
                StatusLogEngine.updateLog("Detected lingering sub-screen. Pressing back to return to dashboard...\n")
                AutomationEngine.sendCommand(AutomationCommand.PressBack)
                delay(1200)
                foundNewSale = waitForScreenContent(listOf("NEW SALE", "New Sale"), timeoutMs = 4000)
            }
        }

        if (!foundNewSale) {
            // Check for any blocking error on dashboard
            val alertMsg = checkForInterruptingDialogOrError()
            val err = alertMsg ?: "Failed to find 'NEW SALE' on dashboard."
            StatusLogEngine.updateLog("Error: $err\n")
            return BusinessStkResult(success = false, errorMessage = err, requestId = requestId)
        }

        StatusLogEngine.updateLog("Tapping 'NEW SALE'...\n")
        AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(textContains = "NEW SALE")))
        delay(1200)

        // Check if PIN was prompted after tapping NEW SALE (session re-auth)
        if (isPinScreenVisible()) {
            handlePinEntry(mpesaPin)
            delay(1000)
            AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(textContains = "NEW SALE")))
            delay(1200)
        }

        // 5. Bottom Sheet: Tap "M-PESA Prompt"
        StatusLogEngine.updateLog("Looking for 'M-PESA Prompt' option...\n")
        var foundPromptOption = waitForScreenContent(listOf("M-PESA Prompt", "M-PESA PROMPT"), timeoutMs = 6000)
        if (!foundPromptOption) {
            // Maybe bottom sheet didn't open; re-tap NEW SALE once
            StatusLogEngine.updateLog("Retrying tap on 'NEW SALE'...\n")
            AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(textContains = "NEW SALE")))
            delay(1500)
            foundPromptOption = waitForScreenContent(listOf("M-PESA Prompt", "M-PESA PROMPT"), timeoutMs = 4000)
        }

        if (!foundPromptOption) {
            val err = "Failed to find 'M-PESA Prompt' option in bottom sheet."
            StatusLogEngine.updateLog("Error: $err\n")
            return BusinessStkResult(success = false, errorMessage = err, requestId = requestId)
        }

        StatusLogEngine.updateLog("Tapping 'M-PESA Prompt'...\n")
        AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(textContains = "M-PESA Prompt")))
        delay(1500)

        // 6. Navigation to Phone Entry Screen
        val onPhoneEntryDirectly = checkScreenContains(listOf("ENTER CUSTOMER PHONE NUMBER", "CUSTOMER PHONE NUMBER"), timeoutMs = 2000)
        if (!onPhoneEntryDirectly) {
            val foundPromptList = waitForScreenContent(listOf("M-PESA PROMPT", "M-PESA Prompt"), timeoutMs = 5000)
            if (foundPromptList) {
                StatusLogEngine.updateLog("Tapping 'M-PESA PROMPT' initiation button...\n")
                AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(text = "M-PESA PROMPT", index = 1)))
                delay(1500)
            }
        }

        // 7. Customer Phone Number Entry Screen
        StatusLogEngine.updateLog("Looking for customer phone input screen...\n")
        val foundPhoneInput = waitForScreenContent(
            listOf("ENTER CUSTOMER PHONE NUMBER", "CUSTOMER PHONE NUMBER", "PHONE NUMBER"),
            timeoutMs = 7000
        )
        if (!foundPhoneInput) {
            val err = "Could not find customer phone number entry screen."
            StatusLogEngine.updateLog("Error: $err\n")
            return BusinessStkResult(success = false, errorMessage = err, requestId = requestId)
        }

        val cleanPhone = formatKenyanPhone(phone)
        StatusLogEngine.updateLog("Entering customer phone: $cleanPhone\n")
        for (digit in cleanPhone) {
            AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(text = digit.toString())))
            delay(160)
        }
        delay(500)

        StatusLogEngine.updateLog("Tapping 'CONTINUE' for phone...\n")
        AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(textContains = "CONTINUE")))
        delay(1500)

        // Check for immediate Phone Validation Error Dialog (e.g., "Invalid Phone", "Customer does not exist")
        val phoneErr = checkForInterruptingDialogOrError()
        if (phoneErr != null) {
            StatusLogEngine.updateLog("Phone Entry Rejected: $phoneErr\n")
            dismissAnyInterruptingDialog()
            return BusinessStkResult(success = false, errorMessage = phoneErr, requestId = requestId)
        }

        // 8. Amount Entry Screen
        StatusLogEngine.updateLog("Looking for amount entry screen...\n")
        val foundAmountScreen = waitForScreenContent(listOf("KSH.", "Ksh", "AMOUNT", "PHONE NUMBER"), timeoutMs = 7000)
        if (!foundAmountScreen) {
            val err = checkForInterruptingDialogOrError() ?: "Could not find amount entry screen."
            StatusLogEngine.updateLog("Error: $err\n")
            dismissAnyInterruptingDialog()
            return BusinessStkResult(success = false, errorMessage = err, requestId = requestId)
        }

        val amountIntStr = amount.toInt().toString()
        StatusLogEngine.updateLog("Entering amount KES $amountIntStr...\n")
        for (digit in amountIntStr) {
            AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(text = digit.toString())))
            delay(160)
        }
        delay(500)

        StatusLogEngine.updateLog("Tapping 'CONTINUE' for amount...\n")
        AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(textContains = "CONTINUE")))
        delay(2000)

        // Check for immediate Amount Validation Error Dialog (e.g. "Amount exceeds maximum", "Insufficient")
        val amtErr = checkForInterruptingDialogOrError()
        if (amtErr != null) {
            StatusLogEngine.updateLog("Amount Entry Rejected: $amtErr\n")
            dismissAnyInterruptingDialog()
            return BusinessStkResult(success = false, errorMessage = amtErr, requestId = requestId)
        }

        // 9. Confirmation & Name Extraction Screen ("CONFIRM")
        StatusLogEngine.updateLog("Looking for confirmation screen & resolving customer name...\n")
        val foundConfirmScreen = waitForScreenContent(listOf("CONFIRM", "Confirm", "CUSTOMER NAME"), timeoutMs = 9000)
        if (!foundConfirmScreen) {
            val err = checkForInterruptingDialogOrError() ?: "Confirmation screen did not appear in time."
            StatusLogEngine.updateLog("Error: $err\n")
            dismissAnyInterruptingDialog()
            return BusinessStkResult(success = false, errorMessage = err, requestId = requestId)
        }

        // Extract customer name
        val screenStrings = getScreenStrings()
        var extractedCustomerName: String? = null
        val custNameIndex = screenStrings.indexOfFirst { it.contains("CUSTOMER NAME", ignoreCase = true) }
        if (custNameIndex != -1 && custNameIndex + 1 < screenStrings.size) {
            extractedCustomerName = screenStrings[custNameIndex + 1].trim()
            StatusLogEngine.updateLog("Resolved Customer Name: $extractedCustomerName\n")
        } else {
            // Fallback: look for 2-3 capitalized words
            extractedCustomerName = screenStrings.firstOrNull { str ->
                val words = str.trim().split("\\s+".toRegex())
                words.size in 2..4 && words.all { w -> w.isNotEmpty() && w.all { ch -> ch.isUpperCase() || ch.isLetter() } } &&
                !str.contains("CONFIRM", ignoreCase = true) &&
                !str.contains("SALE", ignoreCase = true) &&
                !str.contains("PROMPT", ignoreCase = true)
            }
            if (extractedCustomerName != null) {
                StatusLogEngine.updateLog("Fallback Resolved Customer Name: $extractedCustomerName\n")
            } else {
                StatusLogEngine.updateLog("Could not parse exact customer name.\n")
            }
        }

        // Dispatch STK Push by tapping CONTINUE / CONFIRM
        StatusLogEngine.updateLog("Confirming STK Push dispatch...\n")
        AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(textContains = "CONTINUE")))
        delay(2000)

        // 10. Success / Completion Screen
        StatusLogEngine.updateLog("Waiting for prompt initiation confirmation...\n")
        val successFound = waitForScreenContent(
            listOf(
                "Prompt successfully initiated to the customer",
                "Prompt successfully initiated",
                "initiated to the customer",
                "Prompt sent"
            ),
            timeoutMs = 14000
        )

        if (successFound) {
            StatusLogEngine.updateLog("Prompt successfully initiated to customer phone!\n")
            AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(text = "DONE")))
            delay(800)

            return BusinessStkResult(
                success = true,
                customerName = extractedCustomerName,
                requestId = requestId
            )
        }

        // Check for error screen / failure on final confirmation
        val finalErr = checkForInterruptingDialogOrError() ?: run {
            val currStrings = getScreenStrings()
            currStrings.firstOrNull {
                it.contains("fail", ignoreCase = true) ||
                it.contains("error", ignoreCase = true) ||
                it.contains("retry", ignoreCase = true) ||
                it.contains("timed out", ignoreCase = true)
            } ?: "Prompt initiation did not confirm within 14 seconds."
        }

        StatusLogEngine.updateLog("Dispatch Error: $finalErr\n")
        dismissAnyInterruptingDialog()

        return BusinessStkResult(
            success = false,
            customerName = extractedCustomerName,
            errorMessage = finalErr,
            requestId = requestId
        )
    }

    private suspend fun normalizeToDashboardOrPin(context: Context, targetPackage: String, mpesaPin: String) {
        // Dismiss any unexpected dialogs that might be hanging from previous runs
        dismissAnyInterruptingDialog()

        // Check if we are already on Dashboard ("NEW SALE") or PIN screen
        val isHome = checkScreenContains(listOf("NEW SALE", "New Sale"), timeoutMs = 1500)
        val isPin = isPinScreenVisible()

        if (isHome || isPin) {
            return // Clean state
        }

        // Try pressing back up to 3 times to pop out of lingering sub-screens
        StatusLogEngine.updateLog("Normalizing screen state to dashboard...\n")
        for (i in 1..3) {
            if (checkScreenContains(listOf("NEW SALE", "New Sale"), timeoutMs = 800) || isPinScreenVisible()) {
                break
            }
            AutomationEngine.sendCommand(AutomationCommand.PressBack)
            delay(600)
            dismissAnyInterruptingDialog()
        }
    }

    private suspend fun isPinScreenVisible(): Boolean {
        return checkScreenContains(listOf("ENTER PIN", "OPERATOR ID", "CHANGE PIN"), timeoutMs = 1500)
    }

    private suspend fun handlePinEntry(mpesaPin: String): Boolean {
        if (mpesaPin.length != 4) {
            StatusLogEngine.updateLog("Error: 4-digit M-PESA Business PIN is required.\n")
            return false
        }
        StatusLogEngine.updateLog("Entering 4-digit PIN...\n")
        for (digit in mpesaPin) {
            AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(text = digit.toString())))
            delay(180)
        }
        delay(1800)
        return true
    }

    private suspend fun checkForInterruptingDialogOrError(): String? {
        val strings = getScreenStrings()
        val errorSignals = listOf(
            "not registered",
            "not an active",
            "invalid phone",
            "invalid number",
            "exceeds",
            "limit",
            "try again",
            "connection error",
            "timed out",
            "service unavailable",
            "developer options",
            "security alert",
            "cannot be completed",
            "failed",
            "cancelled",
            "insufficient"
        )

        for (sig in errorSignals) {
            val match = strings.firstOrNull { it.contains(sig, ignoreCase = true) }
            if (match != null) {
                return match
            }
        }
        return null
    }

    private suspend fun dismissAnyInterruptingDialog() {
        val dismissLabels = listOf("DISMISS", "NOT NOW", "CANCEL", "CLOSE", "LATER", "OK", "GOT IT", "DONE")
        for (label in dismissLabels) {
            val found = checkScreenContains(listOf(label), timeoutMs = 500)
            if (found) {
                Timber.d("Dismissing interrupting dialog via '$label'")
                AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(textContains = label)))
                delay(600)
                return
            }
        }
    }

    private fun suppressDeveloperOptions(context: Context) {
        try {
            android.provider.Settings.Global.putInt(
                context.contentResolver,
                android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            )
        } catch (t: Throwable) {
            Timber.d("Could not set DEVELOPMENT_SETTINGS_ENABLED: ${t.message}")
        }
    }

    private fun bringPochiPayToForeground(context: Context) {
        try {
            val returnIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            if (returnIntent != null) {
                context.startActivity(returnIntent)
            }
        } catch (e: Exception) {
            Timber.e(e, "Could not bring PochiPay to foreground")
        }
    }

    fun formatKenyanPhone(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.startsWith("254") && digits.length == 12 -> "0" + digits.substring(3)
            digits.startsWith("0") && digits.length == 10 -> digits
            digits.length == 9 -> "0$digits"
            else -> digits
        }
    }

    private suspend fun getScreenStrings(): List<String> {
        return try {
            val deferred = CompletableDeferred<Any?>()
            AutomationEngine.sendCommand(AutomationCommand.ExtractName(deferred, all = true))
            val res = deferred.await()
            if (res is List<*>) {
                res.mapNotNull { it?.toString() }
            } else if (res is String) {
                listOf(res)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun checkScreenContains(keywords: List<String>, timeoutMs: Long = 3000): Boolean {
        return waitForScreenContent(keywords, timeoutMs)
    }

    private suspend fun waitForScreenContent(keywords: List<String>, timeoutMs: Long = 8000): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val strings = getScreenStrings()
            for (kw in keywords) {
                if (strings.any { it.contains(kw, ignoreCase = true) }) {
                    return true
                }
            }
            delay(400)
        }
        return false
    }
}
