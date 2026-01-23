package com.mnivesh.callyn.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {

    //
    private const val BASE_URL_PROD = "https://callyn-backend-avh8cae5dpdnckg8.centralindia-01.azurewebsites.net/"
    private const val BASE_URL_LOCAL = "http://localhost:5500/"

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL_PROD)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
}