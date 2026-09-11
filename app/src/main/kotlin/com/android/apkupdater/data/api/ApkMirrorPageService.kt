package com.android.apkupdater.data.api

import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Streaming
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

interface ApkMirrorPageService {

    @Headers("User-Agent: APKUpdater/1.0.0")
    @GET
    suspend fun getReleasePage(@Url url: String): ResponseBody

    @Streaming
    @Headers("User-Agent: APKUpdater/1.0.0")
    @GET
    suspend fun download(@Url url: String): ResponseBody

    companion object {
        private const val BASE_URL = "https://www.apkmirror.com/"

        fun create(): ApkMirrorPageService {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .build()
                .create(ApkMirrorPageService::class.java)
        }
    }
}
