package com.android.apkupdater.data.api

import com.android.apkupdater.data.model.ApkMirrorApk
import com.android.apkupdater.data.model.ApkMirrorApp
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class ApkMirrorClient {

    fun appExists(packageNames: List<String>): List<ApkMirrorApp> {
        val payload = JSONObject()
            .put("pnames", JSONArray(packageNames))
            .put("exclude", JSONArray(EXCLUDED_CHANNELS))
            .toString()

        val request = Request.Builder()
            .url(APP_EXISTS_URL)
            .header("User-Agent", USER_AGENT)
            .header("Authorization", AUTHORIZATION)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val body = SharedHttpClient.instance.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("APKMirror responded with ${response.code}.")
            response.body.string()
        }

        return parseApps(JSONObject(body).optJSONArray("data"))
    }

    private fun parseApps(data: JSONArray?): List<ApkMirrorApp> {
        if (data == null) return emptyList()
        return (0 until data.length()).mapNotNull { index ->
            val entry = data.optJSONObject(index) ?: return@mapNotNull null
            if (!entry.optBoolean("exists")) return@mapNotNull null

            val release = entry.optJSONObject("release")

            ApkMirrorApp(
                packageName = entry.optString("pname"),
                versionName = release?.optString("version").orEmpty(),
                publishDate = release?.optString("publish_date").orEmpty(),
                apks = parseApks(entry.optJSONArray("apks"))
            )
        }
    }

    private fun parseApks(apks: JSONArray?): List<ApkMirrorApk> {
        if (apks == null) return emptyList()
        return (0 until apks.length()).mapNotNull { index ->
            val entry = apks.optJSONObject(index) ?: return@mapNotNull null

            ApkMirrorApk(
                versionCode = entry.optLong("version_code"),
                link = entry.optString("link"),
                publishDate = entry.optString("publish_date"),
                architectures = parseStrings(entry.optJSONArray("arches")),
                densities = parseStrings(entry.optJSONArray("dpis")),
                minimumApi = entry.optString("minapi").toIntOrNull() ?: 0,
                capabilities = parseStrings(entry.optJSONArray("capabilities")),
                signatureSha1s = parseStrings(entry.optJSONArray("signatures-sha1")),
                signatureSha256s = parseStrings(entry.optJSONArray("signatures-sha256"))
            )
        }
    }

    private fun parseStrings(values: JSONArray?): List<String> {
        if (values == null) return emptyList()
        return (0 until values.length()).mapNotNull { index ->
            values.optString(index).lowercase().ifBlank { null }
        }
    }

    private companion object {
        const val APP_EXISTS_URL = "https://www.apkmirror.com/wp-json/apkm/v1/app_exists/"
        const val USER_AGENT = "com.android.apkupdater"
        const val AUTHORIZATION = "Basic YXBpLWFwa3VwZGF0ZXI6cm01cmNmcnVVakt5MDRzTXB5TVBKWFc4"
        val EXCLUDED_CHANNELS = listOf("alpha", "beta")
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
