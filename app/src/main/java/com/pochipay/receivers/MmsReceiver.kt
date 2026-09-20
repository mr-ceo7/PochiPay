package com.pochipay.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import com.pochipay.data.Repository
import com.pochipay.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * BroadcastReceiver to handle incoming MMS messages.
 * Extracts sender phone number from MMS database and creates conversation.
 */
class MmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

    // The public action for MMS/WAP push is "android.provider.Telephony.WAP_PUSH_RECEIVED"
    // Listen for WAP_PUSH with the MMS MIME type (handled by manifest intent-filter).
    if (intent.action == "android.provider.Telephony.WAP_PUSH_RECEIVED") {
            Timber.d("MMS received")

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Get the latest MMS from the provider
                    val mmsUri = Uri.parse("content://mms")
                    val cursor: Cursor? = context.contentResolver.query(
                        mmsUri,
                        arrayOf("_id", "date", "sub"),
                        null,
                        null,
                        "date DESC LIMIT 1"
                    )

                    cursor?.use {
                        if (it.moveToFirst()) {
                            val mmsId = it.getString(0)
                            val timestamp = it.getLong(1) * 1000L
                            val subject = it.getString(2) ?: ""

                            // Get sender address from mms_addr table
                            val addrUri = Uri.parse("content://mms/$mmsId/addr")
                            val addrCursor: Cursor? = context.contentResolver.query(
                                addrUri,
                                arrayOf("address", "type"),
                                "type=137", // 137 = FROM
                                null,
                                null
                            )

                            addrCursor?.use { addressCursor ->
                                if (addressCursor.moveToFirst()) {
                                    val phoneNumber = addressCursor.getString(0) ?: "Unknown"
                                    val messageBody = subject.ifEmpty { "[MMS Message]" }

                                    Timber.d("MMS received from: $phoneNumber, Subject: $subject")

                                    val repository = Repository.getInstance(context)

                                    // mark as incoming message
                                    repository.createConversation(
                                        topic = phoneNumber,
                                        message = messageBody,
                                        isFromUser = false
                                    )

                                    NotificationHelper(context).showNotification(
                                        phoneNumber,
                                        messageBody
                                    )

                                    Timber.d("MMS conversation created for $phoneNumber")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error processing MMS")
                }
            }
        }
    }
}
