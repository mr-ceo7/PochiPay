package com.pochipay.utils

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import com.pochipay.MainActivity
import com.pochipay.R
import com.pochipay.TripleTapReceiver
import timber.log.Timber

class NotificationHelper(private val context: Context) {

        private val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        fun createNotificationChannel() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val channel =
                                NotificationChannel(
                                                CHANNEL_ID,
                                                "Automation Notifications",
                                                NotificationManager
                                                        .IMPORTANCE_HIGH // Changed to HIGH for
                                                // time-sensitive
                                                // messages
                                                )
                                        .apply {
                                                description =
                                                        "Notifications for incoming messages and automation events"
                                                enableLights(true)
                                                enableVibration(true)
                                        }
                        notificationManager.createNotificationChannel(channel)
                        Timber.d("Notification channel created: $CHANNEL_ID")
                }
        }

        @SuppressLint("LaunchActivityFromNotification")
        fun showNotification(title: String, message: String) {
                try {
                        Timber.d("showNotification called with title='$title', message='$message'")

                        // Check permission for Android 13+ (API 33+)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val hasPermission =
                                        ActivityCompat.checkSelfPermission(
                                                context,
                                                android.Manifest.permission.POST_NOTIFICATIONS
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                                if (!hasPermission) {
                                        Timber.w("POST_NOTIFICATIONS permission not granted.")
                                        return
                                }
                        }

                        // Create Person for the sender
                        // Generate large avatar for prominent display in notifications (1024px for
                        // max quality)
                        val avatarBitmap = AvatarGenerator.generateAvatar(title, 1024)
                        val sender =
                                Person.Builder()
                                        .setName(title)
                                        .setIcon(IconCompat.createWithBitmap(avatarBitmap))
                                        .setImportant(true)
                                        .build()

                        // Create Person for the user (me)
                        val user = Person.Builder().setName("Me").build()

                        // Create MessagingStyle
                        // Don't set conversation title to allow larger avatar display
                        // (person-to-person layout)
                        val messagingStyle =
                                NotificationCompat.MessagingStyle(user)
                                        .addMessage(
                                                NotificationCompat.MessagingStyle.Message(
                                                        message,
                                                        System.currentTimeMillis(),
                                                        sender
                                                )
                                        )

                        // Create or update conversation shortcut for larger avatars and bubble
                        // support
                        val shortcutId =
                                ShortcutHelper.createOrUpdateConversationShortcut(
                                        context,
                                        conversationId =
                                                title, // Using title (phone number) as conversation
                                        // ID
                                        name = title,
                                        avatar = avatarBitmap
                                )

                        // Create bubble metadata for Android 11+ (enables floating conversations)
                        val bubbleMetadata =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                                                shortcutId.isNotEmpty()
                                ) {
                                        // Intent for when bubble is tapped
                                        val bubbleIntent =
                                                Intent(context, MainActivity::class.java).apply {
                                                        action = Intent.ACTION_VIEW
                                                        data =
                                                                Uri.parse(
                                                                        "ntfy://conversation/$title"
                                                                )
                                                        flags =
                                                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                                                        Intent.FLAG_ACTIVITY_CLEAR_TASK
                                                }
                                        val bubblePendingIntent =
                                                PendingIntent.getActivity(
                                                        context,
                                                        title.hashCode(),
                                                        bubbleIntent,
                                                        PendingIntent.FLAG_UPDATE_CURRENT or
                                                                PendingIntent.FLAG_MUTABLE
                                                )

                                        NotificationCompat.BubbleMetadata.Builder(
                                                        bubblePendingIntent,
                                                        IconCompat.createWithAdaptiveBitmap(
                                                                avatarBitmap
                                                        )
                                                )
                                                .setDesiredHeight(600) // Height in dp for bubble
                                                .setAutoExpandBubble(false) // Don't auto-expand
                                                .setSuppressNotification(
                                                        false
                                                ) // Show in notification shade too
                                                .build()
                                } else {
                                        null
                                }

                        // Check if sender is a shortcode (alphanumeric or short numeric codes like
                        // MPESA, SAFARICOM)
                        // Shortcodes don't support replies, so we'll only show "Mark as Read" for
                        // them
                        val isShortcode = title.length <= 6 || title.any { it.isLetter() }

                        // Create RemoteInput for Inline Reply (only for regular phone numbers)
                        val remoteInput =
                                RemoteInput.Builder(KEY_TEXT_REPLY).setLabel("Reply").build()

                        // Create PendingIntent for Reply Action
                        val replyIntent =
                                Intent(context, NotificationActionReceiver::class.java).apply {
                                        action = ACTION_REPLY
                                        putExtra(EXTRA_NOTIFICATION_ID, NOTIFICATION_ID)
                                        putExtra(EXTRA_SENDER, title)
                                }
                        val replyPendingIntent =
                                PendingIntent.getBroadcast(
                                        context,
                                        NOTIFICATION_ID,
                                        replyIntent,
                                        PendingIntent.FLAG_UPDATE_CURRENT or
                                                PendingIntent
                                                        .FLAG_MUTABLE // Mutable for RemoteInput
                                )

                        // Create Reply Action
                        val replyAction =
                                NotificationCompat.Action.Builder(
                                                R.drawable.ic_send,
                                                "Reply",
                                                replyPendingIntent
                                        )
                                        .addRemoteInput(remoteInput)
                                        .build()

                        // Create Mark as Read Intent
                        val markAsReadIntent =
                                Intent(context, NotificationActionReceiver::class.java).apply {
                                        action = ACTION_MARK_AS_READ
                                        putExtra(EXTRA_NOTIFICATION_ID, NOTIFICATION_ID)
                                }
                        val markAsReadPendingIntent =
                                PendingIntent.getBroadcast(
                                        context,
                                        NOTIFICATION_ID + 1, // Different ID
                                        markAsReadIntent,
                                        PendingIntent.FLAG_UPDATE_CURRENT or
                                                PendingIntent.FLAG_IMMUTABLE
                                )

                        // Create Tap Intent (Open App)
                        val tapIntent =
                                Intent(TripleTapReceiver.ACTION_TRIPLE_TAP) // Or MainActivity
                        val tapPendingIntent =
                                PendingIntent.getBroadcast(
                                        context,
                                        NOTIFICATION_ID + 2,
                                        tapIntent,
                                        PendingIntent.FLAG_UPDATE_CURRENT or
                                                PendingIntent.FLAG_IMMUTABLE
                                )

                        // Build Notification
                        val builder =
                                NotificationCompat.Builder(context, CHANNEL_ID)
                                        .setSmallIcon(R.drawable.ic_notification)
                                        .setLargeIcon(avatarBitmap) // Show avatar prominently
                                        .setStyle(messagingStyle)
                                        .setColor(
                                                context.getColor(R.color.accent_blue)
                                        ) // Use app accent color
                                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                                        .setCategory(NotificationCompat.CATEGORY_MESSAGE)

                        // Only add reply action for regular phone numbers (not shortcodes)
                        if (!isShortcode) {
                                builder.addAction(replyAction)
                        }

                        // Always add "Mark as Read" action
                        builder.addAction(
                                        android.R.drawable.ic_menu_view, // Replace with checkmark
                                        // icon if
                                        // available
                                        "Mark as Read",
                                        markAsReadPendingIntent
                                )
                                .setContentIntent(tapPendingIntent)
                                .setAutoCancel(true)

                        // Link notification to shortcut for larger avatars (Android 11+)
                        if (shortcutId.isNotEmpty()) {
                                builder.setShortcutId(shortcutId)
                        }

                        // Add bubble metadata if available (Android 11+)
                        if (bubbleMetadata != null) {
                                builder.setBubbleMetadata(bubbleMetadata)
                        }

                        notificationManager.notify(NOTIFICATION_ID, builder.build())
                        Timber.d("MessagingStyle notification posted successfully")
                } catch (e: Exception) {
                        Timber.e(e, "Failed to show notification")
                }
        }

        companion object {
                private const val CHANNEL_ID = "automation_notifications"
                private const val NOTIFICATION_ID = 1
                const val ACTION_MARK_AS_READ = "com.pochipay.action.MARK_AS_READ"
                const val ACTION_REPLY = "com.pochipay.action.REPLY"
                const val EXTRA_NOTIFICATION_ID = "notification_id"
                const val EXTRA_SENDER = "sender"
                const val KEY_TEXT_REPLY = "key_text_reply"
        }
}
