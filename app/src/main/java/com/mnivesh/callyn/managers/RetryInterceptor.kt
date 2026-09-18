package com.mnivesh.callyn.managers

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class RetryInterceptor : Interceptor {
    private val maxRetries = 3

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        
        // 1. Check for custom timeout header
        val customTimeoutStr = request.header("Custom-Timeout")
        var customTimeoutSeconds: Int? = null
        
        if (customTimeoutStr != null) {
            customTimeoutSeconds = customTimeoutStr.toIntOrNull()
            // Remove the header so it doesn't get sent to the server
            request = request.newBuilder()
                .removeHeader("Custom-Timeout")
                .build()
        }

        // If a custom timeout is present, use it and DO NOT retry
        if (customTimeoutSeconds != null) {
            return chain
                .withConnectTimeout(customTimeoutSeconds, TimeUnit.SECONDS)
                .withReadTimeout(customTimeoutSeconds, TimeUnit.SECONDS)
                .withWriteTimeout(customTimeoutSeconds, TimeUnit.SECONDS)
                .proceed(request)
        }

        // 2. Default timeouts for standard requests
        var currentTimeout = 10 // Start with 10s
        var response: Response? = null
        var exception: Exception? = null
        var tryCount = 0

        // Check if request body can be retried (one-shot bodies cannot be re-sent)
        val canRetry = request.body?.isOneShot() != true
        val retryLimit = if (canRetry) maxRetries else 1

        while (tryCount < retryLimit) {
            try {
                // Apply dynamic timeout
                response = chain
                    .withConnectTimeout(currentTimeout, TimeUnit.SECONDS)
                    .withReadTimeout(currentTimeout, TimeUnit.SECONDS)
                    .withWriteTimeout(currentTimeout, TimeUnit.SECONDS)
                    .proceed(request)

                // If response is successful, break out of loop
                if (response.isSuccessful) {
                    break
                }
                
                // If response is not successful, retry on 5xx server errors, 408 (Request Timeout), or 429 (Too Many Requests)
                if (response.code in 500..599 || response.code == 408 || response.code == 429) {
                     response.close() // Close before retrying
                } else {
                     break // Don't retry other client errors (400, 401, 403, 404, etc.)
                }

            } catch (e: Exception) {
                // If request was canceled (e.g. coroutine scope left composition), do not retry
                if (e is IOException && (e.message?.contains("Canceled", ignoreCase = true) == true || chain.call().isCanceled())) {
                    throw e
                }
                exception = e
                Log.e("RetryInterceptor", "Request (${request.method} ${request.url}) failed on attempt ${tryCount + 1}", e)
                response?.close()
                response = null
            }
            
            tryCount++
            
            // If we are going to retry, apply exponential backoff/delay and increase timeout
            if (tryCount < retryLimit && (response == null || !response.isSuccessful)) {
                // Delay: 1s, then 2s
                val delayMs = (1000L * tryCount)
                try {
                    Thread.sleep(delayMs)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
                
                // Increase timeout slightly for next attempt: 10s -> 15s -> 20s
                currentTimeout += 5
            }
        }

        // Throw the last exception if we never got a response
        if (response == null && exception != null) {
            throw exception ?: IOException("Unknown network error during retry")
        }

        return response ?: throw IOException("Unexpected error: null response without exception")
    }
}
