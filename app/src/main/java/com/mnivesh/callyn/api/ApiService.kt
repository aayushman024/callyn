package com.mnivesh.callyn.api

import com.google.gson.annotations.SerializedName
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

// --- DATA MODELS FOR API ---

/**
 * Model for the response from `GET /auth/zoho`
 */
data class AuthResponse(
    val authUrl: String
)

/**
 * Model for the response from `GET /auth/callback`
 * Includes token and user name.
 */
data class LoginResponse(
    val token: String,
    val userName: String
)

/**
 * Model for the response from `GET /auth/me`
 */
data class UserResponse(
    val name: String
)

/**
 * Model for the response from `GET /getLegacyData`
 * This must match the JSON your server sends.
 */
data class ContactResponse(
    val name: String,
    val number: String,
    val type: String,
    val pan: String,
    val familyHead: String,
    val rshipManager: String
)

/**
 * Model for the request body of `POST /uploadCallLog`
 */
data class CallLogRequest(
    val callerName: String,
    val rshipManagerName: String?,
    val type: String,
    val timestamp: Long,
    val duration: Long,
    val simSlot: String? = null,
    val isWork: Boolean
)

data class CallLogResponse(
    val _id: String,
    val callerName: String,
    val rshipManagerName: String?,
    val type: String, // "outgoing", "incoming", "missed"
    val timestamp: String,
    val duration: Long,
    val uploadedBy: String?,
    val simslot: String?,
    val isWork: Boolean,
    val uploadedAt: String?
)

data class CallLogListResponse(
    val count: Int,
    val data: List<CallLogResponse>
)

//request as personal contact
data class PersonalRequestData(
    @SerializedName("requestedContact")
    val requestedContact: String,

    @SerializedName("requestedBy")
    val requestedBy: String,

    @SerializedName("reason")
    val reason: String
)

//fetch pending requests for management level
data class PendingRequest(
    val _id: String,
    val requestedContact: String, // Client Name
    val requestedBy: String,      // RM Name
    val reason: String,
    val status: String
)

//update pending requests for management level
data class UpdateRequestStatusBody(
    val requestId: String,
    val status: String // "approved" or "denied"
)

//Version Check Data Class
data class VersionResponse(
    val latestVersion: String,
    val updateType: String, // "hard" or "soft"
    val changelog: String,
    val downloadUrl: String
)

//User Details Data Class
data class UserDetailsRequest(
    val username: String,
    val email: String,
    val phoneModel: String,
    val osLevel: String,
    val appVersion: String,
    val department: String?,
    val lastSeen: Long
)

data class UserDetailsResponse(
    val _id: String,
    val email: String,
    val username: String,
    val phoneModel: String?,
    val osLevel: String?,
    val appVersion: String?,
    val department: String,
    val lastSeen: String? // Received as ISO date string
)

// --- API SERVICE INTERFACE ---

interface ApiService {

     // Calls your backend to get the Zoho OAuth URL.

    @GET("auth/zoho")
    suspend fun getZohoAuthUrl(): Response<AuthResponse>

    //Handles the callback from Zoho, exchanging the code for a token.
    @GET("auth/callback")
    suspend fun handleCallback(@Query("code") code: String): Response<LoginResponse>

    //Calls your backend to get the logged-in user's name.
    @GET("auth/me")
    suspend fun getMe(@Header("Authorization") token: String): Response<UserResponse>

     //Calls your backend to get the contacts for a specific manager.
    @GET("getLegacyData")
    suspend fun getContacts(
        @Header("Authorization") token: String,
        @Query("manager") managerName: String
    ): Response<List<ContactResponse>>


     // Uploads a call log to the backend.
    @POST("uploadCallLog")
    suspend fun uploadCallLog(
        @Header("Authorization") token: String,
        @Body log: CallLogRequest
    ): Response<ResponseBody>

    @GET("version/latest")
    suspend fun getLatestVersion(
        @Header("Authorization") token: String
    ): Response<VersionResponse>

    @POST("requestAsPersonal")
    suspend fun requestAsPersonal(
        @Header("Authorization") token: String,
        @Body request: PersonalRequestData
    ): Response<ResponseBody>

    @GET("getPendingRequests")
    suspend fun getPendingRequests(
        @Header("Authorization") token: String
    ): Response<List<PendingRequest>>

    @PUT("updateRequestStatus")
    suspend fun updateRequestStatus(
        @Header("Authorization") token: String,
        @Body body: UpdateRequestStatusBody
    ): Response<ResponseBody>

    @POST("syncUserDetails")
    suspend fun syncUserDetails(
        @Header("Authorization") token: String,
        @Body details: UserDetailsRequest
    ): Response<ResponseBody>

    @GET("getUserDetails")
    suspend fun getAllUserDetails(
        @Header("Authorization") token: String
    ): Response<List<UserDetailsResponse>>

    @GET("getCallLogs")
    suspend fun getCallLogs(
        @Header("Authorization") token: String,
        @Query("date") date: String? = null, // Format: YYYY-MM-DD
        @Query("uploadedBy") uploadedBy: String? = null
    ): Response<CallLogListResponse>
}

