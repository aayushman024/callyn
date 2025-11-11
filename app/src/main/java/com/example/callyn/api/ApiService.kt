package com.example.callyn.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

// --- DATA MODELS FOR API ---

/**
 * Model for the response from `GET /auth/zoho`
 */
data class AuthResponse(
    val authUrl: String
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
    val type: String
)

// --- API SERVICE INTERFACE ---

interface ApiService {
    /**
     * Calls your backend to get the Zoho OAuth URL.
     */
    @GET("auth/zoho")
    suspend fun getZohoAuthUrl(): Response<AuthResponse>

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
}