package com.pochipay

import android.app.Service
import android.content.Intent
import android.os.IBinder
import timber.log.Timber

/**
 * A service that is required for an app to be a default SMS app.
 * It handles the "quick reply" action from a notification.
 */
class RespondViaMessageService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("RespondViaMessageService started with intent: $intent")
        // In a real implementation, you would handle the reply here,
        // for example, by sending an SMS.
        // For now, we just stop the service.
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        // This service is not designed to be bound to, so return null.
        return null
    }
}
