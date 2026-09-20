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
import timber.log.Timber

data class BusinessStkResult(
    val success: Boolean,
    val customerName: String? = null,
    val errorMessage: String? = null,
    val requestId: String? = null
)

object BusinessStkAutomation {

    suspend fun execute(
        context: Context,
        phone: String,
        amount: Double,
        requestId: String? = null
    ): BusinessStkResult {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val targetPackage = prefs.getString("target_package", "com.safaricom.mpesa.orgapp")
            ?.takeIf { it.isNotBlank() } ?: "com.safaricom.mpesa.orgapp"
        val mpesaPin = prefs.getString("mpesa_pin", "") ?: ""

        StatusLogEngine.updateLog("Starting Business STK Push for $phone, Amount: KES $amount (Target: $targetPackage)\n")

        // 1. Launch M-PESA for Business App
        val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPackage)
        if (launchIntent == null) {
            val err = "Target app ($targetPackage) is not installed on this device."
            StatusLogEngine.updateLog("Error: $err\n")
            return BusinessStkResult(success = false, errorMessage = err, requestId = requestId)
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        context.startActivity(launchIntent)
        StatusLogEngine.updateLog("Launching $targetPackage...\n")

        // Wait for app window to settle
        delay(2500)

        // 2. Check for PIN Unlock Screen ("ENTER PIN:" or "OPERATOR ID:")
        val pinScreenFound = checkScreenContains(listOf("ENTER PIN", "OPERATOR ID", "CHANGE PIN"), timeoutMs = 3500)
        if (pinScreenFound) {
            StatusLogEngine.updateLog("Detected PIN unlock screen.\n")
            if (mpesaPin.length != 4) {
                val err = "4-digit M-PESA Business PIN not configured in PochiPay settings."
                StatusLogEngine.updateLog("Error: $err\n")
                return BusinessStkResult(success = false, errorMessage = err, requestId = requestId)
            }
            StatusLogEngine.updateLog("Entering 4-digit PIN...\n")
            for (digit in mpesaPin) {
                AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(text = digit.toString())))
                delay(200)
            }
            delay(2000)
        } else {
            StatusLogEngine.updateLog("App already unlocked or no PIN prompt visible.\n")
        }

        // 3. Look for Home Dashboard with "NEW SALE"
        StatusLogEngine.updateLog("Looking for dashboard 'NEW SALE'...\n")
        val foundNewSale = waitForScreenContent(listOf("NEW SALE"), timeoutMs = 8000)
        if (!foundNewSale) {
            val err = "Failed to find 'NEW SALE' on dashboard."
            StatusLogEngine.updateLog("Error: $err\n")
            return BusinessStkResult(success = false, errorMessage = err, requestId = requestId)
        }
        StatusLogEngine.updateLog("Tapping 'NEW SALE'...\n")
        AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(textContains = "NEW SALE")))
        delay(1200)

        // 4. Bottom sheet with "M-PESA Prompt" and "Pay by QR"
        StatusLogEngine.updateLog("Looking for 'M-PESA Prompt' option...\n")
        val foundPromptOption = waitForScreenContent(listOf("M-PESA Prompt"), timeoutMs = 6000)
        if (!foundPromptOption) {
            val err = "Failed to find 'M-PESA Prompt' in sale options."
            StatusLogEngine.updateLog("Error: $err\n")
            return BusinessStkResult(success = false, errorMessage = err, requestId = requestId)
        }
        StatusLogEngine.updateLog("Tapping 'M-PESA Prompt'...\n")
        AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(textContains = "M-PESA Prompt")))
        delay(1500)

        // 5. Prompt list screen or direct phone entry
        val onPhoneEntryDirectly = checkScreenContains(listOf("ENTER CUSTOMER PHONE NUMBER"), timeoutMs = 2000)
        if (!onPhoneEntryDirectly) {
            val foundPromptList = waitForScreenContent(listOf("M-PESA PROMPT"), timeoutMs = 6000)
            if (foundPromptList) {
                StatusLogEngine.updateLog("Tapping 'M-PESA PROMPT' initiation button...\n")
                AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(text = "M-PESA PROMPT", index = 1)))
                delay(1500)
            }
        }

        // 6. Customer Phone Number Entry Screen ("ENTER CUSTOMER PHONE NUMBER")
        StatusLogEngine.updateLog("Looking for customer phone number input...\n")
        val foundPhoneInput = waitForScreenContent(listOf("ENTER CUSTOMER PHONE NUMBER"), timeoutMs = 7000)
        if (!foundPhoneInput) {
            val err = "Could not find customer phone number entry screen."
            StatusLogEngine.updateLog("Error: $err\n")
            return BusinessStkResult(success = false, errorMessage = err, requestId = requestId)
        }

        val cleanPhone = formatKenyanPhone(phone)
        StatusLogEngine.updateLog("Entering customer phone: $cleanPhone\n")

        // Try paste first
        AutomationEngine.sendCommand(
            AutomationCommand.Paste(
                Selector(hint = "ENTER CUSTOMER PHONE NUMBER", textContains = "CUSTOMER PHONE NUMBER"),
                cleanPhone
            )
        )
        delay(500)

        // Also enter via numeric keypad if needed
        for (digit in cleanPhone) {
            AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(text = digit.toString())))
            delay(150)
        }
        delay(600)

        StatusLogEngine.updateLog("Tapping 'CONTINUE' for phone number...\n")
        AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(textContains = "CONTINUE")))
        delay(1500)

        // 7. Amount Entry Screen ("KSH. 0" or "PHONE NUMBER")
        StatusLogEngine.updateLog("Looking for amount entry screen...\n")
        val foundAmountScreen = waitForScreenContent(listOf("KSH.", "PHONE NUMBER"), timeoutMs = 6000)
        if (!foundAmountScreen) {
            val err = "Could not find amount entry screen."
            StatusLogEngine.updateLog("Error: $err\n")
            return BusinessStkResult(success = false, errorMessage = err, requestId = requestId)
        }

        val amountIntStr = amount.toInt().toString()
        StatusLogEngine.updateLog("Entering amount KES $amountIntStr...\n")
        for (digit in amountIntStr) {
            AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(text = digit.toString())))
            delay(150)
        }
        delay(600)

        StatusLogEngine.updateLog("Tapping 'CONTINUE' for amount...\n")
        AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(textContains = "CONTINUE")))
        delay(2000)

        // 8. Confirmation & Name Extraction Screen ("CONFIRM")
        StatusLogEngine.updateLog("Looking for confirmation screen & resolving customer name...\n")
        val foundConfirmScreen = waitForScreenContent(listOf("CONFIRM"), timeoutMs = 8000)
        if (!foundConfirmScreen) {
            val err = "Confirmation screen did not appear."
            StatusLogEngine.updateLog("Error: $err\n")
            return BusinessStkResult(success = false, errorMessage = err, requestId = requestId)
        }

        // Extract customer name
        val screenStrings = getScreenStrings()
        var extractedCustomerName: String? = null
        val custNameIndex = screenStrings.indexOfFirst { it.contains("CUSTOMER NAME", ignoreCase = true) }
        if (custNameIndex != -1 && custNameIndex + 1 < screenStrings.size) {
            extractedCustomerName = screenStrings[custNameIndex + 1]
            StatusLogEngine.updateLog("Resolved Customer Name: $extractedCustomerName\n")
        } else {
            StatusLogEngine.updateLog("Could not parse exact customer name from screen nodes.\n")
        }

        // No PIN needed to trigger STK push! Tap green CONTINUE button
        StatusLogEngine.updateLog("Confirming STK Push dispatch...\n")
        AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(textContains = "CONTINUE")))
        delay(2000)

        // 9. Success / Completion Screen ("Prompt successfully initiated to the customer.")
        StatusLogEngine.updateLog("Waiting for prompt initiation confirmation...\n")
        val successFound = waitForScreenContent(listOf("Prompt successfully initiated to the customer"), timeoutMs = 12000)
        if (successFound) {
            StatusLogEngine.updateLog("Prompt successfully initiated to customer phone!\n")
            AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(text = "DONE")))
            delay(1000)

            bringPochiPayToForeground(context)

            return BusinessStkResult(
                success = true,
                customerName = extractedCustomerName,
                requestId = requestId
            )
        }

        // Check if an error screen appeared (e.g. Retry, Failed, Insufficient)
        val currentStrings = getScreenStrings()
        val errorItem = currentStrings.firstOrNull {
            it.contains("fail", ignoreCase = true) ||
            it.contains("error", ignoreCase = true) ||
            it.contains("retry", ignoreCase = true)
        }
        val err = errorItem ?: "Prompt initiation did not confirm in time."
        StatusLogEngine.updateLog("Error: $err\n")
        bringPochiPayToForeground(context)

        return BusinessStkResult(
            success = false,
            customerName = extractedCustomerName,
            errorMessage = err,
            requestId = requestId
        )
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

    private fun formatKenyanPhone(raw: String): String {
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
            delay(500)
        }
        return false
    }
}
