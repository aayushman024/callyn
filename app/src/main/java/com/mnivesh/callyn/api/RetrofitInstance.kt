package com.mnivesh.callyn.api

import android.content.Context
import android.util.Log
import com.mnivesh.callyn.managers.AuthInterceptor
import com.mnivesh.callyn.managers.RetryInterceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitInstance {

    private const val BASE_URL_PROD = "https://callyn-backend-avh8cae5dpdnckg8.centralindia-01.azurewebsites.net/"
    private const val BASE_URL_LOCAL = "http://192.168.1.40:5000/"

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val client: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)

        appContext?.let {
            builder.addInterceptor(AuthInterceptor(it))
        } ?: Log.e("RetrofitInstance", "Forgot to call RetrofitInstance.init() in Application class!")

        builder.addInterceptor(RetryInterceptor())
        builder.build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL_LOCAL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun handsFreeWebSocketUrl(): String = BASE_URL_LOCAL
        .replaceFirst("https://", "wss://")
        .replaceFirst("http://", "ws://") + "ws/hands-free"

    val api: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
}