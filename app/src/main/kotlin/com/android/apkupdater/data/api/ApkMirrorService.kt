package com.android.apkupdater.data.api

import com.android.apkupdater.data.model.AppExistsRequest
import com.android.apkupdater.data.model.AppExistsResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface ApkMirrorService {

    @Headers(
        "User-Agent: GUpdater/1.0.0",
        "Authorization: Basic YXBpLWFwa3VwZGF0ZXI6cm01cmNmcnVVakt5MDRzTXB5TVBKWFc4"
    )
    @POST("wp-json/apkm/v1/app_exists/")
    suspend fun appExists(@Body request: AppExistsRequest): AppExistsResponse

    companion object {
        private const val BASE_URL = "https://www.apkmirror.com/"

        fun create(): ApkMirrorService {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(ApkMirrorService::class.java)
        }
    }
}
