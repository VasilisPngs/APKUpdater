package com.example.data.api

import com.example.data.model.AppExistsRequest
import com.example.data.model.AppExistsResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface ApkMirrorService {

    @Headers(
        "User-Agent: APKUpdater-v3.0.3",
        "Authorization: Basic YXBpLWFwa3VwZGF0ZXI6cm01cmNmcnVVakt5MDRzTXB5TVBKWFc4",
        "Content-Type: application/json"
    )
    @POST("/wp-json/apkm/v1/app_exists/")
    suspend fun appExists(
        @Body request: AppExistsRequest
    ): AppExistsResponse

    companion object {
        private const val BASE_URL = "https://www.apkmirror.com/"

        fun create(): ApkMirrorService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()

            return retrofit.create(ApkMirrorService::class.java)
        }
    }
}
