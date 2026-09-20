package com.pochipay.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pochipay.data.Repository
import com.pochipay.network.ApiService
import com.pochipay.network.VerificationResultRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber

class VerificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Timber.d("Starting Payment Verification check...")
            
            // 1. Initialize API (Quick & Dirty separate instance or DI if available. Using new instance for robustness in worker)
            // Assuming BACKEND_URL logic is handled elsewhere or hardcoded for now, checking server.js port 5000? 
            // In production, this URL should be the deployed backend URL.
            // For now, I'll assume we use the Vercel URL or IP. 
            // NOTE: Android Emulator needs 10.0.2.2 for localhost.
            
            // TODO: Replace with actual BASE URL from config
            val BASE_URL = "https://uon-smart-timetable-backend.vercel.app/" // Or user provided URL?
            // "The lipana API is taking time..." -> implies we are modifying existing backend.
            // I'll check where ApiService is instantiated normally to copy Base URL, but for now I'll use a placeholder or best guess.
            // Checking step 20 `server.js` -> it has no hardcoded URL.
            // I'll use a likely valid URL or try to find it. 
            // Actually, I should check how ApiService is created in the app.
            
            // Let's assume we can get the singleton if configured, but Manual instantiation is safer for Worker if DI isn't set up.
            // I'll peek at where ApiService is created.
            
            // Read URL from Preferences (Robust for Worker)
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)
            val customUrl = prefs.getString("custom_server_url", "")?.trim()
            
            // Fallback to DependencyProvider or default if empty (but custom is expected for this test)
            var baseUrl = customUrl
            if (baseUrl.isNullOrEmpty()) {
                 // Try DependencyProvider if initialized, else default
                 // For now, default to Vercel but warn
                 baseUrl = "https://uon-smart-backend.onrender.com"
            }
            
            // Ensure trailing slash
            if (!baseUrl.endsWith("/")) {
                baseUrl += "/"
            }

            val api = Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)

            val repository = Repository.getInstance(applicationContext)

            // 2. Get pending verifications
            val response = api.getPendingVerifications()
            val pending = response.pending

            if (pending.isEmpty()) {
                Timber.d("No pending verifications.")
                // Update UI: Connected but idle (Optional: Broadcast IDLE?)
                return@withContext Result.success()
            }

            // Notify UI: Processing
            val intentProcessing = android.content.Intent("com.pochipay.ACTION_PROCESSING_TASK")
            applicationContext.sendBroadcast(intentProcessing)

            Timber.d("Found ${pending.size} pending verifications.")

            // 3. Check each
            for (req in pending) {
                Timber.d("Checking transaction: ${req.code} for ${req.amount}")
                
                val isValid = repository.checkTransaction(req.code, req.amount.toString())
                
                Timber.d("Result for ${req.code}: $isValid")

                // 4. Send Result
                api.sendVerificationResult(VerificationResultRequest(
                    transactionId = req.id,
                    isValid = isValid,
                    metadata = mapOf("worker" to "android_ntfy5")
                ))
            }

            // Notify UI: Done
            val intentDone = android.content.Intent("com.pochipay.ACTION_TASK_COMPLETED")
            applicationContext.sendBroadcast(intentDone)

            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "VerificationWorker failed")
            // Retry if network error?
            Result.retry()
        }
    }
}
