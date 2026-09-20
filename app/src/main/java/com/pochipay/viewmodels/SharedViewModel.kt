package com.pochipay.viewmodels

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pochipay.AutomationEngine
import com.pochipay.PochiPayApplication
import com.pochipay.Selector
import com.pochipay.StatusLogEngine
import com.pochipay.data.AutomationCommand
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

sealed class AutomationEvent {
    data class ShowToast(val message: String) : AutomationEvent()
    object StartOverlayService : AutomationEvent()
    data class LaunchApp(val packageName: String) : AutomationEvent()
    data class CreateConversation(val message: String) : AutomationEvent()
    data class ShowError(val message: String) : AutomationEvent()
    object ReturnToApp : AutomationEvent()
}

class SharedViewModel(application: Application) : AndroidViewModel(application) {

    private val _events = MutableSharedFlow<AutomationEvent>()
    val events = _events.asSharedFlow()

    // Properties to store the last automation run
    private var lastPaymentMethod: String? = null
    private var lastNumber: String? = null
    private var lastAmount: Int? = null

    // --- New State Management ---
    private val _uiState = MutableStateFlow<AutomationUiState>(AutomationUiState.InitialScreen)
    val uiState: StateFlow<AutomationUiState> = _uiState.asStateFlow()

    fun onMpesaClicked() {
        _uiState.value = AutomationUiState.MpesaMainMenu
    }

    fun onLipaNaMpesaClicked() {
        _uiState.value = AutomationUiState.LipaNaMpesaMenu
    }

    fun onSendMoneyClicked() {
        _uiState.value = AutomationUiState.EnterSendMoneyPhone
    }

    fun onBuyGoodsClicked() {
        _uiState.value = AutomationUiState.EnterTillNumber
    }

    fun onPochiClicked() {
        _uiState.value = AutomationUiState.EnterPochiPhone
    }

    fun onPhoneNumberEntered(number: String) {
        if (number.isBlank()) {
            viewModelScope.launch { _events.emit(AutomationEvent.ShowError("Phone number cannot be empty")) }
            return
        }
        _uiState.value = AutomationUiState.EnterSendMoneyAmount(number)
    }

    fun onTillNumberEntered(number: String) {
        if (number.isBlank()) {
            viewModelScope.launch { _events.emit(AutomationEvent.ShowError("Till number cannot be empty")) }
            return
        }
        _uiState.value = AutomationUiState.EnterTillAmount(number)
    }

