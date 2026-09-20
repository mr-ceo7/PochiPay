package com.pochipay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import com.pochipay.data.AutomationCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var statusLogView: TextView
    private lateinit var logScrollView: ScrollView

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("OverlayService created")

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)
        statusLogView = overlayView.findViewById(R.id.overlay_status_log)
        logScrollView = overlayView.findViewById(R.id.overlay_log_scrollview)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(overlayView, params)

        // Listen for log updates
        scope.launch {
            StatusLogEngine.logFlow.collectLatest { log ->
                statusLogView.append(log)
            }
        }

        overlayView.findViewById<Button>(R.id.toggle_log_button).setOnClickListener {
            logScrollView.visibility = if (logScrollView.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        overlayView.findViewById<Button>(R.id.button_stop).setOnClickListener {
            scope.launch {
                AutomationEngine.sendCommand(AutomationCommand.Stop)
            }
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("OverlayService destroyed")
        windowManager.removeView(overlayView)
        job.cancel()
    }
}
