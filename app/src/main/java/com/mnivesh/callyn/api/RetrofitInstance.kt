package com.mnivesh.callyn.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitInstance {

    private const val BASE_URL_PROD = "https://callyn-backend-avh8cae5dpdnckg8.centralindia-01.azurewebsites.net/"
    private const val BASE_URL_LOCAL = "http://localhost:5500/"

    // 1. Create a custom OkHttpClient with 2-minute timeouts
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.MINUTES) // Connect timeout
            .readTimeout(4, TimeUnit.MINUTES)    // Socket read timeout
            .writeTimeout(4, TimeUnit.MINUTES)   // Socket write timeout
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL_PROD)
            .client(client) // 2. Attach the client here
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
}