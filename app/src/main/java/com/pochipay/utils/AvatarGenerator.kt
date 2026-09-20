package com.pochipay.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.content.ContextCompat
import com.pochipay.R
import kotlin.math.abs

object AvatarGenerator {
    private val colors = listOf(
        0xFF4A90E2.toInt(),  // Blue
        0xFF4ECB71.toInt(),  // Green
        0xFFFF6B6B.toInt(),  // Red
        0xFFFFA500.toInt(),  // Orange
        0xFF9B59B6.toInt(),  // Purple
        0xFF1ABC9C.toInt(),  // Teal
        0xFFE74C3C.toInt(),  // Crimson
        0xFF3498DB.toInt(),  // Light Blue
    )

    fun generateAvatar(text: String, size: Int = 128): Bitmap {
        val firstChar = text.firstOrNull()?.uppercaseChar() ?: 'A'
        val colorIndex = abs(firstChar.hashCode()) % colors.size
        val backgroundColor = colors[colorIndex]

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw circle background
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = backgroundColor
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        // Draw text
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = size / 2.5f
            textAlign = Paint.Align.CENTER
        }

        val textBounds = Rect()
        textPaint.getTextBounds(firstChar.toString(), 0, 1, textBounds)
        val y = size / 2f + textBounds.height() / 2f
        canvas.drawText(firstChar.toString(), size / 2f, y, textPaint)

        return bitmap
    }

    fun getColorForTopic(topic: String): Int {
        val firstChar = topic.firstOrNull()?.uppercaseChar() ?: 'A'
        val colorIndex = abs(firstChar.hashCode()) % colors.size
        return colors[colorIndex]
    }
}
