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

data class AuthResponse(
    val authUrl: String
)

data class LoginResponse(
    val token: String,
    val userName: String
)

data class UserResponse(
    val name: String
)

data class ContactResponse(
    val name: String,
    val number: String,
    val type: String,
    val pan: String,
    val familyHead: String,
    val rshipManager: String,
    val aum: String,
    val familyAum: String
)

data class CallLogRequest(
    val callerName: String,
    val familyHead: String,
    val rshipManagerName: String?,
    val type: String,
    val timestamp: Long,
    val duration: Long,
    val notes: String?,
    val simSlot: String? = null,
    val isWork: Boolean
)

data class CallLogResponse(
    val _id: String,
    val callerName: String,
    val familyHead: String,
    val rshipManagerName: String?,
    val type: String, // "outgoing", "incoming", "missed"
    val timestamp: String,
    val duration: Long,
    val notes: String?,
    val uploadedBy: String?,
    val simslot: String?,
    val isWork: Boolean,
    val uploadedAt: String?
)

data class CallLogListResponse(
    val count: Int,
    val data: List<CallLogResponse>
)

//internal employee directory
data class EmployeeDirectory(
    val name: String,
    val email: String,
    val phone: String,
    val department: String,
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

data class SmsLogRequest(
    val sender: String,
    val message: String,
    val timestamp: Long
)

data class SmsLogResponse(
    val _id: String,
    val sender: String,
    val message: String,
    val timestamp: String, // Or Long, depending on exact backend serialization
    val uploadedBy: String
)

data class ViewLimitResponse(
    val canView: Boolean,
    val remaining: Int,
    val count: Int? = null,
    val success: Boolean? = null
)

//Zoho CRM Data
data class CrmRecord(
    @SerializedName("ClientName") val clientName: String?,
    @SerializedName("ClientMobileNumber") val clientMobileNumber: String?,
    @SerializedName("OwnerName") val ownerName: String?,
    @SerializedName("ID") val id: String?,
    @SerializedName("ModuleName") val moduleName: String?, // "Tickets", "Investment_leads", "Insurance_Leads"
    @SerializedName("Product") val product: String?,
    @SerializedName("LastActivity") val lastActivity: String?
)

data class CrmModuleData(
    val fetchedCount: Int,
    val uniqueCount: Int,
    val data: List<CrmRecord>
)

data class CrmSyncData(
    @SerializedName("Tickets") val tickets: CrmModuleData?,
    @SerializedName("Investment_leads") val investmentLeads: CrmModuleData?,
    @SerializedName("Insurance_Leads") val insuranceLeads: CrmModuleData?
)

data class CrmResponse(
    val success: Boolean,
    val message: String,
    val data: CrmSyncData
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
        @Query("date") date: String? = null,
        @Query("uploadedBy") uploadedBy: String? = null,
        @Query("showNotes") showNotes: Boolean = false,
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null
    ): Response<CallLogListResponse>

    @GET("getEmployeePhoneDetails")
    suspend fun getEmployeePhoneDetails(
        @Header("Authorization") token: String,
    ): Response<List<EmployeeDirectory>>

    @POST("postSMSLogs")
    suspend fun uploadSms(
        @Header("Authorization") token: String,
        @Body sms: SmsLogRequest
    ): Response<ResponseBody>

    @GET("getSMSLogs")
    suspend fun getSmsLogs(
        @Header("Authorization") token: String
    ): Response<List<SmsLogResponse>>

    @GET("getCRMData")
    suspend fun getCrmData(
        @Header("Authorization") token: String
    ): Response<CrmResponse>

    @GET("view-limit/status")
    suspend fun getViewLimitStatus(
        @Header("Authorization") token: String
    ): Response<ViewLimitResponse>

    @POST("view-limit/increment")
    suspend fun incrementViewCount(
        @Header("Authorization") token: String
    ): Response<ViewLimitResponse>
}

