package com.pochipay.viewmodels

sealed class AutomationUiState {
    // Main menus
    object InitialScreen : AutomationUiState()         // Shows "Safaricom+" & "M-PESA"
    object MpesaMainMenu : AutomationUiState()         // Shows "Send Money", "Lipa na M-PESA", etc.
    object LipaNaMpesaMenu : AutomationUiState()     // Shows "Buy Goods", "Pochi", etc.

    // "Send Money" flow
    object EnterSendMoneyPhone : AutomationUiState()
    data class EnterSendMoneyAmount(val phoneNumber: String) : AutomationUiState()

    // "Buy Goods" (Till) flow
    object EnterTillNumber : AutomationUiState()
    data class EnterTillAmount(val tillNumber: String) : AutomationUiState()

    // "Pochi La Biashara" flow
    object EnterPochiPhone : AutomationUiState()
    data class EnterPochiAmount(val phoneNumber: String) : AutomationUiState()
}
