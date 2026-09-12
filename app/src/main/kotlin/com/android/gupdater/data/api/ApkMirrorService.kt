package com.android.gupdater.data.api

import com.android.gupdater.data.model.AppExistsRequest
import com.android.gupdater.data.model.AppExistsResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

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
            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(SharedHttpClient.instance)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(ApkMirrorService::class.java)
        }
    }
}
