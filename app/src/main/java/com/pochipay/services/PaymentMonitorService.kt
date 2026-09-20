package com.pochipay.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pochipay.MainActivity
import com.pochipay.R
import com.pochipay.StatusLogEngine
import com.pochipay.automation.BusinessStkAutomation
import com.pochipay.data.Repository
import com.pochipay.network.ApiService
import com.pochipay.network.StkPushResultRequest
import com.pochipay.network.VerificationResultRequest
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import timber.log.Timber
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class PaymentMonitorService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var isRunning = false
    private var socket: Socket? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            startForegroundService()
            startMonitoring()
            connectSocket()
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        val channelId = "PaymentMonitorChannel"
        val channelName = "PochiPay Monitor"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("PochiPay Service Active")
            .setContentText("Connected to Push Server (Verification & STK)")
            .setSmallIcon(R.drawable.ic_check_circle)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(999, notification)
    }

    private fun getBaseUrl(): String {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val customUrl = prefs.getString("custom_server_url", "")?.trim()
        var baseUrl = if (!customUrl.isNullOrEmpty()) customUrl else "https://uon-smart-backend.onrender.com"
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.dropLast(1)
        return baseUrl
    }

    private fun getApiService(): ApiService {
        var baseUrl = getBaseUrl()
        if (!baseUrl.endsWith("/")) baseUrl += "/"
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    private fun connectSocket() {
        serviceScope.launch {
            try {
                val baseUrl = getBaseUrl()
                Timber.d("SocketIO: Connecting to $baseUrl")
                
                val opts = IO.Options()
                opts.forceNew = true
                opts.reconnection = true

                socket = IO.socket(baseUrl, opts)
                
                socket?.on(Socket.EVENT_CONNECT) {
                    Timber.i("SocketIO: Connected! ID: ${socket?.id()}")
                }

                socket?.on(Socket.EVENT_DISCONNECT) {
                    Timber.w("SocketIO: Disconnected")
                }

                // 1. Payment Verification Event
                socket?.on("request_verification") { args ->
                    Timber.i("SocketIO: EVENT RECEIVED: request_verification")
                    if (args.isNotEmpty()) {
                        val data = args[0] as? JSONObject
                        Timber.d("Event Data: $data")
                        if (data != null) {
                            val ref = data.optString("reference", data.optString("code", "")).trim()
                            val amt = data.optDouble("amount", 0.0)
                            if (ref.isNotEmpty()) {
                                serviceScope.launch {
                                    try {
                                        val repository = Repository.getInstance(applicationContext)
                                        val isValid = repository.checkTransaction(ref, if (amt > 0) amt.toString() else "")
                                        val resultJson = JSONObject().apply {
                                            put("reference", ref)
                                            put("verified", isValid)
                                            put("amount", amt)
                                            put("timestamp", System.currentTimeMillis())
                                        }
                                        socket?.emit("verification_result", resultJson)
                                        Timber.i("SocketIO: Emitted verification_result: $resultJson")
                                    } catch (e: Exception) {
                                        Timber.e(e, "Error executing socket verification")
                                    }
                                }
                            }
                        }
                    }
                    checkPendingVerifications()
                }

                // 2. Business STK Push Event
                socket?.on("request_stk_push") { args ->
                    Timber.i("SocketIO: EVENT RECEIVED: request_stk_push")
                    if (args.isNotEmpty()) {
                        val data = args[0] as? JSONObject
                        Timber.d("STK Push Data: $data")
                        if (data != null) {
                            val requestId = data.optString("requestId", data.optString("id", System.currentTimeMillis().toString()))
                            val phone = data.optString("phone", data.optString("phone_number", ""))
                            val amount = data.optDouble("amount", 0.0)
                            val reference = data.optString("reference", "")
                            handleStkPushRequest(requestId, phone, amount, reference)
                        }
                    }
                }

                socket?.connect()

            } catch (e: Exception) {
                Timber.e(e, "SocketIO Setup Error")
            }
        }
    }

    private fun handleStkPushRequest(requestId: String, phone: String, amount: Double, reference: String) {
        if (phone.isBlank() || amount <= 0.0) {
            Timber.w("Invalid STK push request: phone=$phone, amount=$amount")
            return
        }

        serviceScope.launch {
            try {
                sendBroadcast(Intent("com.pochipay.ACTION_PROCESSING_TASK"))
                StatusLogEngine.updateLog("Server requested STK Push for $phone, KES $amount (ID: $requestId)\n")

                val result = BusinessStkAutomation.execute(
                    context = applicationContext,
                    phone = phone,
                    amount = amount,
                    requestId = requestId
                )

                // Report result via Socket.io
                val resultJson = JSONObject().apply {
                    put("requestId", requestId)
                    put("success", result.success)
                    put("customerName", result.customerName ?: "")
                    put("errorMessage", result.errorMessage ?: "")
                    put("reference", reference)
                }
                socket?.emit("stk_push_result", resultJson)
                Timber.i("SocketIO: Emitted stk_push_result: $resultJson")

                // Also report via REST API
                try {
                    val api = getApiService()
                    api.sendStkPushResult(
                        StkPushResultRequest(
                            requestId = requestId,
                            success = result.success,
                            customerName = result.customerName,
                            errorMessage = result.errorMessage
                        )
                    )
                } catch (e: Exception) {
                    Timber.d("REST stk-result call exception: ${e.message}")
                }

                sendBroadcast(Intent("com.pochipay.ACTION_TASK_COMPLETED"))
            } catch (e: Exception) {
                Timber.e(e, "Error executing STK push automation")
                sendBroadcast(Intent("com.pochipay.ACTION_TASK_COMPLETED"))
            }
        }
    }

    private fun startMonitoring() {
        serviceScope.launch {
            Timber.d("Starting Payment Monitor Loop (Fallback)")
            while (isActive) {
                checkPendingVerifications()
                checkPendingStkPushes()
                delay(30000)
            }
        }
    }

    private fun checkPendingVerifications() {
        serviceScope.launch {
            try {
                val api = getApiService()
                val repository = Repository.getInstance(applicationContext)

                Timber.d("Monitor: Checking pending verifications...")
                val response = api.getPendingVerifications()
                val pending = response.pending

                if (pending.isNotEmpty()) {
                    Timber.d("Monitor: Found ${pending.size} pending verifications.")
                    sendBroadcast(Intent("com.pochipay.ACTION_PROCESSING_TASK"))

                    for (req in pending) {
                        val code: String = req.code
                        val amountStr: String = req.amount.toString()
                        val isValidTransaction = repository.checkTransaction(code, amountStr)
                        Timber.d("Monitor: Check $code -> $isValidTransaction")
                        
                        api.sendVerificationResult(VerificationResultRequest(
                            transactionId = req.id,
                            isValid = isValidTransaction,
                            metadata = mapOf("worker" to "socket_push")
                        ))
                    }
                    sendBroadcast(Intent("com.pochipay.ACTION_TASK_COMPLETED"))
                }
            } catch (e: Exception) {
                Timber.e("Monitor Check Error: ${e.message}")
            }
        }
    }

    private fun checkPendingStkPushes() {
        serviceScope.launch {
            try {
                val api = getApiService()
                val response = api.getPendingStkPushes()
                val pending = response.pending

                if (pending.isNotEmpty()) {
                    Timber.d("Monitor: Found ${pending.size} pending STK pushes via polling.")
                    for (stk in pending) {
                        handleStkPushRequest(
                            requestId = stk.id,
                            phone = stk.phone,
                            amount = stk.amount,
                            reference = stk.reference ?: ""
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.d("Polling STK check: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        socket?.disconnect()
        socket?.off()
        serviceJob.cancel()
        isRunning = false
    }
}
