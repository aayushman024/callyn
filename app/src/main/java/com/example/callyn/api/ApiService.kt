package com.example.callyn.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
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
    val duration: Long
)

//request as personal contact
data class PersonalRequestData(
    val requestedContact: String,
    val requestedBy: String, // The logged-in user's name
    val reason: String
)

//Version Check Data Class
data class VersionResponse(
    val latestVersion: String,
    val updateType: String, // "hard" or "soft"
    val changelog: String,
    val downloadUrl: String
)

// --- API SERVICE INTERFACE ---

interface ApiService {
    /**
     * Calls your backend to get the Zoho OAuth URL.
     */
    @GET("auth/zoho")
    suspend fun getZohoAuthUrl(): Response<AuthResponse>

    /**
     * Handles the callback from Zoho, exchanging the code for a token.
     */
    @GET("auth/callback")
    suspend fun handleCallback(@Query("code") code: String): Response<LoginResponse>

    /**
     * Calls your backend to get the logged-in user's name.
     */
    @GET("auth/me")
    suspend fun getMe(@Header("Authorization") token: String): Response<UserResponse>

    /**
     * Calls your backend to get the contacts for a specific manager.
     */
    @GET("getLegacyData")
    suspend fun getContacts(
        @Header("Authorization") token: String,
        @Query("manager") managerName: String
    ): Response<List<ContactResponse>>

    /**
     * Uploads a call log to the backend.
     */
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
}