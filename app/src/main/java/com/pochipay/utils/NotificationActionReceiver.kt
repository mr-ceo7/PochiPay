package com.pochipay.utils

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.pochipay.AutomationEngine
import com.pochipay.data.AutomationCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class NotificationActionReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val pendingResult = goAsync()

        scope.launch {
            try {
                when (intent.action) {
                    NotificationHelper.ACTION_MARK_AS_READ -> {
                        val notificationId =
                                intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, -1)
                        if (notificationId != -1) {
                            val notificationManager =
                                    context.getSystemService(Context.NOTIFICATION_SERVICE) as
                                            NotificationManager
                            notificationManager.cancel(notificationId)
                            Timber.d(
                                    "Notification marked as read and dismissed. ID: $notificationId"
                            )
                        }
                    }
                    NotificationHelper.ACTION_REPLY -> {
                        val notificationId =
                                intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, -1)
                        val sender = intent.getStringExtra(NotificationHelper.EXTRA_SENDER)
                        val remoteInput = RemoteInput.getResultsFromIntent(intent)

                        if (remoteInput != null && sender != null) {
                            val replyText =
                                    remoteInput
                                            .getCharSequence(NotificationHelper.KEY_TEXT_REPLY)
                                            ?.toString()
                            if (!replyText.isNullOrBlank()) {
                                Timber.d("Received inline reply for $sender: $replyText")

                                // Send the reply via AutomationEngine
                                AutomationEngine.sendCommand(
                                        AutomationCommand.SendMessage(
                                                conversationId = sender,
                                                message = replyText,
                                                isUser = true
                                        )
                                )

                                val notificationManager =
                                        context.getSystemService(Context.NOTIFICATION_SERVICE) as
                                                NotificationManager
                                notificationManager.cancel(notificationId)
                            }
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
