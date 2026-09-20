package com.pochipay.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * BroadcastReceiver to handle default SMS app role requests.
 * Required for the app to be selectable as default messaging app.
 */
class DefaultSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        when (intent.action) {
            "android.provider.Telephony.ACTION_CHANGE_DEFAULT" -> {
                Timber.d("Default SMS app status changed")
            }
            "android.provider.Telephony.ACTION_DEFAULT_SMS_PACKAGE" -> {
                Timber.d("System querying default SMS package")
                resultData = context.packageName
            }
        }
        }
    }

