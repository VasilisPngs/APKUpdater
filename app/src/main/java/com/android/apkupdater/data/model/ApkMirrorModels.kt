package com.android.apkupdater.data.model

data class AppExistsRequest(
    val pnames: List<String>,
    val exclude: List<String> = listOf("alpha", "beta")
)

data class AppExistsResponse(
    val data: List<AppExistsResponseData> = emptyList(),
    val status: Int? = null
)

data class AppExistsResponseData(
    val pname: String = "",
    val exists: Boolean? = null,
    val release: AppExistsRelease? = null,
    val apks: List<AppExistsApk> = emptyList()
)

data class AppExistsRelease(
    val version: String? = null,
    val whatsNew: String? = null
)

data class AppExistsApk(
    val versionCode: Long = 0,
    val link: String = "",
    val arches: List<String> = emptyList(),
    val minapi: String? = "0",
    val capabilities: List<String>? = null,
    val signaturesSha1: List<String>? = null
)
