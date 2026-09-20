package com.pochipay.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.pochipay.MainActivity
import timber.log.Timber

/**
 * Helper class for managing conversation shortcuts. Conversation shortcuts enable larger avatars in
 * notifications and bubble support.
 */
object ShortcutHelper {

    private const val SHORTCUT_PREFIX = "conversation_"

    /**
     * Creates or updates a conversation shortcut with the given avatar. This enables larger avatars
     * in notifications (up to 104dp) and bubble support.
     *
     * @param context Application context
     * @param conversationId Unique identifier for the conversation (e.g., phone number)
     * @param name Display name for the conversation
     * @param avatar Avatar bitmap for the conversation
     * @return Shortcut ID that can be used to link notifications
     */
    fun createOrUpdateConversationShortcut(
            context: Context,
            conversationId: String,
            name: String,
            avatar: Bitmap
    ): String {
        // Only create shortcuts on Android 11+ where conversation shortcuts are supported
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Timber.d("Conversation shortcuts require Android 11+, skipping")
            return ""
        }

        val shortcutId = "$SHORTCUT_PREFIX$conversationId"

        try {
            // Create intent for when user taps the shortcut or bubble
            val intent =
                    Intent(context, MainActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        data = Uri.parse("ntfy://conversation/$conversationId")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }

            // Create adaptive icon from avatar bitmap
            val icon = IconCompat.createWithAdaptiveBitmap(avatar)

            // Build the shortcut
            val shortcut =
                    ShortcutInfoCompat.Builder(context, shortcutId)
                            .setShortLabel(name)
                            .setLongLabel(name)
                            .setIcon(icon)
                            .setIntent(intent)
                            .setLongLived(
                                    true
                            ) // Keeps shortcut alive for better conversation history
                            .setCategories(
                                    setOf("com.pochipay.category.CONVERSATION")
                            )
                            .build()

            // Push the dynamic shortcut
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
            Timber.d("Created/Updated conversation shortcut: $shortcutId for $name")

            return shortcutId
        } catch (e: Exception) {
            Timber.e(e, "Failed to create conversation shortcut for $conversationId")
            return ""
        }
    }

    /**
     * Removes a conversation shortcut. Call this when a conversation is deleted.
     *
     * @param context Application context
     * @param conversationId The conversation ID used when creating the shortcut
     */
    fun removeConversationShortcut(context: Context, conversationId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }

        val shortcutId = "$SHORTCUT_PREFIX$conversationId"
        try {
            ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(shortcutId))
            Timber.d("Removed conversation shortcut: $shortcutId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to remove conversation shortcut: $shortcutId")
        }
    }

    /** Removes all conversation shortcuts. Useful for app cleanup or logout scenarios. */
    fun removeAllConversationShortcuts(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }

        try {
            val shortcuts = ShortcutManagerCompat.getDynamicShortcuts(context)
            val conversationShortcuts =
                    shortcuts.filter { it.id.startsWith(SHORTCUT_PREFIX) }.map { it.id }

            if (conversationShortcuts.isNotEmpty()) {
                ShortcutManagerCompat.removeDynamicShortcuts(context, conversationShortcuts)
                Timber.d("Removed ${conversationShortcuts.size} conversation shortcuts")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to remove all conversation shortcuts")
        }
    }
}
