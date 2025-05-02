package com.xeraphion.laporbang.api

import com.xeraphion.laporbang.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ApiConfig {

    companion object {
        private const val LOCAL_BASE_URL = "http://192.168.1.7:3000"
        private const val  PHONE_BASE_URL = "http://0.0.0.0:3000"
        private  const val PHONE_BASE_URL2 = "http://192.168.30.11:3000"
        private const val NGROK_BASE_URL = "https://parakeet-faithful-kangaroo.ngrok-free.app"
        private const val VERCEL_BASE_URL = "https://laporbang.vercel.app"

        private const val BASE_URL = NGROK_BASE_URL

        fun getApiService(token: String? = null): ApiService {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }

            val authInterceptor = Interceptor { chain ->
                val requestBuilder = chain.request().newBuilder()

                if (!token.isNullOrEmpty()) {
                    requestBuilder.addHeader("Authorization", "Bearer $token")
                }

                chain.proceed(requestBuilder.build())
            }

            val retryInterceptor = Interceptor { chain ->
                val request = chain.request()
                var response = chain.proceed(request)
                var tryCount = 0
                val maxRetry = 3

                while (!response.isSuccessful && tryCount < maxRetry) {
                    tryCount++
                    response.close()
                    response = chain.proceed(request)
                }

                response
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .addInterceptor(authInterceptor)
                .addInterceptor(retryInterceptor)
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build()

            return retrofit.create(ApiService::class.java)
        }
    }
}
