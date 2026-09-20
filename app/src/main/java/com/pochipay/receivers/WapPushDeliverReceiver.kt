package com.pochipay.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * Receiver for WAP_PUSH_DELIVER broadcast. Required for SMS role qualification on Android 13+.
 * The system sends WAP_PUSH_DELIVER when an MMS/WAP-PUSH has been delivered.
 */
class WapPushDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        if (intent.action == "android.provider.Telephony.WAP_PUSH_DELIVER") {
            Timber.d("WAP_PUSH_DELIVER received - MMS delivered")
            // No additional processing here; MmsReceiver handles WAP_PUSH_RECEIVED and the MMS DB is queried
            // This receiver exists to satisfy RoleManager's required components for SMS role qualification
        }
    }
}
