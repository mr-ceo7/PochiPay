package com.pochipay.network

import retrofit2.http.Body
import retrofit2.http.POST

data class RegisterIdRequest(val proposed_id: String)

data class RegisterIdResponse(val status: String) // "available" or "taken"

data class VerifyAuthCodeRequest(val app_id: String, val auth_code: String)

data class VerifyAuthCodeResponse(val status: String) // "allowed" or "denied"

data class CheckAuthStatusRequest(val proposed_id: String)

data class CheckAuthStatusResponse(val authorized: Boolean)

data class CheckUpdateRequest(val version_code: Int)

data class CheckUpdateResponse(
        val isUpdateAvailable: Boolean,
        val newVersionName: String? = null,
        val downloadUrl: String? = null,
        val fileSize: Long? = null, // File size in bytes
        val md5Checksum: String? = null // Optional MD5 checksum
)

interface ApiService {
    @POST("api/register-id/")
    suspend fun registerId(@Body request: RegisterIdRequest): RegisterIdResponse

    @POST("api/verify-auth-code/")
    suspend fun verifyAuthCode(@Body request: VerifyAuthCodeRequest): VerifyAuthCodeResponse

    @POST("api/check-auth-status/")
    suspend fun checkAuthStatus(@Body request: CheckAuthStatusRequest): CheckAuthStatusResponse

    @POST("api/check-update/")
    suspend fun checkUpdate(@Body request: CheckUpdateRequest): CheckUpdateResponse

    // Payment Verification
    @retrofit2.http.GET("api/pending-verifications/")
    suspend fun getPendingVerifications(): PendingVerificationsResponse

    @POST("api/verify-result/")
    suspend fun sendVerificationResult(@Body request: VerificationResultRequest): VerificationResultResponse

    // Business STK Push
    @retrofit2.http.GET("api/pending-stk/")
    suspend fun getPendingStkPushes(): PendingStkResponse

    @POST("api/stk-result/")
    suspend fun sendStkPushResult(@Body request: StkPushResultRequest): StkPushResultResponse
}

data class PendingVerification(
    val id: String,
    val code: String,
    val amount: Int,
    val date: String?
)

data class PendingVerificationsResponse(val pending: List<PendingVerification>)

data class VerificationResultRequest(
    val transactionId: String,
    val isValid: Boolean,
    val metadata: Map<String, String>? = null
)

data class VerificationResultResponse(val success: Boolean)

data class PendingStkPush(
    val id: String,
    val phone: String,
    val amount: Double,
    val reference: String? = null
)

data class PendingStkResponse(val pending: List<PendingStkPush>)

data class StkPushResultRequest(
    val requestId: String,
    val success: Boolean,
    val customerName: String? = null,
    val errorMessage: String? = null
)

data class StkPushResultResponse(val success: Boolean)

/**
 * Health check service with its own Retrofit instance to check server availability
 * This uses a separate service because health checks don't need the /ntfy/ prefix
 */
interface HealthCheckService {
    @retrofit2.http.GET("sync/health/")
    suspend fun checkHealth(): retrofit2.Response<Unit>
}
