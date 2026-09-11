package com.android.apkupdater.data.api

import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Url

interface ApkMirrorPageService {

    @Headers("User-Agent: APKUpdater/1.0.0")
    @GET
    suspend fun getReleasePage(@Url url: String): ResponseBody

    companion object {
        private const val BASE_URL = "https://www.apkmirror.com/"

        fun create(): ApkMirrorPageService = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .build()
            .create(ApkMirrorPageService::class.java)
    }
}
