package com.android.apkupdater.data.model

import com.squareup.moshi.Json

data class AppExistsRequest(
    @Json(name = "pnames") val pnames: List<String>,
    @Json(name = "exclude") val exclude: List<String> = listOf("alpha", "beta")
)

data class AppExistsResponse(
    @Json(name = "data") val data: List<AppExistsResponseData> = emptyList(),
    @Json(name = "status") val status: Int? = null
)

data class AppExistsResponseData(
    @Json(name = "pname") val pname: String = "",
    @Json(name = "exists") val exists: Boolean? = null,
    @Json(name = "developer") val developer: AppExistsDeveloper? = null,
    @Json(name = "app") val app: AppExistsApp? = null,
    @Json(name = "release") val release: AppExistsRelease? = null,
    @Json(name = "apks") val apks: List<AppExistsApk> = emptyList()
)

data class AppExistsDeveloper(
    @Json(name = "name") val name: String? = null,
    @Json(name = "link") val link: String? = null
)

data class AppExistsApp(
    @Json(name = "name") val name: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "link") val link: String? = null
)

data class AppExistsRelease(
    @Json(name = "version") val version: String? = null,
    @Json(name = "publish_date") val publishDate: String? = null,
    @Json(name = "whats_new") val whatsNew: String? = null,
    @Json(name = "link") val link: String? = null
)

data class AppExistsApk(
    @Json(name = "version_code") val versionCode: Long = 0,
    @Json(name = "link") val link: String = "",
    @Json(name = "publish_date") val publishDate: String? = null,
    @Json(name = "arches") val arches: List<String> = emptyList(),
    @Json(name = "dpis") val dpis: List<String>? = null,
    @Json(name = "minapi") val minapi: String? = "0",
    @Json(name = "description") val description: String? = null,
    @Json(name = "capabilities") val capabilities: List<String>? = null,
    @Json(name = "signatures-sha1") val signaturesSha1: List<String>? = null,
    @Json(name = "signatures-sha256") val signaturesSha256: List<String>? = null
)
