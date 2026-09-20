package com.pochipay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pochipay.AutomationFragment
import timber.log.Timber

class TripleTapReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_TRIPLE_TAP = "com.pochipay.action.TRIPLE_TAP"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TRIPLE_TAP) {
            Timber.d("Triple tap detected, attempting to toggle dummy to false")
            // This is a placeholder. Actual toggling must be done via a shared ViewModel or similar mechanism.
//            AutomationFragment.dummyValueSetter?.invoke(false)
        }
    }
}
