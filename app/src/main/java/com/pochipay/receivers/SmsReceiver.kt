package com.pochipay.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import com.pochipay.data.Repository
import com.pochipay.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * BroadcastReceiver to handle incoming SMS messages.
 * Creates a conversation for each unique sender phone number.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Timber.d("Intent extras keys=${intent.extras?.keySet()}")

            // Prefer platform helper which handles pdus/formats
            var messages: Array<SmsMessage> = emptyArray()
            try {
                messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            } catch (e: Exception) {
                Timber.w(e, "getMessagesFromIntent failed, falling back to manual pdu parsing")
            }

            if (messages.isEmpty()) {
                val rawPdus = intent.extras?.get("pdus")
                Timber.d("raw pdus class=${rawPdus?.javaClass}")
                if (rawPdus == null) {
                    Timber.w("SMS pdu is null or empty")
                    return
                }

                val pduList = mutableListOf<ByteArray>()
                when (rawPdus) {
                    is Array<*> -> {
                        for (elem in rawPdus) {
                            when (elem) {
                                is ByteArray -> pduList.add(elem)
                                null -> Timber.w("pdus element is null")
                                else -> {
                                    try {
                                        val asBytes = elem as ByteArray
                                        pduList.add(asBytes)
                                    } catch (e: Exception) {
                                        Timber.w(e, "Unexpected pdus element type: ${elem?.javaClass}")
                                    }
                                }
                            }
                        }
                    }
                    is ByteArray -> pduList.add(rawPdus)
                    else -> Timber.w("Unexpected pdus container type: ${rawPdus.javaClass}")
                }

                if (pduList.isEmpty()) {
                    Timber.w("resolved pdu list is empty")
                    return
                }

                messages = pduList.mapNotNull {
                    try {
                        SmsMessage.createFromPdu(it)
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to create SmsMessage from pdu (fallback)")
                        null
                    }
                }.toTypedArray()
            }

            // Group parts by originating address and concatenate to form full messages
            val grouped = messages.groupBy { it.originatingAddress ?: "Unknown" }
            for ((phoneNumber, parts) in grouped) {
                val fullMessage = parts.joinToString(separator = "") { it.messageBody ?: "" }
                val timestamp = parts.maxOfOrNull { it.timestampMillis } ?: System.currentTimeMillis()

                Timber.d("SMS received from: $phoneNumber, full Message: $fullMessage")

                // Create conversation and message in database
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val repository = Repository.getInstance(context)

                        // Phone number serves as the topic/conversation identifier
                        // mark as not from user (incoming)
                        repository.createConversation(
                            topic = phoneNumber,
                            message = fullMessage,
                            isFromUser = false
                        )

                        // Show notification once per full message
                        NotificationHelper(context).showNotification(
                            phoneNumber,
                            fullMessage
                        )

                        Timber.d("SMS conversation created for $phoneNumber")
                    } catch (e: Exception) {
                        Timber.e(e, "Error processing SMS from $phoneNumber")
                    }
                }
            }
        }
    }
}