    fun onPochiPhoneNumberEntered(number: String) {
        if (number.isBlank()) {
            viewModelScope.launch { _events.emit(AutomationEvent.ShowError("Phone number cannot be empty")) }
            return
        }
        _uiState.value = AutomationUiState.EnterPochiAmount(number)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun initiateTransaction(amount: Int, isDummy: Boolean = true) {
        val currentState = _uiState.value
        val paymentMethod: String
        val number: String

        when (currentState) {
            is AutomationUiState.EnterSendMoneyAmount -> {
                paymentMethod = "Send money"
                number = currentState.phoneNumber
            }
            is AutomationUiState.EnterTillAmount -> {
                paymentMethod = "BygoodsTill"
                number = currentState.tillNumber
            }
            is AutomationUiState.EnterPochiAmount -> {
                paymentMethod = "Pochi la biashara"
                number = currentState.phoneNumber
            }
            else -> {
                viewModelScope.launch { _events.emit(AutomationEvent.ShowError("Invalid state for transaction")) }
                return
            }
        }
        startAutomation(paymentMethod, number, amount, isDummy)
    }


    fun onCancel() {
        _uiState.value = when (_uiState.value) {
            is AutomationUiState.MpesaMainMenu -> AutomationUiState.InitialScreen
            is AutomationUiState.LipaNaMpesaMenu -> AutomationUiState.MpesaMainMenu
            is AutomationUiState.EnterSendMoneyPhone -> AutomationUiState.MpesaMainMenu
            is AutomationUiState.EnterSendMoneyAmount -> AutomationUiState.EnterSendMoneyPhone
            is AutomationUiState.EnterTillNumber -> AutomationUiState.LipaNaMpesaMenu
            is AutomationUiState.EnterTillAmount -> AutomationUiState.EnterTillNumber
            is AutomationUiState.EnterPochiPhone -> AutomationUiState.LipaNaMpesaMenu
            is AutomationUiState.EnterPochiAmount -> AutomationUiState.EnterPochiPhone
            else -> AutomationUiState.InitialScreen // Default fallback
        }
    }


    // --- Existing Automation Logic ---

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startAutomation(paymentMethod: String, number: String?, amount: Int?, isDummy: Boolean) {
        // Store the parameters for a potential re-run
        this.lastPaymentMethod = paymentMethod
        this.lastNumber = number
        this.lastAmount = amount

        viewModelScope.launch {
            if (number == null) {
                _events.emit(AutomationEvent.ShowError("Please enter a valid number"))
                return@launch
            }
            if (amount == null || amount <= 0) {
                _events.emit(AutomationEvent.ShowError("Please enter a valid positive amount"))
                return@launch
            }

            StatusLogEngine.updateLog("Initiating $paymentMethod with note: $number amount: $amount\n")
            _events.emit(AutomationEvent.ShowToast("Initiating automation..."))
            _events.emit(AutomationEvent.StartOverlayService)

            val sharedPreferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(getApplication())
            val targetPackage = sharedPreferences.getString("target_package", "")

            if (targetPackage.isNullOrBlank()) {
                StatusLogEngine.updateLog("Target package not set in settings.\n")
                return@launch
            }

            try {
                getApplication<Application>().packageManager.getPackageInfo(targetPackage, 0)
                StatusLogEngine.updateLog("Target app is installed.\n")
            } catch (e: PackageManager.NameNotFoundException) {
                StatusLogEngine.updateLog("Target app is not installed $e.\n")
                return@launch
            }

            _events.emit(AutomationEvent.LaunchApp(targetPackage))
            StatusLogEngine.updateLog("Launch intent sent. Starting app.\n")
            println("Launch intent sent. Starting app.")

            val result = when (paymentMethod) {
                "Send money" -> sendmoney(number, isDummy, amount)
                "Pochi la biashara" -> pochi(number, isDummy, amount)
                "BygoodsTill" -> bygoods(number, isDummy, amount)
                else -> {
                    StatusLogEngine.updateLog("Unknown payment method: $paymentMethod\n")
                    null
                }
            }

            if (result != null) {
                StatusLogEngine.updateLog("Creating conversation with name: $result\n")
                val finalNote = assembleTemplate(paymentMethod, amount, number, result.toString())
                StatusLogEngine.updateLog("Final note: $finalNote\n")
                _events.emit(AutomationEvent.CreateConversation(finalNote))
                // Return to the app after automation is done
                kotlinx.coroutines.delay(6000)
                _events.emit(AutomationEvent.ReturnToApp)
                StatusLogEngine.updateLog("Returning to app.\n")
            }


        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun rerunLastAutomation() {
        if (lastPaymentMethod != null && lastNumber != null && lastAmount != null) {
            // Re-run the last automation, but this time with dummy set to false
            startAutomation(lastPaymentMethod!!, lastNumber, lastAmount, isDummy = false)
        } else {
            viewModelScope.launch {
                _events.emit(AutomationEvent.ShowError("No previous automation run to push."))
            }
        }
    }

    private suspend fun bygoods(number: String, dummy: Boolean, amount: Int?): Any? {
        StatusLogEngine.updateLog("Waiting for app to launch and show 'Send' button...\n")
        ifScreenContentIs("My Usage", all = true)
        AutomationEngine.sendCommand(AutomationCommand.Tap(
            Selector(
                text = "Lipa na M-PESA",
                index = 0
            )
        ))
        StatusLogEngine.updateLog("Waiting for phone number input field...\n")
        ifScreenContentIs("Till Number", all = true)
        AutomationEngine.sendCommand(AutomationCommand.Paste(Selector(text = "Till Number"), number))
        val pasteAmount = amount?.toString() ?: "1"
        AutomationEngine.sendCommand(AutomationCommand.Paste(Selector(text = "Amount"), pasteAmount))
        StatusLogEngine.updateLog("Waiting for confirm button...\n")
        kotlinx.coroutines.delay(1000)
        AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(text = "CONTINUE")))
        ifScreenContentIs("Transaction Cost:", all = true)
        StatusLogEngine.updateLog("Sending extract name command...\n")
        if (dummy) {
            val deferredResult = CompletableDeferred<Any?>()
            AutomationEngine.sendCommand(AutomationCommand.ExtractName(deferredResult, index = 1))
            val name = deferredResult.await()
            Timber.d("Extracted name: $name")
            return name
        }
        AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(text = "Send", index = 0)))
        ifScreenContentIs("Enter PIN", all = true)
        StatusLogEngine.updateLog("Waiting for PIN input field...\n")
        pinInput()
        StatusLogEngine.updateLog("Waiting for transaction confirmation...\n")
        AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(text = "OK", index = 0)))
        ifScreenContentIs("Retry", all = true, timeout = 6000)
        val deferredResult = CompletableDeferred<Any?>()
        AutomationEngine.sendCommand(AutomationCommand.ExtractName(deferredResult, index = 1))
        val name = deferredResult.await()
        if (name == "Retry") {
            StatusLogEngine.updateLog("Transaction failed! Retrying..\n")
        }
        ifScreenContentIs("Request executed successfully", all = true)
        StatusLogEngine.updateLog("Transaction successful!\n")
        return null
    }

    private suspend fun pochi(number: String, dummy: Boolean, amount: Int?): Any? {
        StatusLogEngine.updateLog("Waiting for app to launch and show 'Send' button...\n")
        ifScreenContentIs("My Usage", all = true)
        AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(text = "Lipa na M-PESA", index = 0)))
        StatusLogEngine.updateLog("Waiting for phone number input field...\n")
        ifScreenContentIs("Send Money", all = true)
        AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(text = "POCHI LA BIASHARA", index = 0)))
        ifScreenContentIs("Phone Number", all = true)
        AutomationEngine.sendCommand(AutomationCommand.Paste(Selector(text = "Phone Number"), number))
        val pasteAmount = amount?.toString() ?: "1"
        AutomationEngine.sendCommand(AutomationCommand.Paste(Selector(text = "Amount"), pasteAmount))
        StatusLogEngine.updateLog("Waiting for confirm button...\n")
        kotlinx.coroutines.delay(1000)
        AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(text = "CONTINUE")))
        ifScreenContentIs("Transaction Cost:", all = true)
        StatusLogEngine.updateLog("Sending extract name command...\n")
        if (dummy) {
            val deferredResult = CompletableDeferred<Any?>()
            AutomationEngine.sendCommand(AutomationCommand.ExtractName(deferredResult, index = 1))
            val name = deferredResult.await()
            Timber.d("Extracted name: $name")
            return name
        }
        AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(text = "Send", index = 0)))
        ifScreenContentIs("Enter PIN", all = true)
        StatusLogEngine.updateLog("Waiting for PIN input field...\n")
        pinInput()
        StatusLogEngine.updateLog("Waiting for transaction confirmation...\n")
        AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(text = "OK", index = 0)))
        ifScreenContentIs("Retry", all = true, timeout = 6000)
        val deferredResult = CompletableDeferred<Any?>()
        AutomationEngine.sendCommand(AutomationCommand.ExtractName(deferredResult, index = 1))
        val name = deferredResult.await()
        if (name == "Retry") {
            StatusLogEngine.updateLog("Transaction failed! Retrying..\n")
        }
        ifScreenContentIs("Request executed successfully", all = true)
        StatusLogEngine.updateLog("Transaction successful!\n")
        return null
    }

    private suspend fun sendmoney(number: String, dummy: Boolean, amount: Int?): Any? {
        StatusLogEngine.updateLog("Waiting for app to launch and show 'Send' button...\n")
        ifScreenContentIs("My Usage", all = true)
        println("Checking for 'Lipa na M-PESA'")
        AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(text = "Lipa na M-PESA", index = 0)))
        StatusLogEngine.updateLog("Waiting for phone number input field...\n")
        ifScreenContentIs("Send Money", all = true)
        AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(text = "Send Money", index = 0)))
        ifScreenContentIs("Phone Number", all = true)
        AutomationEngine.sendCommand(AutomationCommand.Paste(Selector(text = "Phone Number"), number))
        val pasteAmount = amount?.toString() ?: "1"
        AutomationEngine.sendCommand(AutomationCommand.Paste(Selector(text = "Amount"), pasteAmount))
        StatusLogEngine.updateLog("Waiting for confirm button...\n")
        kotlinx.coroutines.delay(1000)
        AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(text = "CONTINUE")))
        ifScreenContentIs("Transaction Cost:", all = true)
        StatusLogEngine.updateLog("Sending extract name command...\n")
        if (dummy) {
            val deferredResult = CompletableDeferred<Any?>()
            AutomationEngine.sendCommand(AutomationCommand.ExtractName(deferredResult, index = 1))
            val name = deferredResult.await()
            Timber.d("Extracted name: $name")
            return name
        }
        AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(text = "Send", index = 0)))
        ifScreenContentIs("Enter PIN", all = true)
        StatusLogEngine.updateLog("Waiting for PIN input field...\n")
        pinInput()
        StatusLogEngine.updateLog("Waiting for transaction confirmation...\n")
        AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(text = "OK", index = 0)))
        ifScreenContentIs("Retry", all = true, timeout = 6000)
        val deferredResult = CompletableDeferred<Any?>()
        AutomationEngine.sendCommand(AutomationCommand.ExtractName(deferredResult, index = 1))
        val name = deferredResult.await()
        if (name == "Retry") {
            StatusLogEngine.updateLog("Transaction failed! Retrying..\n")
        }
        ifScreenContentIs("Request executed successfully", all = true)
        StatusLogEngine.updateLog("Transaction successful!\n")
        return null
    }

    private suspend fun ifScreenContentIs(expectedName: String, index: Int = 0, all: Boolean = false, timeout: Long? = null) {
        println("Checking for '$expectedName'")
        var found = false
        val startTime = System.currentTimeMillis()
        while (!found) {
            println("Checking for while loop run")
            if (timeout != null && System.currentTimeMillis() - startTime > timeout) {
                Timber.w("Timed out waiting for name: $expectedName")
                break
            }
            val deferredResult = CompletableDeferred<Any?>()
            AutomationEngine.sendCommand(AutomationCommand.ExtractName(deferredResult, index, all))
            kotlinx.coroutines.delay(1000)
            val result = deferredResult.await()
            Timber.d("Checking for '$expectedName', current value: $result")
            if (result is String && result == expectedName) {
                Timber.d("Successfully found name: $result")
                break
            }
            if (result is List<*>) {
                for (name in result) {
                    if (name == expectedName) {
                        Timber.d("Successfully found name in list: $expectedName")
                        found = true
                        break
                    }
                }
            }
        }
    }

    private suspend fun pinInput() {
        val sharedPreferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(getApplication())
        val pin = sharedPreferences.getString("mpesa_pin", null)

        if (pin.isNullOrBlank()) {
            StatusLogEngine.updateLog("M-PESA PIN not set in settings. Please set it and try again.\n")
            _events.emit(AutomationEvent.ShowError("M-PESA PIN not set."))
            return
        }

        if (pin.length != 4) {
            StatusLogEngine.updateLog("PIN must be 4 digits.\n")
            _events.emit(AutomationEvent.ShowError("PIN must be 4 digits."))
            return
        }

        for (digit in pin) {
            AutomationEngine.sendCommand(AutomationCommand.Tap(Selector(text = digit.toString())))
            kotlinx.coroutines.delay(500) // Keep a small delay between taps
        }
    }

    private fun formatAmount(amount: Int?): String {
        val a = (amount ?: 0).toDouble()
        return java.text.NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }.format(a)
    }

    private fun generateTransactionCode(method: String): String {
        val rnd = SecureRandom()
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val sb = StringBuilder("TK")
        val marker = when {
            method.equals("Pochi la biashara", ignoreCase = true) -> "IJE"
            method.equals("Send money", ignoreCase = true) -> "JJE"
            method.contains("Bygoods", ignoreCase = true) -> "IJE2"
            else -> "JX"
        }
        sb.append(marker)
        repeat(6) { sb.append(chars[rnd.nextInt(chars.length)]) }
        return sb.toString()
    }

    private suspend fun getAndUpdateBalance(amount: Int?): String {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(getApplication())
        val noteTemplate = prefs.getString("note_template", null)

        if (!noteTemplate.isNullOrBlank()) {
            val candidate = noteTemplate.replace("Ksh", "", ignoreCase = true).replace(",", "").trim()
            candidate.toDoubleOrNull()?.let {
                val newBalance = max(0.0, it - (amount ?: 0).toDouble())
                prefs.edit().putString("mpesa_balance", String.format(Locale.US, "%.2f", newBalance)).apply()
                return java.text.NumberFormat.getNumberInstance(Locale.US).apply {
                    minimumFractionDigits = 2
                    maximumFractionDigits = 2
                }.format(newBalance)
            }
        }

        val repo = (getApplication<Application>() as PochiPayApplication).repository
        val latestFromDb = try {
            repo.getLatestMpesaBalance()
        } catch (t: Throwable) {
            null
        }
        if (latestFromDb != null) {
            val newBalance = max(0.0, latestFromDb - (amount ?: 0).toDouble())
            prefs.edit().putString("mpesa_balance", String.format(Locale.US, "%.2f", newBalance)).apply()
            return java.text.NumberFormat.getNumberInstance(Locale.US).apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }.format(newBalance)
        }

        val previous = prefs.getString("mpesa_balance", null)?.toDoubleOrNull() ?: 5000.00
        val newBalance = max(0.0, previous - (amount ?: 0).toDouble())
        prefs.edit().putString("mpesa_balance", String.format(Locale.US, "%.2f", newBalance)).apply()
        return java.text.NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }.format(newBalance)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun assembleTemplate(method: String, amount: Int?, number: String, name: String): String {
        val amtFormatted = formatAmount(amount)
        val date = LocalDateTime.now()
        val dateStr = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault()))
        val timeStr = date.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
        val txCode = generateTransactionCode(method)
        val balanceStr = getAndUpdateBalance(amount)

        return when {
            method.equals("Pochi la biashara", ignoreCase = true) ->
                "$txCode Confirmed. Ksh$amtFormatted sent to $name on $dateStr at $timeStr. New M-PESA balance is Ksh$balanceStr. Transaction cost, Ksh0.00. Amount you can transact within the day is 499,797.00. Sign up for Lipa Na M-PESA Till online https://m-pesaforbusiness.co.ke"
            method.equals("Send money", ignoreCase = true) ->
                "$txCode Confirmed. Ksh$amtFormatted sent to $name $number on $dateStr at $timeStr. New M-PESA balance is Ksh$balanceStr. Transaction cost, Ksh0.00. Amount you can transact within the day is 499,730.00. Earn interest daily on Ziidi MMF,Dial *334#"
            method.contains("Bygoods", ignoreCase = true) ->
                "$txCode Confirmed. Ksh$amtFormatted paid to $name. on $dateStr at $timeStr.New M-PESA balance is Ksh$balanceStr. Transaction cost, Ksh0.00. Amount you can transact within the day is 499,632.00. Save frequent Tills for quick payment on M-PESA app https://bit.ly/mpesalnk"
            else ->
                "$txCode Confirmed. Ksh$amtFormatted to $name on $dateStr at $timeStr. New M-PESA balance is Ksh$balanceStr."
        }
    }
}

