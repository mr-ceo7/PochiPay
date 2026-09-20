package com.pochipay.data.local

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var appId: String?
        get() = prefs.getString(KEY_APP_ID, null)
        set(value) = prefs.edit().putString(KEY_APP_ID, value).apply()

    var authCode: String?
        get() = prefs.getString(KEY_AUTH_CODE, null)
        set(value) = prefs.edit().putString(KEY_AUTH_CODE, value).apply()

    companion object {
        private const val PREFS_NAME = "ntfy5_prefs"
        private const val KEY_APP_ID = "app_id"
        private const val KEY_AUTH_CODE = "auth_code"
    }
}
